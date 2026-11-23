package com.personal.offerguardian;

/**
 * History-only ordering. The SQLite ORDER BY expression is constant code, never
 * interpolated from user input. Unverified/unknown hourly rates go LAST in
 * both directions; they are not treated as zero or as an inflated OCR rate.
 */
public enum OfferHistorySort {
    NEWEST("NEWEST", "Newest first", "Sort ▾"),
    PAYMENT_HIGH("PAYMENT_HIGH", "Highest payment first", "Sort: A$ ↓"),
    PAYMENT_LOW("PAYMENT_LOW", "Lowest payment first", "Sort: A$ ↑"),
    HOURLY_HIGH("HOURLY_HIGH", "Highest hourly rate first", "Sort: /hr ↓"),
    HOURLY_LOW("HOURLY_LOW", "Lowest hourly rate first", "Sort: /hr ↑");

    public final String code, label, shortLabel;

    OfferHistorySort(String code, String label, String shortLabel) {
        this.code = code;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public static OfferHistorySort fromCode(String code) {
        for (OfferHistorySort item : values()) {
            if (item.code.equals(code)) return item;
        }
        return NEWEST;
    }

    public boolean hourly() {
        return this == HOURLY_HIGH || this == HOURLY_LOW;
    }

    public String orderBy() {
        if (this == PAYMENT_HIGH) return "payout_cents DESC, seen_at DESC, id DESC";
        if (this == PAYMENT_LOW) return "payout_cents ASC, seen_at DESC, id DESC";
        if (hourly()) {
            String verified = "extraction_status IN ('VERIFIED','CORRECTED') AND minutes > 0";
            String rate = "CASE WHEN " + verified +
                " THEN CAST(payout_cents AS REAL) / minutes ELSE NULL END";
            return "CASE WHEN " + verified + " THEN 0 ELSE 1 END ASC, " +
                rate + (this == HOURLY_HIGH ? " DESC" : " ASC") +
                ", seen_at DESC, id DESC";
        }
        return "seen_at DESC, id DESC";
    }
}
