package com.personal.offerguardian;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.os.SystemClock;
import java.util.Locale;
/** Store only a short-lived offer signal, never notification text or credentials. */
public final class OfferNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification s) {
        if(s==null||!OfferSurfacePolicy.DOORDASH.equals(s.getPackageName()))return;
        Notification n=s.getNotification();if(n==null||n.extras==null)return;
        String text=(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE))+" "+String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT))+" "+String.valueOf(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))).toLowerCase(Locale.ROOT);
        if(text.contains("new offer")||text.contains("new order")||text.contains("new delivery")||text.contains("delivery opportunity")||text.contains("new dash offer")||text.contains("you have a new")){
            android.content.SharedPreferences p=Prefs.of(this);
            if(p.getBoolean(Prefs.MONITOR,false)&&p.getBoolean(Prefs.DASH_ENABLED,true)&&p.getBoolean(Prefs.AUTO_SWITCH,false))p.edit().putLong(Prefs.DASH_NOTICE,SystemClock.elapsedRealtime()).apply();
        }
    }
}
