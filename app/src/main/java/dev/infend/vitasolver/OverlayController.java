package dev.infend.vitasolver;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

/** Small accessibility-owned overlay: no SYSTEM_ALERT_WINDOW permission. */
final class OverlayController {
    private final VitaAccessibilityService service;
    private final WindowManager manager;
    private TextView widget;
    private WindowManager.LayoutParams params;
    private float downX,downY;
    private int initialX,initialY;
    private boolean moved;

    OverlayController(VitaAccessibilityService service){
        this.service=service;
        this.manager=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
    }
    void show(){
        if(widget!=null)return;
        widget=new TextView(service);
        widget.setTextSize(17);widget.setTypeface(null,1);
        widget.setGravity(Gravity.CENTER);widget.setTextColor(Color.WHITE);
        widget.setElevation(10f);
        int size=(int)(58*service.getResources().getDisplayMetrics().density);
        params=new WindowManager.LayoutParams(size,size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        params.x=(int)(12*service.getResources().getDisplayMetrics().density);
        params.y=(int)(140*service.getResources().getDisplayMetrics().density);
        widget.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getRawX();downY=event.getRawY();initialX=params.x;initialY=params.y;moved=false;return true;
                case MotionEvent.ACTION_MOVE:
                    int dx=Math.round(event.getRawX()-downX),dy=Math.round(event.getRawY()-downY);
                    if(Math.abs(dx)>10||Math.abs(dy)>10)moved=true;
                    params.x=Math.max(0,initialX+dx);params.y=Math.max(0,initialY+dy);
                    try{manager.updateViewLayout(widget,params);}catch(Exception ignore){}
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved)service.toggle();return true;
                default:return false;
            }
        });
        update();
        try{manager.addView(widget,params);}catch(Exception ex){widget=null;VitaAccessibilityService.publish(service,"Не удалось создать панель: "+ex.getMessage());}
    }
    void update(){
        if(widget==null)return;
        boolean run=service.getSharedPreferences("solver",Context.MODE_PRIVATE).getBoolean("running",false);
        widget.setText(run?"Ⅱ":"▶");
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(8,18,27));bg.setStroke((int)(2*service.getResources().getDisplayMetrics().density),run?0xFF21D3E8:0xFF637481);
        widget.setBackground(bg);
    }
    void destroy(){if(widget!=null){try{manager.removeView(widget);}catch(Exception ignore){}widget=null;}}
}
