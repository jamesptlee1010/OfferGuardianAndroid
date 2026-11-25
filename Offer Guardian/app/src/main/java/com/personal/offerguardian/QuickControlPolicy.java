package com.personal.offerguardian;

import java.util.Locale;

/** Fail closed: never pause an active delivery or confuse an offer with the idle screen. */
final class QuickControlPolicy {
    enum Screen { IDLE, MENU, PAUSED, UNKNOWN }
    static Screen screen(String raw) {
        String s=raw==null?"":raw.toLowerCase(Locale.ROOT).replace('’','\'');
        if(s.contains("customer drop-off")||s.contains("decline offer")||s.contains("accept offer")||s.contains("confirm pickup")||s.contains("complete delivery")||s.contains("arrived at store")||s.contains("directions")||s.contains("pick up by")||s.contains("deliver by")||s.contains("cancel delivery"))return Screen.UNKNOWN;
        if(s.contains("dash paused")&&s.contains("resume dash"))return Screen.PAUSED;
        if(s.contains("current dash")&&s.contains("pause orders")&&s.contains("this dash")&&s.contains("dash ends at"))return Screen.MENU;
        if(s.contains("finding offers")&&s.contains("this dash")&&(s.contains("hotspots")||s.contains("zone offer wait")))return Screen.IDLE;
        return Screen.UNKNOWN;
    }
}
