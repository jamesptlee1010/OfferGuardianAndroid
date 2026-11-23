package com.personal.offerguardian;

import java.util.Arrays;
import java.util.List;

/** v0.9.9: no distance dependency in the live price/hourly rule path. */
public final class OfferV099SimplifiedStandaloneTest {
    private static int checks;
    private static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
    private static OfferScreenEvaluator.Line l(String text,int y){
        return new OfferScreenEvaluator.Line(text,62,y,Math.min(622,62+Math.max(70,text.length()*8)),y+25);
    }
    private static OfferDetailsParser.Details parsed(String time){
        List<OfferScreenEvaluator.Line> lines=Arrays.asList(
            l("Delivery (2)",720),l("$27.10",800),l("Est. earnings for completed trip",875),
            l(time,994),l("Hane Sushi Nundah",1100),l("Clayfield Rd",1175));
        return OfferDetailsParser.parse(752,lines);
    }
    public static void main(String[] args){
        OfferRuleEngine.Config onlyPrice=new OfferRuleEngine.Config(true,35,false,40,false,0,OfferRuleEngine.Mode.ANY);
        OfferRuleEngine.Config hourly=new OfferRuleEngine.Config(true,25,true,50,false,0,OfferRuleEngine.Mode.ALL);
        OfferDetailsParser.Details missingKm=parsed("1 hr 7 min total");
        ok(missingKm.minutes==67,"67 min even without km label");
        ok(missingKm.metres==0,"distance truly missing; no invented km");
        ok(missingKm.pickup.contains("Hane"),"merchant found without km anchor");
        ok(OfferExtractionGuard.check(missingKm).valid,"valid time without km");
        ok(OfferRuleEngine.evaluate(onlyPrice,27.10,0,0,2).outcome==OfferRuleEngine.Outcome.DECLINE,"price-only decline without time or km");
        ok(OfferRuleEngine.evaluate(hourly,27.10,67,0,2).outcome==OfferRuleEngine.Outcome.DECLINE,"hourly decline no km");
        OfferReadVerifier verifier=new OfferReadVerifier();
        ok(verifier.observe(27.10,missingKm,578,752,1000,true).state==OfferReadVerifier.State.WAITING,"first reading waits");
        ok(verifier.observe(27.10,missingKm,578,752,1450,true).state==OfferReadVerifier.State.CONFIRMED,"second confirms no km");
        OfferCountVerifier cv=new OfferCountVerifier();
        ok(!cv.observe(27.10,missingKm,578,752,1000),"price-only frame one waits");
        ok(cv.observe(27.10,missingKm,578,752,1400),"payout/count verified on frame two");
        ok(cv.hasConfirmedExactDuration(),"exact duration also independently confirmed; preserve in History");
        cv.reset();
        ok(!cv.observe(27.10,missingKm,578,752,2000),"new price-only read");
        OfferDetailsParser.Details changing=parsed("1 hr 8 min total");
        ok(cv.observe(27.10,changing,578,752,2500),"payout/count still confirmed despite unstable duration");
        ok(!cv.hasConfirmedExactDuration(),"unstable minutes must remain Unknown in History");
        OfferReadVerifier noisyKm=new OfferReadVerifier();
        OfferDetailsParser.Details kmA=parsed("1 hr 7 min (2.3 km) total");
        OfferDetailsParser.Details kmB=parsed("1 hr 7 min (2.4 km) total");
        ok(noisyKm.observe(27.10,kmA,578,752,3000,true).state==OfferReadVerifier.State.WAITING,
                "first frame with distance");
        ok(noisyKm.observe(27.10,kmB,578,752,3400,true).state==OfferReadVerifier.State.CONFIRMED,
                "distance fluctuations cannot block a valid hourly decision");
        ok(noisyKm.confirmedDistanceMetres()==0,"unconfirmed optional distance excluded from History rates");
        OfferReadVerifier sameKm=new OfferReadVerifier();
        sameKm.observe(27.10,kmA,578,752,3600,true);
        ok(sameKm.observe(27.10,kmA,578,752,4000,true).state==OfferReadVerifier.State.CONFIRMED,
                "stable optional kilometres verified");
        ok(sameKm.confirmedDistanceMetres()==2300,"stable distance retained for History");
        OfferDetailsParser.Details unknownTime=parsed("total");
        ok(unknownTime.minutes==0,"no invented time");
        ok(!OfferExtractionGuard.check(unknownTime).valid,"no hourly decision on unknown duration");
        ok(OfferRuleEngine.evaluate(hourly,27.10,0,0,2).outcome==OfferRuleEngine.Outcome.REVIEW,"hourly missing reviewed");
        System.out.println("v0.9.9 live decision and duration preservation: "+checks+" assertions passed");
    }
}
