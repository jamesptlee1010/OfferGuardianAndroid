package com.personal.offerguardian;

public final class OfferHourlyRateStandaloneTest {
    private static int count=0;
    private static void eq(String got,String want){count++;if(!want.equals(got))throw new AssertionError("wanted " + want + " got " + got);}
    public static void main(String[] args){
        eq(OfferRate.dollarsPerHour(2000,27),"A$44.44/hr");
        eq(OfferRate.dollarsPerHour(1903,43),"A$26.55/hr");
        eq(OfferRate.dollarsPerHour(1200,30),"A$24.00/hr");
        eq(OfferRate.dollarsPerHour(3500,60),"A$35.00/hr");
        eq(OfferRate.dollarsPerHour(0,30),"A$0.00/hr");
        eq(OfferRate.dollarsPerHour(1000,0),"N/A");
        eq(OfferRate.dollarsPerHour(-100,10),"N/A");
        eq(OfferRate.dollarsPerKm(1903,20300),"A$0.94/km");
        System.out.println("PASS OfferHourlyRate "+count+" cases");
    }
}
