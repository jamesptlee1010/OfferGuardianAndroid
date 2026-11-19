package com.personal.offerguardian;

/** Host-side checks. No Android SDK required. */
public final class OfferAlertRulesStandaloneTest {
    private static int checks;
    private static void eq(boolean expected, double amount, double threshold, boolean on) {
        boolean got = OfferAlertRules.shouldRing(amount, threshold, on);
        if (got != expected) throw new AssertionError("Expected " + expected + " for payout " + amount +
                ", threshold " + threshold + ", enabled " + on + "; got " + got);
        checks++;
    }
    public static void main(String[] args) {
        eq(false, 7.04, 30.0, true);
        eq(false, 11.47, 30.0, true);
        eq(false, 12.41, 30.0, true);
        eq(false, 19.03, 30.0, true);
        eq(false, 29.99, 30.0, true);
        eq(true, 30.00, 30.0, true);
        eq(true, 30.59, 30.0, true);
        eq(true, 45.00, 30.0, true);
        eq(false, 45.00, 30.0, false);
        eq(false, Double.NaN, 30.0, true);
        eq(false, Double.POSITIVE_INFINITY, 30.0, true);
        eq(false, 30.59, Double.NaN, true);
        eq(false, 0, 30.0, true);
        eq(false, -1, 30.0, true);
        eq(false, 30.59, 0, true);
        System.out.println("Independent high-offer sound rules: " + checks + " tests PASSED");
    }
}
