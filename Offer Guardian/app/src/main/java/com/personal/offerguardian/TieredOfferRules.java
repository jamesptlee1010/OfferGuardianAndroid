package com.personal.offerguardian;

/** Independent, locally configured rules for exactly 1, 2 and 3 deliveries.
 * An unknown count, a disabled category, and 4+ deliveries are MANUAL REVIEW,
 * never silently reassigned to a different category. */
public final class TieredOfferRules {
    public static final int CATEGORIES=3;
    private final OfferRuleEngine.Config[] rules;
    private final boolean[] enabled;

    public TieredOfferRules(OfferRuleEngine.Config[] rules,boolean[] enabled) {
        if(rules==null || enabled==null || rules.length!=CATEGORIES || enabled.length!=CATEGORIES)
            throw new IllegalArgumentException("Provide 3 independent categories");
        this.rules=rules.clone();this.enabled=enabled.clone();
    }
    public boolean valid() {
        int active=0;
        for(int i=0;i<CATEGORIES;i++) {
            if(enabled[i])active++;
            if(enabled[i] && (rules[i]==null || !rules[i].valid() || rules[i].deliveriesOn))return false;
        }
        return active>0;
    }
    public boolean enabled(int count) {return count>=1&&count<=3&&enabled[count-1];}
    public OfferRuleEngine.Config configFor(int count) {
        return enabled(count) ? rules[count-1] : null;
    }
    /** Price-based refusal proven without needing duration/distance OCR.
     * In AND mode a failed price criterion alone is decisive. In OR mode it
     * is decisive ONLY when price is the sole active criterion. The caller
     * still requires two matching payout/count/merchant/X frames before tapping. */
    public boolean provenPriceFailure(int count,double payout) {
        OfferRuleEngine.Config c=configFor(count);
        return c!=null && c.valid() && c.priceOn && Double.isFinite(payout) &&
                payout>0 && payout<c.minPrice &&
                (c.mode==OfferRuleEngine.Mode.ALL || c.count()==1);
    }

    public OfferRuleEngine.Decision evaluate(double payout,int minutes,int metres,int count) {
        if(count<=0)return review("Delivery count unreadable; awaiting two matching images",payout);
        if(count>3)return review("4+ deliveries: manual review (no category configured)",payout);
        if(!enabled(count))return review(count+"-delivery rules disabled: manual review",payout);
        OfferRuleEngine.Config config=configFor(count);
        if(config==null || !config.valid())return review("Invalid "+count+"-delivery rules: manual review",payout);
        OfferRuleEngine.Decision result=OfferRuleEngine.evaluate(config,payout,minutes,metres,count);
        return new OfferRuleEngine.Decision(result.outcome,
                count+"-delivery tier: "+result.reason,result.payout,result.hourly,result.perKm);
    }
    private static OfferRuleEngine.Decision review(String why,double payout) {
        return new OfferRuleEngine.Decision(OfferRuleEngine.Outcome.REVIEW,why,payout,Double.NaN,Double.NaN);
    }
    public String summary() {
        StringBuilder b=new StringBuilder("Delivery-specific rules: ");
        for(int count=1;count<=3;count++) {
            if(count>1)b.append(" | ");
            b.append(count).append(count==1?" delivery: ":" deliveries: ");
            b.append(enabled(count)?OfferRuleEngine.summary(configFor(count)):"Manual review (disabled)");
        }
        b.append(" | 4+: manual review");
        return b.toString();
    }
}
