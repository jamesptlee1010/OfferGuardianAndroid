package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test synthetic ML Kit line boxes patterned after real Uber offer layouts. */
public final class OfferDeliveryCountStandaloneTest {
    private static int checks;
    private static void ok(boolean condition,String reason) {
        checks++;if(!condition)throw new AssertionError(reason);
    }
    private static OfferScreenEvaluator.Line line(String text,int x,int y) {
        return new OfferScreenEvaluator.Line(text,x,y,x+Math.min(270,text.length()*13+12),y+28);
    }
    private static List<OfferScreenEvaluator.Line> card(String heading,String minutes) {
        return new ArrayList<>(Arrays.asList(
                line(heading,60,610),line("$36.03",60,687),
                line("21 items (27 units)",60,787),
                line(minutes,60,920),line("Coles (Chermside)",130,1005),
                line("McMaster Street, Nundah",130,1090)));
    }
    private static OfferDetailsParser.Details parsed(String title,String duration) {
        return OfferDetailsParser.parse(675,card(title,duration));
    }
    private static void count(String title,int want) {
        OfferDeliveryCount.Reading r=OfferDeliveryCount.detect(675,card(title,"1 hr 5 min (14.4 km) total"));
        ok(r.count==want,"Heading '"+title+"' expected "+want+", got "+r.count+" / "+r.reason);
    }
    private static OfferRuleEngine.Config rules(int min,OfferRuleEngine.Mode mode) {
        return new OfferRuleEngine.Config(true,40,true,50,false,3,mode,true,min);
    }
    private static void verdict(OfferRuleEngine.Config cfg,double pay,int mins,int metres,int n,
                                OfferRuleEngine.Outcome expected) {
        OfferRuleEngine.Decision d=OfferRuleEngine.evaluate(cfg,pay,mins,metres,n);
        ok(d.outcome==expected,"count="+n+", min="+cfg.minDeliveries+", mode="+
                cfg.mode+", outcome="+d.outcome+", reason="+d.reason);
    }
    public static void main(String[] args) {
        count("Delivery",1);
        count("Pack & Deliver",1);
        count("Delivery (2)",2);
        count("Delivery(3)",3);
        count("Delivery ( 4 )",4);
        count("Pack & Deliver (2)",2);
        count("Pack & Deliver (3)",3);
        count("pack and deliver (4)",4);
        count("Delivery (12)",12);
        count("Delivery (",0);
        count("Delivery (3",0);
        count("Delivery 3",0);
        count("Delivery (99)",0);
        count("Delivery ()",0);
        List<OfferScreenEvaluator.Line> split=card("Delivery", "43 min (14.7 km) total");
        split.add(line("(2)",180,611));
        ok(OfferDeliveryCount.detect(675,split).count==2,"Count split from heading");
        List<OfferScreenEvaluator.Line> split3=card("Pack &", "56 min (6.6 km) total");
        split3.add(line("Deliver",157,611));split3.add(line("(3)",260,611));
        ok(OfferDeliveryCount.detect(675,split3).count==3,"Split Pack & Deliver (3)");
        List<OfferScreenEvaluator.Line> missing=card("$19.22", "43 min (14.7 km) total");
        ok(OfferDeliveryCount.detect(675,missing).count==0,"No heading is unknown, not one");
        List<OfferScreenEvaluator.Line> far=card("$19.22", "43 min (14.7 km) total");
        far.add(line("Delivery (3)",100,1120));
        ok(OfferDeliveryCount.detect(675,far).count==0,"Merchant/address must not supply count");
        OfferDetailsParser.Details two=parsed("Delivery (2)","43 min (14.7 km) total");
        ok(two.deliveryCount==2,"Full parser retains two deliveries");
        ok(two.minutes==43,"Full parser preserves time");
        OfferDetailsParser.Details three=parsed("Pack & Deliver (3)","1 hr 5 min (14.4 km) total");
        ok(three.deliveryCount==3,"Full parser retains three deliveries");
        ok(three.minutes==65,"Full parser converts hours/minutes");
        OfferDetailsParser.Details single=parsed("Delivery","27 min (8.2 km) total");
        ok(single.deliveryCount==1,"Bare Delivery equals one");
        // The mandatory delivery requirement must NOT be bypassed by ANY/OR.
        verdict(rules(3,OfferRuleEngine.Mode.ANY),250,20,4000,2,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(3,OfferRuleEngine.Mode.ALL),250,20,4000,2,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),19.22,43,14700,1,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),19.22,43,14700,2,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),60,27,3000,2,OfferRuleEngine.Outcome.SHOW);
        verdict(rules(3,OfferRuleEngine.Mode.ANY),60,27,3000,3,OfferRuleEngine.Outcome.SHOW);
        verdict(rules(4,OfferRuleEngine.Mode.ANY),60,27,3000,3,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(4,OfferRuleEngine.Mode.ANY),60,27,3000,5,OfferRuleEngine.Outcome.SHOW);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),60,27,3000,0,OfferRuleEngine.Outcome.REVIEW);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),60,0,0,1,OfferRuleEngine.Outcome.DECLINE);
        verdict(rules(2,OfferRuleEngine.Mode.ANY),60,0,0,2,OfferRuleEngine.Outcome.SHOW);
        verdict(new OfferRuleEngine.Config(true,40,false,50,false,3,
                OfferRuleEngine.Mode.ANY,false,3),60,27,3000,1,OfferRuleEngine.Outcome.SHOW);
        OfferRuleEngine.Config countOnly=new OfferRuleEngine.Config(false,40,false,50,false,3,
                OfferRuleEngine.Mode.ANY,true,2);
        ok(countOnly.valid(),"Mandatory-only config is valid");
        verdict(countOnly,10,0,0,1,OfferRuleEngine.Outcome.DECLINE);
        verdict(countOnly,10,0,0,2,OfferRuleEngine.Outcome.SHOW);
        ok(OfferRuleEngine.summary(rules(3,OfferRuleEngine.Mode.ANY)).contains("REQUIRED"),
                "Rule summary explains mandatory gate");
        ok(!new OfferRuleEngine.Config(true,40,false,50,false,3,
                OfferRuleEngine.Mode.ANY,true,5).valid(),"Minimum must be between 1 and 4");
        // Two *independent* frames are necessary. Changing the count resets pending.
        OfferCountVerifier countVerifier=new OfferCountVerifier();
        ok(!countVerifier.observe(19.22,single,579,622,1000),"First single read cannot decline");
        ok(countVerifier.isPending(),"First count read pending");
        ok(!countVerifier.observe(19.22,two,579,622,1500),"Changed count cannot confirm");
        ok(countVerifier.observe(19.22,two,579,622,2000),"Two matching double reads confirm");
        ok(!countVerifier.observe(19.22,two,579,622,2500),"Confirmation cannot replay");
        ok(countVerifier.observe(19.22,two,579,622,3000),"Second matching reading confirms again");
        ok(!countVerifier.observe(19.22,two,579,622,8000),"Stale count reading cannot confirm");
        ok(countVerifier.observe(19.22,two,579,622,8300),"Fresh second reading confirms");
        OfferReadVerifier fullVerifier=new OfferReadVerifier();
        ok(fullVerifier.observe(19.22,two,579,622,10000,true).state==OfferReadVerifier.State.WAITING,
                "First full verification pending");
        ok(fullVerifier.observe(19.22,three,579,622,10500,true).state!=OfferReadVerifier.State.CONFIRMED,
                "Changing count blocks full OCR confirmation");
        ok(fullVerifier.observe(19.22,three,579,622,11000,true).state==OfferReadVerifier.State.CONFIRMED,
                "Same three-delivery count confirms after two frames");
        System.out.println("PASS OfferDeliveryCount "+checks+" assertions");
    }
}
