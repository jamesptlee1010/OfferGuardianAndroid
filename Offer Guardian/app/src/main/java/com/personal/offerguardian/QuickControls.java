package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Touchable three-action vertical pill. Long press anywhere moves the whole pill; never invokes a shortcut. */
final class QuickControls {
    interface Listener { void action(String name); }
    private final AccessibilityService service;
    private final WindowManager wm;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private boolean touching, held, moved, cancelled;
    private float downX,downY;
    private int originX,originY, maxX,maxY, pointer=-1;
    private float appliedX=Float.NaN,appliedY=Float.NaN;
    private final Runnable hold=()->{
        if(touching&&!moved&&!cancelled&&panel!=null){held=true;panel.setAlpha(1f);panel.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);}
    };
    QuickControls(AccessibilityService service,Listener listener){this.service=service;this.listener=listener;wm=(WindowManager)service.getSystemService(AccessibilityService.WINDOW_SERVICE);}
    private int dp(float n){return Math.round(n*service.getResources().getDisplayMetrics().density);}
    private void bounds(){
        int width=service.getResources().getDisplayMetrics().widthPixels,height=service.getResources().getDisplayMetrics().heightPixels;
        if(Build.VERSION.SDK_INT>=30){
            WindowMetrics metrics=wm.getCurrentWindowMetrics();Rect r=metrics.getBounds();
            Insets inset=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            width=r.width()-inset.left-inset.right;height=r.height()-inset.top-inset.bottom;
        }
        maxX=Math.max(0,width-dp(44));maxY=Math.max(0,height-dp(44)*3-dp(1)*2);
    }
    private void updatePosition(){
        if(panel==null||touching)return;
        int oldMaxX=maxX,oldMaxY=maxY;bounds();SharedPreferences p=Prefs.of(service);
        float x=p.getFloat(Prefs.QUICK_X,1f),y=p.getFloat(Prefs.QUICK_Y,Float.NaN);
        if(Float.compare(x,appliedX)==0&&Float.compare(y,appliedY)==0&&oldMaxX==maxX&&oldMaxY==maxY)return;
        params.x=QuickDragPolicy.position(x,maxX);params.y=Float.isNaN(y)?QuickDragPolicy.position(.20f,maxY):QuickDragPolicy.position(y,maxY);
        appliedX=x;appliedY=y;wm.updateViewLayout(panel,params);
    }
    void show(){
        if(wm==null)return;
        if(panel!=null){try{updatePosition();}catch(Exception ignored){}return;}
        try {
            panel=new LinearLayout(service);panel.setGravity(Gravity.CENTER);panel.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(205,31,31,31));bg.setCornerRadius(dp(24));bg.setStroke(dp(1),Color.argb(160,93,93,93));panel.setBackground(bg);
            String[] names={"D","DF","DR"};String[] descriptions={"Open DoorDash","Pause DoorDash and return to Uber","Resume paused DoorDash"};
            panel.setOnTouchListener((v,e)->touch(v,e,false));
            for(int i=0;i<names.length;i++){
                if(i>0){View line=new View(service);line.setBackgroundColor(Color.argb(150,125,125,125));panel.addView(line,new LinearLayout.LayoutParams(dp(24),dp(1)));}
                final String action=names[i];TextView b=new TextView(service);b.setText(action);b.setTextSize(12);b.setTextColor(Color.rgb(220,220,220));b.setGravity(Gravity.CENTER);b.setContentDescription(descriptions[i]);b.setOnClickListener(v->listener.action(action));b.setOnTouchListener((v,e)->touch(v,e,true));panel.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));
            }
            params=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
            params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("Offer Guardian quick controls");bounds();SharedPreferences p=Prefs.of(service);
            appliedX=p.getFloat(Prefs.QUICK_X,1f);appliedY=p.getFloat(Prefs.QUICK_Y,Float.NaN);
            params.x=QuickDragPolicy.position(appliedX,maxX);params.y=Float.isNaN(appliedY)?QuickDragPolicy.position(.20f,maxY):QuickDragPolicy.position(appliedY,maxY);
            wm.addView(panel,params);
        }catch(Exception e){hide();}
    }
    private boolean touch(View v,MotionEvent e,boolean button){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                handler.removeCallbacks(hold);touching=true;held=false;moved=false;cancelled=false;pointer=e.getPointerId(0);
                downX=e.getRawX();downY=e.getRawY();originX=params.x;originY=params.y;
                if(!Prefs.of(service).getBoolean(Prefs.QUICK_LOCK,false))handler.postDelayed(hold,450);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelled=true;handler.removeCallbacks(hold);return true;
            case MotionEvent.ACTION_MOVE:
                if(!touching||cancelled||e.getPointerId(0)!=pointer)return true;
                float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                if(Math.hypot(dx,dy)>ViewConfiguration.get(service).getScaledTouchSlop()){
                    moved=true;if(!held)handler.removeCallbacks(hold);
                }
                if(held){bounds();params.x=QuickDragPolicy.clamp(originX+Math.round(dx),maxX);params.y=QuickDragPolicy.clamp(originY+Math.round(dy),maxY);try{wm.updateViewLayout(panel,params);}catch(Exception ignored){cancelled=true;}}
                return true;
            case MotionEvent.ACTION_UP:
                if(!touching)return true;
                handler.removeCallbacks(hold);
                // A long touch also suppresses taps when position is locked.
                boolean longTouch=e.getEventTime()-e.getDownTime()>=450;
                boolean tap=button&&QuickDragPolicy.mayTap(held||longTouch,moved||Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>ViewConfiguration.get(service).getScaledTouchSlop(),cancelled);
                if(held&&!cancelled){appliedX=QuickDragPolicy.fraction(params.x,maxX);appliedY=QuickDragPolicy.fraction(params.y,maxY);Prefs.of(service).edit().putFloat(Prefs.QUICK_X,appliedX).putFloat(Prefs.QUICK_Y,appliedY).apply();}
                touching=false;pointer=-1;if(panel!=null)panel.setAlpha(1f);
                if(tap)v.performClick();else if(cancelled){appliedX=Float.NaN;appliedY=Float.POSITIVE_INFINITY;try{updatePosition();}catch(Exception ignored){}}
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(hold);touching=false;cancelled=true;pointer=-1;appliedX=Float.NaN;appliedY=Float.POSITIVE_INFINITY;try{updatePosition();}catch(Exception ignored){}return true;
            default:return true;
        }
    }
    boolean covers(int x,int y){if(panel==null)return false;int[] xy=new int[2];panel.getLocationOnScreen(xy);return x>=xy[0]&&x<xy[0]+panel.getWidth()&&y>=xy[1]&&y<xy[1]+panel.getHeight();}
    void hide(){handler.removeCallbacks(hold);touching=false;pointer=-1;if(panel!=null){try{wm.removeViewImmediate(panel);}catch(Exception ignored){}panel=null;}}
}
