package com.personal.offerguardian;

import java.util.Arrays;
import java.util.List;

/** Real incident values supplied by driver 22 Nov 2026, with synthetic OCR boxes. */
public final class OfferV095DeclineRegressionStandaloneTest {
    private static int assertions=0;
    private static void ok(boolean pass,String message) {
        assertions++;
        if(!pass)throw new AssertionError(message);
    }
    private static OfferScreenEvaluator.Line l(String label,int x,int y) {
        return new OfferScreenEvaluator.Line(label,x,y,x+Math.min(440,Math.max(75,label.length()*9)),y+26);
    }
    private static List<OfferScreenEvaluator.Line> card(String amount,String category,
            String items,String duration,String merchant) {
        return merchant.isEmpty() ?
                Arrays.asList(l(category,60,718),l(amount,60,800),
                        l("Est. earnings for completed trip",60,880),l(items,60,940),
                        l(duration,60,995)) :
                Arrays.asList(l(category,60,718),l(amount,60,800),
                        l("Est. earnings for completed trip",60,880),l(items,60,940),
                        l(duration,60,995),l(merchant,60,1090),l("Sample destination",60,1180));
    }
    private static OfferScreenEvaluator.Assessment price(String s) {
        return OfferScreenEvaluator.assess(
                new VisualDeclineDetector.Match(true,578,752,.96,"X"),
                Arrays.asList(l(s,60,800),l("11 min (2.3 km) total",60,995)));
    }
    private static OfferDetailsParser.Details details(String duration,String merchant,int count) {
        return OfferDetailsParser.parse(752,card("$27.10",count>0?"Pack & Deliver":"Delivery",
                count>0?count+" items (47 units)":"",duration,merchant));
    }
    public static void main(String[] args) {
        // Screenshot actually shows "$5", not "$5.00". Old 0.9.4 rejected it before rules.
        OfferScreenEvaluator.Assessment five=price("$5");
        ok(five.valid,"$5 should be valid payout OCR: "+five.reason);
        ok(Math.abs(five.payout-5.0)<.001,"$5 payout value must be 5 exactly");
        ok(price("$9").valid,"Other unambiguous one-digit whole-dollar amounts valid");
        ok(price("$5.00").valid,"Full cents still valid");
        ok(price("$29.66").valid,"Normal payout still valid");
        ok(!price("$29").valid,"Two-digit truncated cents must still fail closed");
        ok(!price("$5.").valid,"Truncated decimal must fail closed");
        ok(!price("$2").valid,"Very low ambiguous payouts fail closed");
        OfferRuleEngine.Config previousRules=new OfferRuleEngine.Config(true,40,
                true,50,false,3,OfferRuleEngine.Mode.ANY);
        OfferDetailsParser.Details sushi=details("11 min (2.3 km) total","Hane Sushi Nundah",0);
        ok(sushi.minutes==11&&sushi.metres==2300,"$5 offer parses 11 min/2.3 km");
        ok(OfferExtractionGuard.check(sushi).valid,"$5 delivery metrics pass checks");
        OfferReadVerifier fullVerify=new OfferReadVerifier();
        ok(fullVerify.observe(5,sushi,578,751,1000).state==OfferReadVerifier.State.WAITING,
                "First $5 reading cannot act");
        ok(fullVerify.observe(5,sushi,579,752,1600).state==OfferReadVerifier.State.CONFIRMED,
                "Two $5/11 minute readings confirm");
        OfferRuleEngine.Decision fiveDecision=OfferRuleEngine.evaluate(previousRules,5,11,2300);
        ok(fiveDecision.outcome==OfferRuleEngine.Outcome.DECLINE,
                "$5/11m fails both $40 and $50/hr OR rules");
        ok(Math.abs(fiveDecision.hourly-27.27272727)<.001,"Actual hourly is $27.27");

        OfferDetailsParser.Details oneMinute=details("1 hr 1 min (6.8 km) total","Coles (Lutwyche)",37);
        OfferDetailsParser.Details sevenMinutes=details("1 hr 7 min (9.8 km) total","Coles (Kedron)",38);
        ok(oneMinute.minutes==61&&OfferExtractionGuard.check(oneMinute).valid,
                "1 hr 1 min must parse exactly 61 min");
        ok(sevenMinutes.minutes==67&&OfferExtractionGuard.check(sevenMinutes).valid,
                "1 hr 7 min must parse exactly 67 min");
        ok(OfferRuleEngine.evaluate(previousRules,27.10,61,6800).outcome==
                OfferRuleEngine.Outcome.DECLINE,"61 minute Coles fails both rules");
        ok(OfferRuleEngine.evaluate(previousRules,29.66,67,9800).outcome==
                OfferRuleEngine.Outcome.DECLINE,"67 minute Coles fails both rules");
        OfferReadVerifier fullHourVerify=new OfferReadVerifier();
        ok(fullHourVerify.observe(27.10,oneMinute,579,666,2000).state==OfferReadVerifier.State.WAITING,
                "First 61 minute read waits");
        ok(fullHourVerify.observe(27.10,oneMinute,579,666,2550).state==OfferReadVerifier.State.CONFIRMED,
                "Second 61 minute read confirms");

        OfferDetailsParser.Details hourOnly=details("1 hr (6.8 km) total","Coles (Lutwyche)",37);
        ok(hourOnly.parsedHours==1&&!hourOnly.minutesSeen,"1 hr without minutes is bound only");
        ok(!OfferExtractionGuard.check(hourOnly).valid,"Not an exact duration");
        ok(OfferSafeDecline.hasReliableHourBound(hourOnly),"Reliable one-hour lower bound");
        OfferRuleEngine.Decision bound=OfferSafeDecline.evaluate(previousRules,27.10,hourOnly);
        ok(bound.outcome==OfferRuleEngine.Outcome.DECLINE,"Provable lower bound should decline");
        ok(Double.isNaN(bound.hourly),"Do not invent hourly rate for incomplete OCR");
        OfferBoundVerifier bv=new OfferBoundVerifier();
        ok(!bv.observe(27.10,hourOnly,579,666,1000),"Hour-only frame 1 cannot decline");
        ok(bv.isPending(),"Hour-bound verification must queue next frame");
        ok(!bv.observe(29.66,hourOnly,579,666,1450),"Different payout resets confirmation");
        ok(!bv.observe(27.10,hourOnly,579,666,1850),"Must restart on payout correction");
        ok(bv.observe(27.10,hourOnly,578,666,2300),"Two NEW consistent lower bounds confirmed");
        ok(!bv.isPending(),"No trailing verification");

        OfferRuleEngine.Config lowHourly=new OfferRuleEngine.Config(true,40,true,20,false,3,OfferRuleEngine.Mode.ANY);
        ok(OfferSafeDecline.evaluate(lowHourly,27.10,hourOnly).outcome==OfferRuleEngine.Outcome.REVIEW,
                "Cannot decline when hourly could meet $20/hr rule");
        OfferRuleEngine.Config highPayment=new OfferRuleEngine.Config(true,25,true,50,false,3,OfferRuleEngine.Mode.ANY);
        ok(OfferSafeDecline.evaluate(highPayment,27.10,hourOnly).outcome==OfferRuleEngine.Outcome.REVIEW,
                "Cannot decline when known payment rule passes");
        OfferRuleEngine.Config perKmPass=new OfferRuleEngine.Config(true,40,true,50,true,3,OfferRuleEngine.Mode.ANY);
        ok(OfferSafeDecline.evaluate(perKmPass,27.10,hourOnly).outcome==OfferRuleEngine.Outcome.REVIEW,
                "Cannot decline when known per-kilometre rule passes");
        OfferRuleEngine.Config perKmFail=new OfferRuleEngine.Config(true,40,true,50,true,5,OfferRuleEngine.Mode.ANY);
        ok(OfferSafeDecline.evaluate(perKmFail,27.10,hourOnly).outcome==OfferRuleEngine.Outcome.DECLINE,
                "May decline if ALL selected OR rules mathematically fail");
        OfferDetailsParser.Details noHours=details("1 min (6.8 km) total","Coles (Lutwyche)",37);
        ok(!OfferSafeDecline.hasReliableHourBound(noHours),"Cannot invent missing hour");
        ok(OfferSafeDecline.evaluate(previousRules,27.10,noHours).outcome==OfferRuleEngine.Outcome.REVIEW,
                "Cannot falsely interpret one minute as an hour");
        OfferDetailsParser.Details noMerchant=details("1 hr (6.8 km) total","",37);
        ok(!OfferSafeDecline.hasReliableHourBound(noMerchant),"Unknown merchant blocks bounded decline");
        bv.observe(27.10,hourOnly,579,666,3000);
        ok(!bv.observe(27.10,hourOnly,579,666,7501),"Old first frame cannot confirm");
        OfferRetryBudget retry=new OfferRetryBudget();
        for(int i=0;i<5;i++)ok(retry.request(579,666,1000+i*450),
                "Fast retry budget should permit attempt "+i);
        ok(!retry.request(579,666,3600),"Fast retry must be bounded");
        ok(retry.request(579,666,15000),"Retry budget renews after cooldown");
        retry.reset();
        ok(retry.request(579,752,15200),"New offer X location gets fresh retry budget");
        System.out.println("PASS v0.9.5 incident and safety regressions: "+assertions+" assertions");
    }
}
