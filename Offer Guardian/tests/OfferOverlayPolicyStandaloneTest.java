package com.personal.offerguardian;

public final class OfferOverlayPolicyStandaloneTest {
    private static int tests=0;
    private static void check(boolean actual,boolean expected,String note) {
        tests++;
        if(actual!=expected)throw new AssertionError(note+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        OfferRuleEngine.Config c=new OfferRuleEngine.Config(true,30,true,40,true,3,OfferRuleEngine.Mode.ANY);
        OfferRuleEngine.Decision low=OfferRuleEngine.evaluate(c,12,30,6300);
        OfferRuleEngine.Decision qualifiesHourly=OfferRuleEngine.evaluate(c,20,27,5000);
        OfferRuleEngine.Decision qualifiesPrice=OfferRuleEngine.evaluate(c,35,60,20000);
        OfferRuleEngine.Decision missing=OfferRuleEngine.evaluate(c,12,0,0);
        check(low.outcome==OfferRuleEngine.Outcome.DECLINE,true,"low offer fails");
        check(qualifiesHourly.outcome==OfferRuleEngine.Outcome.SHOW,true,"hourly qualifies");
        check(qualifiesPrice.outcome==OfferRuleEngine.Outcome.SHOW,true,"price qualifies");
        check(missing.outcome==OfferRuleEngine.Outcome.REVIEW,true,"missing metrics requires review");
        check(OfferOverlayPolicy.shouldShow(qualifiesHourly,true,true,true,true),true,"qualified hourly shows");
        check(OfferOverlayPolicy.shouldShow(qualifiesPrice,true,true,true,true),true,"qualified price shows");
        check(OfferOverlayPolicy.shouldShow(low,true,true,true,true),false,"low hidden");
        check(OfferOverlayPolicy.shouldShow(missing,true,true,true,true),false,"uncertain hidden");
        check(OfferOverlayPolicy.shouldShow(qualifiesHourly,false,true,true,true),false,"switch off");
        check(OfferOverlayPolicy.shouldShow(qualifiesHourly,true,false,true,true),false,"monitor off");
        check(OfferOverlayPolicy.shouldShow(qualifiesHourly,true,true,false,true),false,"Uber background");
        check(OfferOverlayPolicy.shouldShow(qualifiesHourly,true,true,true,false),false,"offer card absent");
        OfferRuleEngine.Config andRules=new OfferRuleEngine.Config(true,30,true,40,true,3,OfferRuleEngine.Mode.ALL);
        check(OfferOverlayPolicy.shouldShow(OfferRuleEngine.evaluate(andRules,20,27,5000),true,true,true,true),false,"AND requires all");
        check(OfferOverlayPolicy.shouldShow(OfferRuleEngine.evaluate(andRules,35,30,5000),true,true,true,true),true,"AND all pass");
        System.out.println("Overlay policy checks passed: "+tests);
    }
}
