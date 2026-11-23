package dev.infend.vitasolver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;

public final class MainActivity extends Activity {
    private static final int BG=0xFF05080C, CARD=0xFF0D1720, BORDER=0xFF214251;
    private static final int CYAN=0xFF21D3E8, FG=0xFFE5F5FA, MUTED=0xFF8FA7B3;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status;
    private Button runButton;
    private boolean foreground;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        draw();
    }
    @Override protected void onResume(){super.onResume();foreground=true;ui.post(poll);}
    @Override protected void onPause(){foreground=false;ui.removeCallbacks(poll);super.onPause();}
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(!foreground)return;
        boolean enabled=VitaAccessibilityService.enabled();
        boolean running=prefs().getBoolean("running",false);
        status.setText((enabled?"● Сервис подключён\n":"○ Включите специальные возможности\n")+
            prefs().getString("status","Решатель готов к настройке"));
        runButton.setText(running?"Ⅱ  ОСТАНОВИТЬ АВТОИГРУ":"▶  ЗАПУСТИТЬ АВТОИГРУ");
        runButton.setEnabled(enabled);
        ui.postDelayed(this,600);
    }};
    private android.content.SharedPreferences prefs(){return getSharedPreferences("solver",MODE_PRIVATE);}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable background(int fill,int stroke,int corner){
        GradientDrawable gd=new GradientDrawable();gd.setColor(fill);gd.setCornerRadius(dp(corner));
        gd.setStroke(dp(1),stroke);return gd;
    }
    private TextView label(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void spaced(LinearLayout parent,View v,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);
    }
    private LinearLayout panel(){
        LinearLayout l=col();l.setPadding(dp(18),dp(16),dp(18),dp(18));
        l.setBackground(background(CARD,BORDER,16));return l;
    }
    private Button button(String text){
        Button b=new Button(this);b.setText(text);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(FG);b.setPadding(dp(10),dp(8),dp(10),dp(8));
        b.setBackground(background(0xFF12303A,0xFF28687C,12));return b;
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=col();root.setPadding(dp(18),dp(24),dp(18),dp(28));scroll.addView(root);
        if(Build.VERSION.SDK_INT>=35){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(18),dp(24)+bars.top,dp(18),dp(28)+bars.bottom);return insets;
            });
        }
        TextView brand=label("◈  VITA SOLVER",27,CYAN,true);spaced(root,brand,1);
        TextView subtitle=label("INFEND  /  MAHJONG AUTO SOLVER",10,MUTED,true);spaced(root,subtitle,4);
        LinearLayout state=panel();spaced(root,state,24);
        spaced(state,label("СОСТОЯНИЕ",11,CYAN,true),0);
        status=label("Проверка службы…",15,FG,false);status.setLineSpacing(dp(5),1);spaced(state,status,12);
        runButton=button("▶  ЗАПУСТИТЬ АВТОИГРУ");spaced(root,runButton,16);
        runButton.setOnClickListener(v->{
            if(!VitaAccessibilityService.enabled()){
                new AlertDialog.Builder(this).setMessage("Сначала включите сервис VITA SOLVER в специальных возможностях Android.")
                    .setPositiveButton("Открыть",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Отмена",null).show();return;
            }
            VitaAccessibilityService.toggleRunning();
        });
        Button permission=button("⚙  ВКЛЮЧИТЬ СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ");spaced(root,permission,10);
        permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button launch=button("▣  ОТКРЫТЬ VITA MAHJONG");spaced(root,launch,10);
        launch.setOnClickListener(v->{
            Intent intent=getPackageManager().getLaunchIntentForPackage(VitaAccessibilityService.GAME);
            if(intent!=null)startActivity(intent);
            else new AlertDialog.Builder(this).setMessage("Игра Vita Mahjong не обнаружена. Установить из Google Play?")
                .setPositiveButton("Google Play",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+VitaAccessibilityService.GAME))))
                .setNegativeButton("Отмена",null).show();
        });
        LinearLayout options=panel();spaced(root,options,16);
        spaced(options,label("НАСТРОЙКИ РЕШАТЕЛЯ",12,CYAN,true),0);
        toggle(options,"Автопереход на следующий уровень","auto_next",true);
        spaced(options,label("Задержка между касаниями",13,FG,true),16);
        final TextView speedValue=label("",12,MUTED,false);spaced(options,speedValue,5);
        SeekBar delay=new SeekBar(this);delay.setMax(1050);
        int saved=prefs().getInt("tap_delay",280);delay.setProgress(Math.max(0,saved-150));
        speedValue.setText(saved+" мс");spaced(options,delay,6);
        delay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser){int value=150+progress;speedValue.setText(value+" мс");if(fromUser)prefs().edit().putInt("tap_delay",value).apply();}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        spaced(options,label("Допуск сравнения рисунков",13,FG,true),16);
        TextView compare=label("",12,MUTED,false);spaced(options,compare,5);
        SeekBar certainty=new SeekBar(this);certainty.setMax(28);
        certainty.setProgress(prefs().getInt("tolerance",28)-12);
        compare.setText(prefs().getInt("tolerance",28)+"%");spaced(options,certainty,4);
        certainty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser){int value=12+progress;compare.setText(value+"%");if(fromUser)prefs().edit().putInt("tolerance",value).apply();}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        LinearLayout ota=panel();spaced(root,ota,16);
        spaced(ota,label("ОБНОВЛЕНИЯ · OTA",12,CYAN,true),0);
        spaced(ota,label("GitHub Releases · постоянная подпись · SHA-256",12,MUTED,false),8);
        Button check=button("↻  ПРОВЕРИТЬ ОБНОВЛЕНИЕ");spaced(ota,check,12);
        check.setOnClickListener(v->checkUpdate());
        spaced(root,label("Сервис обрабатывает скриншоты только на устройстве. Режим с верхним лотком распознаётся отдельно. Для скрытых плиток, рекламы и нестандартных раскладок автоматическое прохождение пока не гарантировано.",12,MUTED,false),20);
        setContentView(scroll);
    }
    private void toggle(LinearLayout box,String title,String pref,boolean def){
        Switch sw=new Switch(this);sw.setText(title);sw.setTextColor(FG);sw.setTextSize(13);
        sw.setChecked(prefs().getBoolean(pref,def));
        sw.setOnCheckedChangeListener((CompoundButton b,boolean value)->prefs().edit().putBoolean(pref,value).apply());
        spaced(box,sw,12);
    }
    private void checkUpdate(){
        final AlertDialog progress=new AlertDialog.Builder(this).setTitle("Обновление VITA SOLVER")
            .setMessage("Проверяю GitHub Releases…").setNegativeButton("Отмена",null).create();
        progress.show();
        OtaManager.check(this,new OtaManager.Listener(){
            @Override public void status(String message){progress.setMessage(message);}
            @Override public void latest(OtaManager.Release release,boolean newer){
                progress.dismiss();
                if(!newer){new AlertDialog.Builder(MainActivity.this).setMessage("Уже установлена последняя версия: "+release.versionName)
                    .setPositiveButton("ОК",null).show();return;}
                new AlertDialog.Builder(MainActivity.this).setTitle("Доступна версия "+release.versionName)
                    .setMessage(release.notes).setPositiveButton("Скачать и установить",(d,w)->download(release))
                    .setNegativeButton("Позже",null).show();
            }
            @Override public void error(String message){progress.dismiss();new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("ОК",null).show();}
        });
    }
    private void download(OtaManager.Release release){
        final AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Загрузка обновления").setMessage("Подключение…")
            .setNegativeButton("Скрыть",(d,w)->{}).create();dlg.show();
        OtaManager.download(this,release,new OtaManager.DownloadListener(){
            @Override public void progress(long done,long all){dlg.setMessage("Загрузка: "+(done/1048576)+" / "+(all>0?all/1048576+" МБ":"? МБ"));}
            @Override public void finished(File apk){dlg.dismiss();OtaManager.install(MainActivity.this,apk);}
            @Override public void error(String error){dlg.dismiss();new AlertDialog.Builder(MainActivity.this).setTitle("Ошибка OTA").setMessage(error).setPositiveButton("ОК",null).show();}
        });
    }
}
