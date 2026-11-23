package com.personal.offerguardian;

/** Regression coverage for independent 1/2/3-delivery rules in v0.9.8. */
public final class TieredOfferRulesStandaloneTest {
    private static int assertions=0;
    private static void eq(Object actual,Object want,String message) {
        assertions++;
        if(!want.equals(actual))throw new AssertionError(message+": got "+actual+" expected "+want);
    }
    private static void yes(boolean actual,String message){eq(actual,true,message);}
    private static OfferRuleEngine.Config price(double minimum){
        return new OfferRuleEngine.Config(true,minimum,false,40,false,3,OfferRuleEngine.Mode.ANY);
    }
    private static OfferRuleEngine.Config full(boolean priceOn,double minPrice,
            boolean hourOn,double minHour,boolean kmOn,double minKm,boolean maxOn,double maxKm,
            OfferRuleEngine.Mode mode) {
        return new OfferRuleEngine.Config(priceOn,minPrice,hourOn,minHour,kmOn,minKm,mode,
                false,1,maxOn,maxKm);
    }
    public static void main(String[] args) {
        TieredOfferRules categories=new TieredOfferRules(
                new OfferRuleEngine.Config[]{price(25),price(35),price(50)},
                new boolean[]{true,true,true});
        yes(categories.valid(),"three independent tiers valid");
        yes(categories.provenPriceFailure(1,5),"solo price-only failure provable without duration");
        eq(categories.provenPriceFailure(1,25),false,"solo exactly threshold not a failure");
        eq(categories.evaluate(5,11,2000,1).outcome,OfferRuleEngine.Outcome.DECLINE,"$5 solo decline");
        eq(categories.evaluate(24.99,30,2000,1).outcome,OfferRuleEngine.Outcome.DECLINE,"solo under 25");
        eq(categories.evaluate(25,30,2000,1).outcome,OfferRuleEngine.Outcome.SHOW,"solo exactly 25");
        eq(categories.evaluate(30,30,2000,1).outcome,OfferRuleEngine.Outcome.SHOW,"solo qualifies despite double minimum 35");
        eq(categories.evaluate(30,30,2000,2).outcome,OfferRuleEngine.Outcome.DECLINE,"double below its own 35");
        eq(categories.evaluate(35,30,2000,2).outcome,OfferRuleEngine.Outcome.SHOW,"double exactly 35");
        eq(categories.evaluate(49,30,2000,3).outcome,OfferRuleEngine.Outcome.DECLINE,"triple below its own 50");
        eq(categories.evaluate(50,30,2000,3).outcome,OfferRuleEngine.Outcome.SHOW,"triple exactly 50");
        eq(categories.evaluate(100,30,2000,4).outcome,OfferRuleEngine.Outcome.REVIEW,"4+ manual review");
        eq(categories.evaluate(100,30,2000,8).outcome,OfferRuleEngine.Outcome.REVIEW,"8+ manual review");
        eq(categories.evaluate(100,30,2000,0).outcome,OfferRuleEngine.Outcome.REVIEW,"unreadable count manual review");
        TieredOfferRules disabled=new TieredOfferRules(
                new OfferRuleEngine.Config[]{price(25),price(35),price(50)},
                new boolean[]{true,false,true});
        eq(disabled.evaluate(1,10,1000,2).outcome,OfferRuleEngine.Outcome.REVIEW,"disabled category manual review");
        eq(disabled.evaluate(1,10,1000,1).outcome,OfferRuleEngine.Outcome.DECLINE,"enabled category still declines");
        TieredOfferRules none=new TieredOfferRules(
                new OfferRuleEngine.Config[]{price(25),price(35),price(50)},
                new boolean[]{false,false,false});
        eq(none.valid(),false,"all tiers disabled rejected at save");
        OfferRuleEngine.Config invalid=full(false,25,false,55,false,3,false,5,OfferRuleEngine.Mode.ALL);
        TieredOfferRules invalidGroup=new TieredOfferRules(
                new OfferRuleEngine.Config[]{invalid,price(35),price(50)},new boolean[]{true,true,true});
        eq(invalidGroup.valid(),false,"active tier must have a rule");
        // Hourly: $40 / 60 minutes = $40/hr. AND and OR modes are tier-local.
        OfferRuleEngine.Config hourAnd=full(true,35,true,50,false,3,false,10,OfferRuleEngine.Mode.ALL);
        OfferRuleEngine.Config hourOr=full(true,35,true,50,false,3,false,10,OfferRuleEngine.Mode.ANY);
        TieredOfferRules mixed=new TieredOfferRules(
                new OfferRuleEngine.Config[]{price(25),hourAnd,hourOr},new boolean[]{true,true,true});
        eq(mixed.provenPriceFailure(2,40),false,"AND payout passes so not early rejected");
        eq(mixed.provenPriceFailure(2,30),true,"AND price fails: reject without hourly OCR");
        eq(mixed.provenPriceFailure(3,30),false,"OR price failure cannot decide if hourly may qualify");
        eq(mixed.evaluate(40,60,9000,2).outcome,OfferRuleEngine.Outcome.DECLINE,"AND fails hourly even when price passes");
        eq(mixed.evaluate(40,60,9000,3).outcome,OfferRuleEngine.Outcome.SHOW,"OR passes on price for triple");
        eq(mixed.evaluate(40,40,9000,2).outcome,OfferRuleEngine.Outcome.SHOW,"AND passes both with $60/hr");
        eq(mixed.evaluate(20,0,9000,2).outcome,OfferRuleEngine.Outcome.REVIEW,"missing hour data reviewed");
        OfferRuleEngine.Config maxDistance=full(true,25,false,50,false,3,true,5,OfferRuleEngine.Mode.ALL);
        TieredOfferRules shortOnly=new TieredOfferRules(
                new OfferRuleEngine.Config[]{maxDistance,price(35),price(50)},
                new boolean[]{true,true,true});
        eq(shortOnly.evaluate(30,22,4999,1).outcome,OfferRuleEngine.Outcome.SHOW,"distance within max qualifies");
        eq(shortOnly.evaluate(30,22,5000,1).outcome,OfferRuleEngine.Outcome.SHOW,"distance boundary inclusive");
        eq(shortOnly.evaluate(30,22,5001,1).outcome,OfferRuleEngine.Outcome.DECLINE,"max distance exceeded");
        eq(shortOnly.evaluate(30,22,0,1).outcome,OfferRuleEngine.Outcome.REVIEW,"unreadable distance not declined");
        OfferRuleEngine.Config kmRule=full(false,25,false,50,true,3,false,5,OfferRuleEngine.Mode.ANY);
        TieredOfferRules perKm=new TieredOfferRules(
                new OfferRuleEngine.Config[]{price(25),kmRule,price(50)},new boolean[]{true,true,true});
        eq(perKm.evaluate(30,20,9000,2).outcome,OfferRuleEngine.Outcome.SHOW,"dollars per kilometre met");
        eq(perKm.evaluate(20,20,9000,2).outcome,OfferRuleEngine.Outcome.DECLINE,"dollars per kilometre below min");
        yes(categories.summary().contains("1 delivery:"),"summary solo");
        yes(categories.summary().contains("2 deliveries:"),"summary double");
        yes(categories.summary().contains("3 deliveries:"),"summary triple");
        yes(categories.summary().contains("4+: manual review"),"summary 4+ fallback");
        System.out.println("TieredOfferRules: "+assertions+" assertions passed");
    }
}
