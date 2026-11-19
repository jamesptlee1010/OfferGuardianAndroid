package com.personal.offerguardian;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings remain in Android private app storage; no network connectivity is requested. */
final class Prefs {
    private Prefs() {}
    private static final String NAME = "offer_guardian_prefs";
    static final String MONITOR = "monitor";
    static final String AUTO_DECLINE = "auto_decline";
    static final String ALERT = "alert";
    // Standalone alert audio settings; independent of Uber Driver notification categories.
    static final String AUDIO_USE_ALARM = "audio_use_alarm";
    static final String AUDIO_VOLUME = "audio_volume";
    static final String AUDIO_REPEAT = "audio_repeat";
    static final String AUDIO_TONE = "audio_tone";
    static final String AUDIO_VIBRATE = "audio_vibrate";
    static final String THRESHOLD = "threshold";
    static final String LEARNING = "learning";
    static final String PENDING = "pending";
    static final String PENDING_ID = "pending_id";
    static final String PENDING_LABEL = "pending_label";
    static final String PENDING_CLASS = "pending_class";
    static final String PENDING_X = "pending_x";
    static final String PENDING_Y = "pending_y";
    static final String PENDING_WIDTH = "pending_width";
    static final String PENDING_HEIGHT = "pending_height";
    static final String CALIBRATED = "calibrated";
    static final String CAL_ID = "cal_id";
    static final String CAL_LABEL = "cal_label";
    static final String CAL_CLASS = "cal_class";
    static final String CAL_X = "cal_x";
    static final String CAL_Y = "cal_y";
    static final String CAL_WIDTH = "cal_width";
    static final String CAL_HEIGHT = "cal_height";
    static final String COORD_FALLBACK = "coord_fallback";
    static final String VISUAL_X = "visual_x_enabled";
    static final String VISUAL_DRY_RUN = "visual_x_dry_run";
    static final String LIVE_STATUS = "live_status";
    static final String LIVE_TIME = "live_time";
    static final String DIAG_HISTORY = "diagnostic_history";
    static final String MIGRATED_V03 = "migrated_v03";
    static final String MIGRATED_V04 = "migrated_v04";
    static final String LAST_STATUS = "last_status";
    static final String LAST_AMOUNT = "last_amount";
    static final String LAST_TIME = "last_time";

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
    static float threshold(SharedPreferences p) {
        return p.getFloat(THRESHOLD, 30.0f);
    }
}
