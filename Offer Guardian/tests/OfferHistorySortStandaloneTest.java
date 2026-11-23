package com.personal.offerguardian;

/** No Android SDK: validate the production History sort options and safe SQL. */
public final class OfferHistorySortStandaloneTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(OfferHistorySort.fromCode(null)==OfferHistorySort.NEWEST,"null preference");
        check(OfferHistorySort.fromCode("garbage' DROP TABLE offers;")==OfferHistorySort.NEWEST,"unsafe preference");
        check(OfferHistorySort.fromCode("HOURLY_HIGH")==OfferHistorySort.HOURLY_HIGH,"stored hourly preference");
        check(OfferHistorySort.values().length==5,"five supported sorts");
        check(OfferHistorySort.NEWEST.orderBy().startsWith("seen_at DESC"),"newest default");
        check(OfferHistorySort.PAYMENT_HIGH.orderBy().contains("payout_cents DESC"),"highest payment");
        check(OfferHistorySort.PAYMENT_LOW.orderBy().contains("payout_cents ASC"),"lowest payment");
        check(OfferHistorySort.HOURLY_HIGH.orderBy().contains(" / minutes"),"hourly arithmetic");
        check(OfferHistorySort.HOURLY_HIGH.orderBy().contains("ELSE 1 END ASC"),"unknown rates last");
        check(OfferHistorySort.HOURLY_LOW.orderBy().contains("ELSE 1 END ASC"),"unknown rates last ascending");
        check(OfferHistorySort.HOURLY_HIGH.orderBy().contains("CORRECTED"),"corrected verified rates count");
        check(OfferHistorySort.HOURLY_HIGH.orderBy().contains("minutes > 0"),"zero durations invalid");
        check(OfferHistorySort.HOURLY_HIGH.orderBy().contains(" ELSE NULL END DESC"),"highest rate direction");
        check(OfferHistorySort.HOURLY_LOW.orderBy().contains(" ELSE NULL END ASC"),"lowest rate direction");
        for(OfferHistorySort sort:OfferHistorySort.values()) {
            check(OfferHistorySort.fromCode(sort.code)==sort,"preference roundtrip: "+sort);
            check(sort.orderBy().endsWith("seen_at DESC, id DESC"),"stable ordering: "+sort);
            check(!sort.label.trim().isEmpty()&&!sort.shortLabel.trim().isEmpty(),"labels: "+sort);
        }
        System.out.println("PASS History sorting Java ("+checks+" assertions)");
    }
}
