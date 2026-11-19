package com.personal.offerguardian;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import java.util.Locale;

/**
 * Locally generated standalone alert; Uber Driver's notification channel does not
 * control this app's sound. Uses the system ALARM or MEDIA volume, as selected.
 * Silent mode / DND / connected Bluetooth devices can still suppress or route audio.
 */
final class OfferAlert {
    private static final String CHANNEL_ID = "high_offer_alerts_v1";
    private static MediaPlayer playing;
    private static int playsRemaining;
    private OfferAlert() {}

    /** Returns true when local audio playback started, not proof of audible speaker output. */
    static boolean signal(Context context, double payout) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = Prefs.of(app);
        boolean audible = playIndependentTone(app, prefs);
        if (prefs.getBoolean(Prefs.AUDIO_VIBRATE, true)) vibrate(app);
        showNotification(app, payout);
        return audible;
    }

    private static synchronized boolean playIndependentTone(Context app, SharedPreferences prefs) {
        stopCurrent();
        int tone = prefs.getInt(Prefs.AUDIO_TONE, 0);
        int res = tone == 1 ? R.raw.alert_urgent : R.raw.alert_bright;
        int percent = Math.max(0, Math.min(100, prefs.getInt(Prefs.AUDIO_VOLUME, 85)));
        int repeats = Math.max(1, Math.min(3, prefs.getInt(Prefs.AUDIO_REPEAT, 2)));
        boolean alarm = prefs.getBoolean(Prefs.AUDIO_USE_ALARM, true);
        if (percent == 0) return false;
        try {
            MediaPlayer player = new MediaPlayer();
            playing = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(alarm ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                    .build());
            // Android bundles this raw WAV inside the APK. No network or notification
            // permission is required for playing audio directly.
            try (AssetFileDescriptor sound = app.getResources().openRawResourceFd(res)) {
                if (sound != null) {
                    player.setDataSource(sound.getFileDescriptor(), sound.getStartOffset(), sound.getLength());
                } else {
                    player.setDataSource(app, Uri.parse("android.resource://" + app.getPackageName() + "/" + res));
                }
            }
            player.setVolume(percent / 100f, percent / 100f);
            playsRemaining = repeats;
            player.setOnCompletionListener(mp -> {
                synchronized (OfferAlert.class) {
                    if (mp != playing) return;
                    playsRemaining--;
                    if (playsRemaining <= 0) stopCurrent();
                    else {
                        try { mp.seekTo(0); mp.start(); }
                        catch (Exception ignored) { stopCurrent(); }
                    }
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                synchronized (OfferAlert.class) { if (mp == playing) stopCurrent(); }
                return true;
            });
            player.prepare();
            player.start();
            return true;
        } catch (Exception e) {
            stopCurrent();
            return false;
        }
    }

    static synchronized void stop() { stopCurrent(); }
    private static void stopCurrent() {
        if (playing != null) {
            try { playing.setOnCompletionListener(null); playing.stop(); }
            catch (Exception ignored) { }
            try { playing.release(); } catch (Exception ignored) { }
            playing = null;
        }
        playsRemaining = 0;
    }

    private static void vibrate(Context app) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = manager == null ? null : manager.getDefaultVibrator();
            } else vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0,180,100,260}, -1));
            }
        } catch (Exception ignored) { }
    }

    private static void showNotification(Context app, double payout) {
        if (Build.VERSION.SDK_INT >= 33 &&
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "High Uber Eats offers", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Silent notification for an offer at or above your payout minimum. Sound plays separately.");
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
