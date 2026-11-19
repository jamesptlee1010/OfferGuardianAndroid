package com.personal.offerguardian;

/** Pure rule: high-value sound only for one verified offer meeting the threshold. */
public final class OfferAlertRules {
    private OfferAlertRules() {}
    public static boolean shouldRing(double payout, double threshold, boolean alertsEnabled) {
        return alertsEnabled && Double.isFinite(payout) && Double.isFinite(threshold) &&
                payout > 0 && threshold > 0 && payout >= threshold;
    }
}
