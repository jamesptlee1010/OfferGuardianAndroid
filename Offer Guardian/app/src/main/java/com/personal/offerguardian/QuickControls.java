package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact touchable overlay. Only the pill itself receives touches. */
final class QuickControls {
    interface Listener { void action(String name); }
    private final AccessibilityService service;
    private final WindowManager wm;
    private final Listener listener;
    private LinearLayout panel;
    QuickControls(AccessibilityService service,Listener listener){this.service=service;this.listener=listener;wm=(WindowManager)service.getSystemService(AccessibilityService.WINDOW_SERVICE);}
    private int dp(float n){return Math.round(n*service.getResources().getDisplayMetrics().density);}
    void show(){
        if(panel!=null||wm==null)return;
        try {
            panel=new LinearLayout(service);panel.setGravity(Gravity.CENTER);panel.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(205,31,31,31));bg.setCornerRadius(dp(24));bg.setStroke(dp(1),Color.argb(160,93,93,93));panel.setBackground(bg);
            String[] names={"D","DF","U"};String[] descriptions={"Open DoorDash","Pause DoorDash and return to Uber","Open Uber"};
            for(int i=0;i<names.length;i++){
                if(i>0){View line=new View(service);line.setBackgroundColor(Color.argb(150,125,125,125));panel.addView(line,new LinearLayout.LayoutParams(dp(1),dp(17)));}
                final String action=names[i];TextView b=new TextView(service);b.setText(action);b.setTextSize(12);b.setTextColor(Color.rgb(220,220,220));b.setGravity(Gravity.CENTER);b.setContentDescription(descriptions[i]);b.setOnClickListener(v->listener.action(action));panel.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));
            }
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(3);p.setTitle("Offer Guardian quick controls");wm.addView(panel,p);
        }catch(Exception e){hide();}
    }
    void hide(){if(panel!=null){try{wm.removeViewImmediate(panel);}catch(Exception ignored){}panel=null;}}
}
