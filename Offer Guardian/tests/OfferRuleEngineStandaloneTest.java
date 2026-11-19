package com.personal.offerguardian;

/** Run with plain Java 17 (no Android SDK). */
public final class OfferRuleEngineStandaloneTest {
    private static int checks=0;
    private static void test(boolean okay,String message){checks++;if(!okay)throw new AssertionError(message);}
    private static OfferRuleEngine.Config c(boolean price,boolean hour,boolean km, OfferRuleEngine.Mode mode) {
        return new OfferRuleEngine.Config(price,30,hour,40,km,3,mode);
    }
    private static void outcome(OfferRuleEngine.Config cfg,double pay,int minutes,int metres,OfferRuleEngine.Outcome want,String name) {
        OfferRuleEngine.Decision d=OfferRuleEngine.evaluate(cfg,pay,minutes,metres);
        test(d.outcome==want,name+": expected "+want+" got "+d.outcome+" :: "+d.reason);
    }
    public static void main(String[] args){
        OfferRuleEngine.Mode or=OfferRuleEngine.Mode.ANY, and=OfferRuleEngine.Mode.ALL;
        OfferRuleEngine.Outcome show=OfferRuleEngine.Outcome.SHOW;
        OfferRuleEngine.Outcome decline=OfferRuleEngine.Outcome.DECLINE;
        OfferRuleEngine.Outcome review=OfferRuleEngine.Outcome.REVIEW;
        outcome(c(true,false,false,or),30,0,0,show,"price inclusive");
        outcome(c(true,false,false,or),29.99,0,0,decline,"price below");
        outcome(c(false,true,false,or),20,30,0,show,"hour only equal 40");
        outcome(c(false,true,false,or),20,27,0,show,"hour only 44.44");
        outcome(c(false,true,false,or),20,31,0,decline,"hour below 40");
        outcome(c(false,false,true,or),12,0,4000,show,"km only 3.00");
        outcome(c(false,false,true,or),12,0,6000,decline,"km below 3");
        outcome(c(true,true,false,or),20,27,5000,show,"OR rescues $20 via rate");
        outcome(c(true,true,false,and),20,27,5000,decline,"AND requires payment too");
        outcome(c(true,true,false,or),35,60,20000,show,"OR via price");
        outcome(c(true,true,false,and),35,60,20000,decline,"AND fails hourly");
        outcome(c(true,true,true,or),12,30,3000,show,"OR rescues via km");
        outcome(c(true,true,true,and),40,60,10000,show,"AND all pass inclusively");
        outcome(c(true,true,true,and),40,61,10000,decline,"AND time fails");
        outcome(c(true,true,true,or),15,45,6000,decline,"OR none pass");
        outcome(c(true,true,true,or),20,0,0,review,"OR unknown and price fails");
        outcome(c(true,true,true,and),20,0,0,review,"AND missing rates no auto decline");
        outcome(c(true,true,true,or),31,0,0,show,"OR price qualifies despite unknown rates");
        outcome(c(true,true,true,and),31,0,0,review,"AND can't decide with missing rates");
        outcome(c(false,true,true,or),31,0,0,review,"OR none measurable");
        outcome(c(false,false,true,or),25,40,0,review,"km missing");
        outcome(c(true,false,false,or),0,30,1000,review,"zero payout invalid");
        outcome(c(false,false,false,or),30,30,1000,review,"no rules invalid");
        test(!new OfferRuleEngine.Config(true,Double.NaN,false,40,false,3,or).valid(),"NaN threshold invalid");
        test(!new OfferRuleEngine.Config(true,-2,false,40,false,3,or).valid(),"negative threshold invalid");
        test(!new OfferRuleEngine.Config(true,10001,false,40,false,3,or).valid(),"extreme threshold invalid");
        test(c(true,true,true,or).count()==3,"three enabled rules");
        test(c(false,false,true,or).count()==1,"one enabled rule");
        test(OfferRuleEngine.summary(c(true,true,true,or)).contains("OR"),"OR summary");
        test(OfferRuleEngine.summary(c(true,true,true,and)).contains("AND"),"AND summary");
        OfferRuleEngine.Decision rates=OfferRuleEngine.evaluate(c(true,true,true,or),19.03,43,20300);
        test(Math.abs(rates.hourly-26.55348)<.01,"hourly arithmetic from minutes");
        test(Math.abs(rates.perKm-.937438)<.01,"km arithmetic from metres");
        System.out.println("PASS OfferRuleEngine "+checks+" cases");
    }
}
