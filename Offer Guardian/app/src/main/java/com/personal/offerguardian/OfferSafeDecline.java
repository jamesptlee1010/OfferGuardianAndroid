package com.personal.offerguardian;

/**
 * A duration such as "1 hr" can be a verified LOWER BOUND, never an exact hour.
 * This deliberately permits only declines proved to fail the active rules even
 * when the missing minute component is as favourable to the driver as possible.
 * The caller MUST confirm the same bounded reading on two fresh screenshots
 * before taking any action, and record exact minutes as unknown in history.
 */
public final class OfferSafeDecline {
    private OfferSafeDecline() {}

    public static boolean hasReliableHourBound(OfferDetailsParser.Details d) {
        return d != null && !d.conflictingDuration && d.hoursSeen && !d.minutesSeen &&
                d.parsedHours >= 1 && d.parsedHours <= 12 &&
                d.minutes == d.parsedHours * 60 &&
                d.metres >= 0 && d.metres <= 300000 &&
                d.pickup != null && d.pickup.trim().length() >= 3 &&
                d.rawDuration != null && !d.rawDuration.trim().isEmpty();
    }

    public static OfferRuleEngine.Decision evaluate(OfferRuleEngine.Config rules,
                                                    double payout,
                                                    OfferDetailsParser.Details d) {
        if (!hasReliableHourBound(d) || !Double.isFinite(payout) || payout < 3 || payout > 1000)
            return new OfferRuleEngine.Decision(OfferRuleEngine.Outcome.REVIEW,
                    "Incomplete hour reading; no safe bound", payout,Double.NaN,Double.NaN);
        // At least H*60 minutes; actual A$/hr is at most this computed bound.
        // If even the MAXIMUM possible rate fails every OR rule, rejecting is safe.
        OfferRuleEngine.Decision conservative=OfferRuleEngine.evaluate(
                rules,payout,d.parsedHours*60,d.metres,d.deliveryCount);
        if (conservative.outcome != OfferRuleEngine.Outcome.DECLINE)
            return new OfferRuleEngine.Decision(OfferRuleEngine.Outcome.REVIEW,
                    "Hour-only OCR could still qualify; await exact duration",
                    payout,Double.NaN,conservative.perKm);
        return new OfferRuleEngine.Decision(OfferRuleEngine.Outcome.DECLINE,
                "Safe duration bound: at least " + (d.parsedHours*60) +
                " min; maximum possible hourly A$" +
                String.format(java.util.Locale.US,"%.2f",conservative.hourly) +
                "/hr; selected rules cannot qualify",
                payout,Double.NaN,conservative.perKm);
    }
}
