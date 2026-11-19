package com.personal.offerguardian;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import java.util.Locale;

/** Emits one short audible signal and a local notification; never accepts requests. */
final class OfferAlert {
    private static final String CHANNEL_ID = "high_offer_alerts_v1";
    private static Ringtone playing;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private OfferAlert() {}

    static void signal(Context context, double payout) {
        final Context app = context.getApplicationContext();
        try {
            if (playing != null && playing.isPlaying()) playing.stop();
            android.net.Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            playing = RingtoneManager.getRingtone(app, sound);
            if (playing != null) {
                playing.play();
                Ringtone current = playing;
                HANDLER.postDelayed(() -> {
                    try { if (current.isPlaying()) current.stop(); } catch (Exception ignored) { }
                }, 2500);
            }
        } catch (Exception ignored) { /* Devices may block sounds in silent / DND mode. */ }
        vibrate(app);
        showNotification(app, payout);
    }

    private static void vibrate(Context app) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) { }
    }

    private static void showNotification(Context app, double payout) {
        if (Build.VERSION.SDK_INT >= 33 &&
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "High Uber Eats offers", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifies when a readable Uber Driver offer meets your limit");
        // Audio plays once in signal() instead; avoid duplicate notification sounds.
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        Intent openUber = app.getPackageManager().getLaunchIntentForPackage("com.ubercab.driver");
        if (openUber == null) openUber = new Intent(app, MainActivity.class);
        openUber.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intent = PendingIntent.getActivity(app, 17, openUber,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(String.format(Locale.US, "Uber Eats offer: A$%.2f", payout))
                .setContentText("Meets your minimum. Open Uber Driver to decide.")
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        manager.notify(2701, notification);
    }
}
