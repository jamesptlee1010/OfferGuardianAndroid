package com.personal.offerguardian;

import java.util.Locale;

/**
 * Pure-Java arithmetic means for offer history. Each detected offer counts once.
 * Offers with unknown time/distance do not dilute their respective rate average.
 * All values are estimates of gross advertised earnings, not completed-trip pay.
 */
public final class OfferHistoryStats {
    private int count, accepted, declined, highestCents;
    private long payoutSumCents, acceptedPayoutSumCents;
    private int hourlyCount, distanceCount;
    private double hourlySum, perKmSum;

    public void add(int payoutCents, int minutes, int metres, String outcome) {
        if (payoutCents < 0) return;
        count++;
        payoutSumCents += payoutCents;
        if (payoutCents > highestCents) highestCents = payoutCents;
        if ("ACCEPTED".equals(outcome)) {
            accepted++;
            acceptedPayoutSumCents += payoutCents;
        } else if ("DECLINED".equals(outcome)) declined++;
        if (minutes > 0) {
            hourlySum += payoutCents * 0.6 / minutes;
            hourlyCount++;
        }
        if (metres > 0) {
            perKmSum += payoutCents * 10.0 / metres;
            distanceCount++;
        }
    }

    public int count() { return count; }
    public int accepted() { return accepted; }
    public int declined() { return declined; }
    public int highestCents() { return highestCents; }
    public long acceptedPayoutCents() { return acceptedPayoutSumCents; }
    public int hourlySampleSize() { return hourlyCount; }
    public int kilometreSampleSize() { return distanceCount; }
    public String averageOffer() {
        return count==0 ? "—" : String.format(Locale.US,"A$%.2f",payoutSumCents / 100.0 / count);
    }
    public String averageHourly() {
        return hourlyCount==0 ? "—" : String.format(Locale.US,"A$%.2f/hr",hourlySum / hourlyCount);
    }
    public String averagePerKm() {
        return distanceCount==0 ? "—" : String.format(Locale.US,"A$%.2f/km",perKmSum / distanceCount);
    }
    public String highestOffer() { return count==0 ? "—" : OfferRate.money(highestCents); }
    public String acceptedEstimate() {
        return String.format(Locale.US,"A$%.2f",acceptedPayoutSumCents / 100.0);
    }
}
