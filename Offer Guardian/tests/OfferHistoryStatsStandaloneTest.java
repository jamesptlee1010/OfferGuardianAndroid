package com.personal.offerguardian;

/** Host-side tests: all values are illustrative, no personal data. */
public final class OfferHistoryStatsStandaloneTest {
    static int checks;
    static void eq(Object actual,Object expected) {
        checks++;
        if(!expected.equals(actual)) throw new AssertionError("expected "+expected+" but got "+actual);
    }
    public static void main(String[] args) {
        OfferHistoryStats empty=new OfferHistoryStats();
        eq(empty.count(),0);eq(empty.averageOffer(),"—");
        eq(empty.averageHourly(),"—");eq(empty.averagePerKm(),"—");
        eq(empty.highestOffer(),"—");
        OfferHistoryStats all=new OfferHistoryStats();
        all.add(2000,27,5000,"ACCEPTED");   // 20, 44.44/hr, 4/km
        all.add(1200,30,6000,"DECLINED");  // 12, 24/hr, 2/km
        all.add(3000,0,0,"UNKNOWN");       // 30, missing rates
        eq(all.count(),3);eq(all.accepted(),1);eq(all.declined(),1);
        eq(all.highestCents(),3000);eq(all.highestOffer(),"A$30.00");
        eq(all.averageOffer(),"A$20.67");
        eq(all.averageHourly(),"A$34.22/hr");
        eq(all.averagePerKm(),"A$3.00/km");
        eq(all.hourlySampleSize(),2);eq(all.kilometreSampleSize(),2);
        eq(all.acceptedPayoutCents(),2000L);eq(all.acceptedEstimate(),"A$20.00");
        // Accepted tab is a separate aggregate of accepted records, not all records.
        OfferHistoryStats accepted=new OfferHistoryStats();
        accepted.add(2000,27,5000,"ACCEPTED");
        eq(accepted.averageOffer(),"A$20.00");
        eq(accepted.averageHourly(),"A$44.44/hr");
        eq(accepted.averagePerKm(),"A$4.00/km");
        // Missing metrics never become zero-valued rates.
        OfferHistoryStats missing=new OfferHistoryStats();
        missing.add(5000,0,0,"UNKNOWN");
        eq(missing.averageOffer(),"A$50.00");
        eq(missing.averageHourly(),"—");
        eq(missing.averagePerKm(),"—");
        // Invalid negative payout is ignored rather than spoiling averages.
        missing.add(-100,10,1000,"DECLINED");
        eq(missing.count(),1);
        System.out.println("PASS OfferHistoryStats "+checks+" assertions");
    }
}
