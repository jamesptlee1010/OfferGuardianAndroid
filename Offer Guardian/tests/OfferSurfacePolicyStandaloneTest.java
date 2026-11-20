package com.personal.offerguardian;

/** Run without Android to verify the new Uber/Waze safety boundaries. */
public final class OfferSurfacePolicyStandaloneTest {
    private static void ok(boolean pass,String description) {
        if(!pass)throw new AssertionError(description);
    }
    public static void main(String[] args) {
        ok(OfferSurfacePolicy.classify("com.ubercab.driver")==OfferSurfacePolicy.Host.UBER,"Uber host");
        ok(OfferSurfacePolicy.classify("com.waze")==OfferSurfacePolicy.Host.WAZE,"Waze host");
        ok(!OfferSurfacePolicy.shouldScan(OfferSurfacePolicy.classify("com.example.random")),"Other apps must be ignored");
        ok(OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.UBER,true,false,1,.96),"Uber verified card allowed");
        ok(!OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.WAZE,true,true,1,.99),"Waze single frame forbidden");
        ok(!OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.WAZE,true,false,2,.99),"Waze missing offer identity forbidden");
        ok(!OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.WAZE,true,true,2,.93),"Waze weak X forbidden");
        ok(!OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.WAZE,false,true,2,.99),"Waze no card forbidden");
        ok(OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.WAZE,true,true,2,.98),"Waze two good frames allowed");
        ok(!OfferSurfacePolicy.allowDecline(OfferSurfacePolicy.Host.OTHER,true,true,10,.99),"Untrusted host forbidden");
        System.out.println("Uber/Waze foreground and decline safety policy PASS (10 tests)");
    }
}
