package com.personal.offerguardian;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.os.Bundle;
/** Store only a short-lived signal, never notification content. */
public final class OfferNotificationListener extends NotificationListenerService {
    private static volatile PendingIntent latestIntent;
    private static volatile long latestAt;
    private static volatile boolean connected;
    static String connectionStatus(Context context){
        String enabled=android.provider.Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
        boolean allowed=enabled!=null&&enabled.contains(context.getPackageName()+"/");
        return (allowed?"access enabled":"access NOT enabled")+"; "+(connected?"listener connected":"listener not connected");
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected();connected=true;
        try{StatusBarNotification[] active=getActiveNotifications();if(active!=null)for(StatusBarNotification n:active)onNotificationPosted(n);}catch(Exception ignored){}
    }
    @Override public void onListenerDisconnected(){
        connected=false;latestIntent=null;latestAt=0;Prefs.of(this).edit().remove(Prefs.DASH_NOTICE).apply();
        super.onListenerDisconnected();
    }
    static void clearPending(){latestIntent=null;latestAt=0;}
    static boolean openLatest(Context context,long now){
        PendingIntent intent=latestIntent;long at=latestAt;latestIntent=null;latestAt=0;
        if(intent==null||!CrossPlatformPolicy.freshNotice(at,now)||!OfferSurfacePolicy.DOORDASH.equals(intent.getCreatorPackage())||(android.os.Build.VERSION.SDK_INT>=31&&!intent.isActivity()))return false;
        try{intent.send();return true;}catch(Exception denied){return false;}
    }
    private String previousKey="";
    private long previousPostTime=0,previousElapsed=0;
    @Override public void onNotificationPosted(StatusBarNotification s) {
        if(s==null||!OfferSurfacePolicy.DOORDASH.equals(s.getPackageName())||!Prefs.of(this).getBoolean(Prefs.DASH_ENABLED,true))return;
        Notification n=s.getNotification();if(n==null||n.extras==null){Prefs.of(this).edit().putString(Prefs.DASH_NOTICE_DIAG,"Dasher notification received; no extras available").apply();return;}
        Bundle e=n.extras;
        android.content.SharedPreferences p=Prefs.of(this);
        java.util.ArrayList<String> fields=new java.util.ArrayList<>();
        for(String key:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,Notification.EXTRA_SUB_TEXT,Notification.EXTRA_SUMMARY_TEXT})fields.add(String.valueOf(e.getCharSequence(key)));
        CharSequence[] textLines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(textLines!=null)for(CharSequence line:textLines)fields.add(String.valueOf(line));
        if(!CrossPlatformPolicy.isDashOfferNotification(s.getPackageName(),fields.toArray(new String[0]))){p.edit().putString(Prefs.DASH_NOTICE_DIAG,"Dasher notification received; not an offer trigger").apply();return;}
        long wallAge=System.currentTimeMillis()-s.getPostTime(),now=SystemClock.elapsedRealtime();
        if(wallAge>16000||wallAge< -3000){p.edit().putString(Prefs.DASH_NOTICE_DIAG,"Dasher offer notification received but stale").apply();return;}
        if(s.getKey().equals(previousKey)&&(s.getPostTime()==previousPostTime||now-previousElapsed<2500))return;
        previousKey=s.getKey();previousPostTime=s.getPostTime();previousElapsed=now;
        p.edit().putString(Prefs.DASH_NOTICE_DIAG,"New Delivery! matched at "+new java.util.Date()+"; monitoring="+p.getBoolean(Prefs.MONITOR,false)+"; switching="+p.getBoolean(Prefs.AUTO_SWITCH,false)).apply();
        if(p.getBoolean(Prefs.MONITOR,false)&&p.getBoolean(Prefs.DASH_ENABLED,true)&&p.getBoolean(Prefs.AUTO_SWITCH,false)){
            latestIntent=n.contentIntent;latestAt=now;
            p.edit().putLong(Prefs.DASH_NOTICE,now).apply();
        }
    }
}
