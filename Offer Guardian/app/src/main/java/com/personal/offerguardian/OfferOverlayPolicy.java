package com.personal.offerguardian;

/** Pure safety gate: the floating badge never advertises a rejected or uncertain offer. */
public final class OfferOverlayPolicy {
    private OfferOverlayPolicy() {}
    public static boolean shouldShow(OfferRuleEngine.Decision decision,
                                     boolean overlayEnabled, boolean monitoringEnabled,
                                     boolean uberForeground, boolean verifiedCard) {
        return overlayEnabled && monitoringEnabled && uberForeground && verifiedCard &&
                decision!=null && decision.outcome==OfferRuleEngine.Outcome.SHOW;
    }
}
