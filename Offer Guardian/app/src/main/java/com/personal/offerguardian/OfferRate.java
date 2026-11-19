package com.personal.offerguardian;

import java.util.Locale;

/** Calculations use offered AUD cents and advertised kilometres (not actual paid earnings). */
public final class OfferRate {
    private OfferRate() {}

    public static String dollarsPerKm(int payoutCents, int distanceMetres) {
        if (distanceMetres <= 0 || payoutCents < 0) return "N/A";
        double audPerKm = payoutCents * 10.0 / distanceMetres;
        return String.format(Locale.US, "A$%.2f/km", audPerKm);
    }

    /** Estimated gross hourly earning rate based on Uber's stated minutes. */
    public static String dollarsPerHour(int payoutCents, int estimatedMinutes) {
        if (estimatedMinutes <= 0 || payoutCents < 0) return "N/A";
        double audPerHour = payoutCents * 0.6 / estimatedMinutes;
        return String.format(Locale.US, "A$%.2f/hr", audPerHour);
    }

    public static String distance(int metres) {
        if (metres <= 0) return "Distance unknown";
        return String.format(Locale.US, "%.1f km", metres / 1000.0);
    }

    public static String money(int cents) {
        return String.format(Locale.US, "A$%.2f", cents / 100.0);
    }
}
