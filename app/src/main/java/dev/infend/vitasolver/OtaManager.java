package dev.infend.vitasolver;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** OTA based on QR LINK's single pinned HTTPS channel and signed APK verification. */
public final class OtaManager {
    public static final String MANIFEST_URL="https://github.com/INFEND/VITA-MAHJONG-SOLVER/releases/latest/download/update.json";
    public static final String APP_ID="dev.infend.vitasolver";
    // Replaced at packaging time with the permanent release signing certificate fingerprint.
    public static final String TRUSTED_CERT_SHA256="C83626A56D1CBB2948E5F598D5A22C2B7F299989E4F489FB5CC8AAF0E170FE82";
    private static final long MAX_APK=150L*1024*1024;
    private static final int MAX_JSON=2*1024*1024;
    public interface Listener {
        void status(String message);
        void latest(Release release,boolean newer);
        void error(String message);
    }
    public interface DownloadListener {
        void progress(long done,long total);
        void finished(File apk);
        void error(String message);
    }
    public static final class Release {
        public final int versionCode;
        public final String versionName,sha256,apkUrl,notes;
        public final long size;
        Release(JSONObject j,int installed)throws Exception {
            if(j.optInt("schema",-1)!=1)throw new Exception("Неизвестный формат OTA");
            if(!APP_ID.equals(j.getString("packageName")))throw new Exception("Неверный идентификатор приложения");
            versionCode=j.getInt("versionCode");versionName=j.getString("versionName");
            size=j.getLong("sizeBytes");sha256=j.getString("sha256").toLowerCase(Locale.ROOT);
            apkUrl=j.getString("apkUrl");
            if(versionCode<1||!versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||size<=0||size>MAX_APK||!sha256.matches("[a-f0-9]{64}"))
                throw new Exception("Некорректные параметры обновления");
            String expected="https://github.com/INFEND/VITA-MAHJONG-SOLVER/releases/download/v"+versionName+"/VITA-SOLVER.apk";
            if(!expected.equals(apkUrl))throw new Exception("Обновление пришло из недоверенного источника");
            StringBuilder changelog=new StringBuilder();
            JSONArray history=j.optJSONArray("history");
            if(history!=null){
                for(int i=0;i<history.length();i++){
                    JSONObject entry=history.optJSONObject(i);
                    if(entry!=null&&entry.optInt("versionCode")>installed&&entry.optInt("versionCode")<=versionCode){
                        changelog.append("Версия ").append(entry.optString("versionName")).append("\n")
                            .append(entry.optString("notes")).append("\n\n");
                    }
                }
            }
            notes=changelog.length()==0?j.optString("notes","Без описания изменений"):changelog.toString().trim();
        }
    }
    private OtaManager(){}
    private static HttpURLConnection open(String value)throws Exception {
        URL parsed=new URL(value);
        if(!"https".equals(parsed.getProtocol()))throw new Exception("Поддерживается только HTTPS");
        HttpURLConnection conn=(HttpURLConnection)parsed.openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(28000);
        conn.setRequestProperty("User-Agent","VITA-SOLVER-OTA/1.0");
        int code=conn.getResponseCode();
        if(code!=200)throw new Exception("Сервер обновлений: HTTP "+code);
        if(!"https".equals(conn.getURL().getProtocol()))throw new Exception("Небезопасное перенаправление");
        return conn;
    }
    private static byte[] bounded(InputStream stream,int max)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int n;
        while((n=stream.read(buffer))!=-1){if(out.size()+n>max)throw new Exception("Манифест слишком большой");out.write(buffer,0,n);}
        return out.toByteArray();
    }
    public static void check(Activity a,Listener callback){
        new Thread(()->{
            try{
                a.runOnUiThread(()->callback.status("Читаю защищённый манифест…"));
                HttpURLConnection connection=open(MANIFEST_URL);
                byte[] bytes;
                try(InputStream in=connection.getInputStream()){bytes=bounded(in,MAX_JSON);}finally{connection.disconnect();}
                long installed=a.getPackageManager().getPackageInfo(APP_ID,0).getLongVersionCode();
                Release result=new Release(new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),(int)installed);
                a.runOnUiThread(()->callback.latest(result,result.versionCode>installed));
            }catch(Exception ex){a.runOnUiThread(()->callback.error("Проверка OTA не удалась: "+ex.getMessage()));}
        },"vita-ota-check").start();
    }
    private static File file(Activity a){return new File(new File(a.getFilesDir(),"ota"),"vita-solver.apk");}
    public static void download(Activity a,Release release,DownloadListener callback){
        new Thread(()->{
            File dest=file(a),part=new File(dest.getPath()+".part");
            try{
                if(!dest.getParentFile().isDirectory()&&!dest.getParentFile().mkdirs())throw new Exception("Нет папки для обновления");
                if(dest.getParentFile().getUsableSpace()<release.size+10*1024*1024)throw new Exception("Недостаточно места");
                HttpURLConnection connection=open(release.apkUrl);
                try{
                    long received=0;
                    MessageDigest md=MessageDigest.getInstance("SHA-256");
                    try(InputStream in=new BufferedInputStream(connection.getInputStream(),65536);
                        FileOutputStream out=new FileOutputStream(part,false)){
                        byte[] buf=new byte[65536];int n;
                        while((n=in.read(buf))!=-1){
                            received+=n;
                            if(received>MAX_APK)throw new Exception("Слишком большой APK");
                            out.write(buf,0,n);md.update(buf,0,n);
                            long done=received;a.runOnUiThread(()->callback.progress(done,release.size));
                        }
                        out.getFD().sync();
                    }
                    if(received!=release.size)throw new Exception("Файл загружен не полностью");
                    StringBuilder sha=new StringBuilder();
                    for(byte b:md.digest())sha.append(String.format(Locale.ROOT,"%02x",b&255));
                    if(!release.sha256.equals(sha.toString()))throw new Exception("Контрольная сумма SHA-256 не совпадает");
                    if(!part.renameTo(dest))throw new Exception("Не удалось сохранить APK");
                    verify(a,dest,release.versionCode);
                    a.runOnUiThread(()->callback.finished(dest));
                }finally{connection.disconnect();}
            }catch(Exception e){part.delete();dest.delete();a.runOnUiThread(()->callback.error(e.getMessage()));}
        },"vita-ota-download").start();
    }
    public static void verify(Activity a,File apk,int expected)throws Exception {
        PackageManager pm=a.getPackageManager();
        PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!APP_ID.equals(archive.packageName)||archive.getLongVersionCode()!=expected)
            throw new Exception("Идентификатор или версия APK не совпадают");
        PackageInfo installed=pm.getPackageInfo(APP_ID,PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive.signingInfo==null||installed.signingInfo==null)throw new Exception("Подпись отсутствует");
        Signature[] actual=archive.signingInfo.getApkContentsSigners();
        Signature[] current=installed.signingInfo.getApkContentsSigners();
        if(actual.length!=1||current.length!=1||!Arrays.equals(actual,current))throw new Exception("Неверная подпись обновления");
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        StringBuilder cert=new StringBuilder();
        for(byte b:md.digest(actual[0].toByteArray()))cert.append(String.format(Locale.ROOT,"%02X",b&255));
        if(!TRUSTED_CERT_SHA256.equals(cert.toString()))throw new Exception("Неизвестный сертификат релиза");
        if(expected<=installed.getLongVersionCode())throw new Exception("Эта версия уже установлена");
    }
    public static void install(Activity a,File apk){
        try{
            PackageInfo info=a.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
            if(info==null)throw new Exception("APK повреждён");
            verify(a,apk,(int)info.getLongVersionCode());
            if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
                new android.app.AlertDialog.Builder(a).setMessage("Разрешите установку приложений из VITA SOLVER, затем повторите установку через OTA.")
                    .setPositiveButton("Разрешить",(d,w)->a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+APP_ID))))
                    .setNegativeButton("Отмена",null).show();return;
            }
            Uri uri=Uri.parse("content://"+APP_ID+".updates/apk");
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri("VITA SOLVER",uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.getApplicationContext().startActivity(intent);
        }catch(Exception ex){new android.app.AlertDialog.Builder(a).setTitle("Ошибка установки").setMessage(ex.getMessage()).setPositiveButton("ОК",null).show();}
    }
}
