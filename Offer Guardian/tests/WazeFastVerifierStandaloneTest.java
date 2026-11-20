package com.personal.offerguardian;

/** Waze must never tap on one frame or on mismatched frames. */
public final class WazeFastVerifierStandaloneTest {
    private static int passed=0;
    private static void ok(boolean condition,String label){
        if(!condition)throw new AssertionError(label);passed++;
    }
    public static void main(String[] ignored) {
        WazeFastVerifier v=new WazeFastVerifier();
        ok(!v.observe(12.41,579,750,676,.97,true,"Subway Kedron",1000),"First safe frame never authorises tap");
        ok(v.isPending(),"Pending for fast confirmation");
        ok(v.observe(12.41,580,749,675,.96,true,"Subway Kedron",1460),"Matching safe second frame");
        ok(!v.isPending(),"Clears after confirmation");
        ok(!v.observe(12.41,579,750,676,.97,true,"Subway Kedron",2000),"A new first frame isn't confirmation");
        ok(!v.observe(13.15,579,750,676,.97,true,"Subway Kedron",2500),"Price change resets");
        ok(!v.observe(13.15,590,750,676,.97,true,"Subway Kedron",2900),"X movement resets");
        ok(!v.observe(13.15,590,750,676,.97,true,"Other shop",3280),"Merchant mismatch resets");
        ok(!v.observe(13.15,590,750,676,.97,true,"Other shop",5700),"Expired evidence resets");
        ok(!v.observe(13.15,590,750,676,.97,false,"Other shop",6000),"Unverified text disallowed");
        ok(!v.observe(13.15,590,750,676,.92,true,"Other shop",6300),"Low confidence disallowed");
        ok(!v.observe(13.15,590,750,676,.99,true,"",6800),"No identified merchant disallowed");
        ok(!v.isPending(),"Safety rejection clears pending");
        ok(!v.observe(14.0,579,742,670,.95,true,"KFC",7000),"Fresh first frame");
        ok(!v.observe(14.0,579,742,670,.95,true,"KFC",7000),"Same timestamp not second frame");
        ok(v.observe(14.0,579,742,670,.95,true,"KFC",7450),"Fresh subsequent frame authorises");
        v.reset();ok(!v.isPending(),"Reset clears");
        ok(OfferScanTiming.summary(100,210,235,315).contains("capture 110ms | X 25ms | OCR 80ms | scan 215ms"),"Measured stage timings");
        System.out.println("Waze fast confirmation + timing tests passed: "+passed);
    }
}
