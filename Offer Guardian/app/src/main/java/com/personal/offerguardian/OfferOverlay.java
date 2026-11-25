package com.personal.offerguardian;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Small, non-touchable accessibility overlay. The sole contents are the two
 * estimated rates: no lightning bolt, separator, close button, or logo.
 * A TYPE_ACCESSIBILITY_OVERLAY is owned by the already enabled accessibility
 * service; it does not ask for intrusive SYSTEM_ALERT_WINDOW permission.
 */
final class OfferOverlay {
    private final AccessibilityService service;
    private final WindowManager wm;
    private LinearLayout panel;
    private TextView perHour, perKm;
    private String currentText="";

    OfferOverlay(AccessibilityService service) {
        this.service=service;
        wm=(WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    private int dp(float n) {return Math.round(service.getResources().getDisplayMetrics().density*n);}

    /** Show only after the rule engine has returned SHOW. */
    boolean show(double hourly,double km) {return show(hourly,km,false);}
    boolean show(double hourly,double km,boolean dash) {
        if(wm==null) return false;
        String hour=Double.isFinite(hourly) && hourly>0
                ?String.format(Locale.US,"$%.2f/hr",hourly):"—/hr";
        String distance=Double.isFinite(km) && km>0
                ?String.format(Locale.US,"$%.2f/km",km):"—/km";
        String label=(dash?"DoorDash":"Uber")+"\n"+hour+"\n"+distance;
        if(panel!=null&&!currentText.startsWith(dash?"DoorDash":"Uber"))hide();
        if(panel!=null && label.equals(currentText)) return true;
        try {
            if(panel==null) {
                panel=new LinearLayout(service);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setGravity(Gravity.CENTER);
                panel.setPadding(dp(14),dp(7),dp(14),dp(8));
                GradientDrawable bg=new GradientDrawable();
                bg.setColor(Color.argb(241,22,23,25));
                bg.setCornerRadius(dp(17));
                bg.setStroke(dp(1),Color.rgb(17,147,76));
                panel.setBackground(bg);
                panel.setElevation(dp(3));
                panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                perHour=new TextView(service);
                perHour.setTextSize(17);
                perHour.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                perHour.setTextColor(Color.rgb(147,239,167));
                perHour.setGravity(Gravity.CENTER);
                perHour.setIncludeFontPadding(false);
                perKm=new TextView(service);
                perKm.setTextSize(13);
                perKm.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                perKm.setTextColor(Color.WHITE);
                perKm.setGravity(Gravity.CENTER);
                perKm.setIncludeFontPadding(false);
                LinearLayout.LayoutParams below=new LinearLayout.LayoutParams(-2,-2);
                below.topMargin=dp(1);
                panel.addView(perHour,new LinearLayout.LayoutParams(-2,-2));
                panel.addView(perKm,below);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                p.gravity=Gravity.TOP|(dash?Gravity.CENTER_HORIZONTAL:Gravity.RIGHT);
                p.x=dp(8);
                p.y=dp(dash?65:10);
                p.setTitle("Offer Guardian estimated rates");
                wm.addView(panel,p);
            }
            perHour.setText((dash?"DD · ":"")+hour);
            perKm.setText(distance);
            currentText=label;
            return true;
        } catch(Exception e) {
            hide();
            return false;
        }
    }

    void hide() {
        if(panel!=null) {
            try {wm.removeViewImmediate(panel);} catch(Exception ignored) {}
        }
        panel=null;perHour=null;perKm=null;currentText="";
    }
}
