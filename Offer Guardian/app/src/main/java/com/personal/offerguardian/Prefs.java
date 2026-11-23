package com.personal.offerguardian;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings remain in Android private app storage; no network connectivity is requested. */
final class Prefs {
    private Prefs() {}
    private static final String NAME = "offer_guardian_prefs";
    static final String MONITOR = "monitor";
    static final String OVERLAY_ENABLED = "qualifying_overlay_enabled";
    static final String ACCEPT_TRACKING = "accept_tracking_enabled";
    static final String MIGRATED_V08 = "migrated_v08";
    static final String AUTO_DECLINE = "auto_decline";
    static final String ALERT = "alert";
    // Standalone alert audio settings; independent of Uber Driver notification categories.
    static final String AUDIO_USE_ALARM = "audio_use_alarm";
    static final String AUDIO_VOLUME = "audio_volume";
    static final String AUDIO_REPEAT = "audio_repeat";
    static final String AUDIO_TONE = "audio_tone";
    static final String AUDIO_VIBRATE = "audio_vibrate";
    static final String THRESHOLD = "threshold"; // Former minimum; remains the price rule value.
    static final String RULE_PRICE = "rule_price_enabled";
    static final String RULE_HOURLY = "rule_hour_enabled";
    static final String RULE_KM = "rule_km_enabled";
    static final String RULE_MIN_HOUR = "rule_min_hour";
    static final String RULE_MIN_KM = "rule_min_km";
    static final String RULE_MODE = "rule_combine_mode";
    static final String RULE_DELIVERIES = "rule_min_deliveries_enabled";
    static final String RULE_MIN_DELIVERIES = "rule_min_deliveries";
    // v0.9.8: new, explicitly activated tier rules. Legacy keys are retained
    // untouched so an upgrade does not silently change existing decline behaviour.
    static final String TIERED_ACTIVE = "tiered_rules_active_v098";
    static final String TIERED_GENERATION = "tiered_rules_generation_v098";
    private static final String TIER_KEY = "tier_v098_";
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
    static boolean tieredEnabled(SharedPreferences p) {return p.getBoolean(TIERED_ACTIVE,false);}
    static String tierKey(int count,String field) {
        if(count<1||count>3)throw new IllegalArgumentException("Tier must be 1, 2 or 3");
        return TIER_KEY+count+"_"+field;
    }
    static TieredOfferRules tieredRules(SharedPreferences p) {
        OfferRuleEngine.Config legacy=rules(p);
        OfferRuleEngine.Config[] cs=new OfferRuleEngine.Config[3];
        boolean[] enabled=new boolean[3];
        for(int i=0;i<3;i++) {
            int n=i+1;
            enabled[i]=p.getBoolean(tierKey(n,"enabled"),true);
            OfferRuleEngine.Mode mode="ALL".equals(p.getString(tierKey(n,"mode"),legacy.mode.name()))
                    ?OfferRuleEngine.Mode.ALL:OfferRuleEngine.Mode.ANY;
            cs[i]=new OfferRuleEngine.Config(
                p.getBoolean(tierKey(n,"price_on"),legacy.priceOn),
                p.getFloat(tierKey(n,"min_price"),(float)legacy.minPrice),
                p.getBoolean(tierKey(n,"hourly_on"),legacy.hourlyOn),
                p.getFloat(tierKey(n,"min_hourly"),(float)legacy.minHourly),
                p.getBoolean(tierKey(n,"km_on"),legacy.kilometreOn),
                p.getFloat(tierKey(n,"min_per_km"),(float)legacy.minPerKm),mode,
                false,1,
                p.getBoolean(tierKey(n,"distance_on"),false),
                p.getFloat(tierKey(n,"max_distance"),15.0f));
        }
        return new TieredOfferRules(cs,enabled);
    }
    static String ruleSummary(SharedPreferences p) {
        return tieredEnabled(p)?tieredRules(p).summary():OfferRuleEngine.summary(rules(p));
    }
    static OfferRuleEngine.Config rules(SharedPreferences p) {
        String selection=p.getString(RULE_MODE,"ANY");
        OfferRuleEngine.Mode mode="ALL".equals(selection)?OfferRuleEngine.Mode.ALL:OfferRuleEngine.Mode.ANY;
        return new OfferRuleEngine.Config(
            p.getBoolean(RULE_PRICE,true),threshold(p),
            p.getBoolean(RULE_HOURLY,false),p.getFloat(RULE_MIN_HOUR,40.0f),
            p.getBoolean(RULE_KM,false),p.getFloat(RULE_MIN_KM,3.0f),mode,
            p.getBoolean(RULE_DELIVERIES,false),p.getInt(RULE_MIN_DELIVERIES,2));
    }
}
