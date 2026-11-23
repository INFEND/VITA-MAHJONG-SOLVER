package dev.infend.vitasolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import dev.infend.vitasolver.core.MahjongSolver;
import dev.infend.vitasolver.core.Tile;

/** Accessibility runs only on the user's explicitly enabled Android service. */
public final class VitaAccessibilityService extends AccessibilityService {
    public static final String GAME="com.vitastudio.mahjong";
    private static volatile VitaAccessibilityService active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private OverlayController overlay;
    private boolean busy=false;
    private long lastAction=0;
    private int lastCount=-1,failures=0,iterations=0;
    private Set<String> banned=new HashSet<>();
    private String lastBoard="";

    public static boolean enabled(){return active!=null;}
    public static void refresh(){VitaAccessibilityService s=active;if(s!=null){if(s.overlay!=null)s.overlay.update();s.kick();}}
    public static boolean toggleRunning(){VitaAccessibilityService s=active;if(s==null)return false;s.toggle();return true;}
    private SharedPreferences prefs(){return getSharedPreferences("solver",MODE_PRIVATE);}
    public static void publish(android.content.Context c,String value){
        c.getSharedPreferences("solver",MODE_PRIVATE).edit().putString("status",value).apply();
    }

    @Override protected void onServiceConnected(){
        super.onServiceConnected();active=this;
        overlay=new OverlayController(this);overlay.show();
        publish(this,"Служба доступна. Открой Vita Mahjong.");
        kick();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e==null)return;
        CharSequence pkg=e.getPackageName();
        if(pkg!=null&&GAME.contentEquals(pkg)&&prefs().getBoolean("running",false))kick();
    }
    @Override public void onInterrupt(){stop("Служба прервана системой");}
    @Override public void onDestroy(){
        active=null;handler.removeCallbacksAndMessages(null);
        if(overlay!=null)overlay.destroy();
        super.onDestroy();
    }

    private void stop(String text){
        prefs().edit().putBoolean("running",false).apply();
        publish(this,text);if(overlay!=null)overlay.update();
    }
    public void toggle(){
        boolean next=!prefs().getBoolean("running",false);
        prefs().edit().putBoolean("running",next).apply();
        failures=0;lastCount=-1;banned.clear();lastBoard="";
        publish(this,next?"Автоигра включена":"Автоигра остановлена");
        if(overlay!=null)overlay.update();
        if(next)kick();
    }
    public void kick(){
        handler.removeCallbacks(tick);
        if(prefs().getBoolean("running",false))handler.postDelayed(tick,250);
    }
    private final Runnable tick=this::runTick;
    private void runTick(){
        if(busy||!prefs().getBoolean("running",false)){if(busy)handler.postDelayed(tick,400);return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null||!GAME.contentEquals(root.getPackageName())){
            publish(this,"Ожидаю открытого Vita Mahjong");
            handler.postDelayed(tick,1600);return;
        }
        root.recycle();
        busy=true;
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult shot){
                    HardwareBuffer hardware=shot.getHardwareBuffer();
                    Bitmap frame=null;
                    try{
                        Bitmap wrapped=Bitmap.wrapHardwareBuffer(hardware,shot.getColorSpace());
                        if(wrapped!=null)frame=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Exception ex){publish(VitaAccessibilityService.this,"Ошибка чтения кадра: "+ex.getMessage());}
                    finally{hardware.close();}
                    if(frame==null){busy=false;handler.postDelayed(tick,1100);return;}
                    final Bitmap captured=frame;
                    // Screenshot analysis is off the UI thread.
                    new Thread(()->{
                        TileVision.Detection detected;
                        try{detected=TileVision.detect(captured);}
                        catch(Exception err){
                            handler.post(()->{busy=false;publish(VitaAccessibilityService.this,"Ошибка распознавания: "+err.getMessage());handler.postDelayed(tick,1500);});
                            captured.recycle();return;
                        }
                        captured.recycle();
                        handler.post(()->onDetection(detected));
                    },"vita-vision").start();
                }
                @Override public void onFailure(int errorCode){
                    busy=false;publish(VitaAccessibilityService.this,"Снимок экрана недоступен: "+errorCode);
                    handler.postDelayed(tick,1200);
                }
            });
        }catch(Exception err){busy=false;publish(this,"Не удалось захватить экран: "+err.getMessage());handler.postDelayed(tick,1500);}
    }

    private void onDetection(TileVision.Detection seen){
        busy=false;
        if(!prefs().getBoolean("running",false))return;
        List<Tile> tiles=seen.tiles;
        if(seen.trayMode ? (tiles.isEmpty() && seen.tray.isEmpty()) : tiles.size()<2){
            failures++;
            if(prefs().getBoolean("auto_next",true)&&clickNextLevel()){
                publish(this,"Переход к следующему уровню");handler.postDelayed(tick,1800);return;
            }
            publish(this,"Поле не распознано (плиток: "+tiles.size()+"). Проверь отображение.");
            if(failures>=9){stop("Пауза: не удалось распознать поле");return;}
            handler.postDelayed(tick,1500);return;
        }
        String board=fingerprint(tiles,seen.tray);
        if(board.equals(lastBoard)){
            // Either the previous pair did not disappear, or the game has settled.
            failures++;
            if(failures>=5){stop("Пауза: выбранные плитки не исчезают. Нужна калибровка.");return;}
        }else{banned.clear();failures=0;}
        lastBoard=board;lastCount=tiles.size();
        float threshold=prefs().getInt("tolerance",28)/100f;
        MahjongSolver.Move storedMatch=seen.trayMode
            ?MahjongSolver.findTrayMatch(seen.tray,tiles,Math.min(threshold,0.24f),banned)
            :null;
        MahjongSolver.Move move=storedMatch!=null ? storedMatch
            : (seen.trayMode && seen.tray.size()>=3
                ?null:MahjongSolver.findMove(tiles,threshold,banned));
        if(move==null){
            if(tiles.isEmpty() && prefs().getBoolean("auto_next",true) && clickNextLevel()){
                publish(this,"Продолжаю прохождение");handler.postDelayed(tick,1600);return;
            }
            stop(seen.trayMode
                ?"Пауза: нет безопасного совпадения для лотка ("+seen.tray.size()+"/4)."
                :"Пауза: нет уверенно распознанной свободной пары ("+tiles.size()+" плиток)");
            return;
        }
        boolean fromTray=storedMatch!=null;
        if(fromTray)banned.add("T:"+move.first.id+":"+move.second.id);
        else banned.add(MahjongSolver.pairKey(move.first,move.second));
        publish(this,"Ход "+(++iterations)+" · "+(seen.trayMode?"лоток "+seen.tray.size()+"/4":"поле")+
            " · плиток: "+tiles.size()+" · сходство: "+Math.round((1-move.distance)*100)+"%");
        busy=true;
        final int delay=prefs().getInt("tap_delay",280);
        if(seen.trayMode) {
            // After EVERY tap re-read the tray and the board: the other tile may move.
            Tile target=fromTray?move.second:move.first;
            tap(target.centerX()/seen.scale,target.centerY()/seen.scale,()->{
                busy=false;lastAction=System.currentTimeMillis();
                handler.postDelayed(tick,Math.max(400,delay));
            });
        }else{
            tap(move.first.centerX()/seen.scale,move.first.centerY()/seen.scale,()->
                handler.postDelayed(()->tap(move.second.centerX()/seen.scale,move.second.centerY()/seen.scale,()->{
                    busy=false;lastAction=System.currentTimeMillis();handler.postDelayed(tick,Math.max(400,delay));
                }),delay));
        }
    }

    private String fingerprint(List<Tile> tiles,List<Tile> tray){
        long acc=1469598103934665603L;
        for(Tile t:tiles){
            acc^=t.centerX();acc*=1099511628211L;
            acc^=t.centerY();acc*=1099511628211L;
            for(int i=0;i<t.signature.length;i+=61){acc^=(long)(t.signature[i]*100);acc*=1099511628211L;}
        }
        // A tray update counts as a changed board even if visible field tiles remain.
        for(Tile t:tray){
            acc^=0x9e3779b9L;acc*=1099511628211L;
            acc^=t.centerX();acc*=1099511628211L;
            for(int i=0;i<t.signature.length;i+=61){acc^=(long)(t.signature[i]*100);acc*=1099511628211L;}
        }
        return Long.toHexString(acc);
    }

    private void tap(float x,float y,Runnable complete){
        Path p=new Path();p.moveTo(x,y);
        GestureDescription.Builder builder=new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(p,0,45));
        boolean accepted=dispatchGesture(builder.build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gestureDescription){handler.post(complete);}
            @Override public void onCancelled(GestureDescription gestureDescription){
                busy=false;publish(VitaAccessibilityService.this,"Касание отменено системой");handler.postDelayed(tick,800);
            }
        },handler);
        if(!accepted){busy=false;publish(this,"Система отклонила касание");handler.postDelayed(tick,1000);}
    }

    private boolean clickNextLevel(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return false;
        try{
            if(root.getPackageName()==null||!GAME.contentEquals(root.getPackageName()))return false;
            return clickEligibleNext(root,0);
        }finally{root.recycle();}
    }
    private boolean clickEligibleNext(AccessibilityNodeInfo node,int depth){
        if(depth>16)return false;
        String text=node.getText()==null?"":node.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String desc=node.getContentDescription()==null?"":node.getContentDescription().toString().trim().toLowerCase(java.util.Locale.ROOT);
        boolean target=text.equals("next level")||text.equals("следующий уровень")||text.equals("play next")||
          desc.equals("next level")||desc.equals("следующий уровень");
        if(target && node.isVisibleToUser()){
            AccessibilityNodeInfo click=node;
            for(int i=0;i<3&&click!=null;i++){
                if(click.isClickable())return click.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                click=click.getParent();
            }
        }
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child==null)continue;
            boolean clicked=clickEligibleNext(child,depth+1);child.recycle();
            if(clicked)return true;
        }
        return false;
    }
}
