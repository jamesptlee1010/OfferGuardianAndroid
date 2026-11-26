package com.personal.offerguardian;
final class OfferDecisionProtection {
    static boolean nearHourly(OfferRuleEngine.Config config, OfferRuleEngine.Decision d){
        return config!=null&&config.hourlyOn&&Double.isFinite(d.hourly)&&Math.abs(d.hourly-config.minHourly)<=2.0;
    }
    // Timer continuity + merchant/stop identity prevents a subsequent conflicting read from rejecting an already qualifying card.
    static boolean sameVisibleDash(DoorDashOffer.Read a, DoorDashOffer.Read b,long elapsed){
        if(a==null||b==null||elapsed<0||elapsed>60000||a.stops!=b.stops||a.pickup.isEmpty()||!a.pickup.equalsIgnoreCase(b.pickup))return false;
        return a.seconds>0&&b.seconds>0&&b.seconds<=a.seconds&&Math.abs((a.seconds-b.seconds)*1000L-elapsed)<=4500;
    }
}
