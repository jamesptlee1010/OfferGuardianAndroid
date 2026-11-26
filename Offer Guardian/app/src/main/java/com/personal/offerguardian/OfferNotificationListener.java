package com.personal.offerguardian;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.os.SystemClock;
import android.os.Bundle;
/** Store only a short-lived signal, never notification content. */
public final class OfferNotificationListener extends NotificationListenerService {
    private String previousKey="";
    private long previousPostTime=0,previousElapsed=0;
    @Override public void onNotificationPosted(StatusBarNotification s) {
        if(s==null||!OfferSurfacePolicy.DOORDASH.equals(s.getPackageName()))return;
        Notification n=s.getNotification();if(n==null||n.extras==null)return;
        Bundle e=n.extras;
        if(!CrossPlatformPolicy.isDashOfferNotification(s.getPackageName(),String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE)),String.valueOf(e.getCharSequence(Notification.EXTRA_TEXT)),String.valueOf(e.getCharSequence(Notification.EXTRA_BIG_TEXT))))return;
        long wallAge=System.currentTimeMillis()-s.getPostTime(),now=SystemClock.elapsedRealtime();
        if(wallAge>16000||wallAge< -3000)return;
        if(s.getKey().equals(previousKey)&&(s.getPostTime()==previousPostTime||now-previousElapsed<2500))return;
        previousKey=s.getKey();previousPostTime=s.getPostTime();previousElapsed=now;
        android.content.SharedPreferences p=Prefs.of(this);
        if(p.getBoolean(Prefs.MONITOR,false)&&p.getBoolean(Prefs.DASH_ENABLED,true)&&p.getBoolean(Prefs.AUTO_SWITCH,false))p.edit().putLong(Prefs.DASH_NOTICE,now).apply();
    }
}
