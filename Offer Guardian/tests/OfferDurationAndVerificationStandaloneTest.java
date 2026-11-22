package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Regression for the 22 Nov Coles Kedron 1 hr 7 min => 67 min failure. */
public final class OfferDurationAndVerificationStandaloneTest {
    private static int count=0;
    private static void ok(boolean value,String message) {
        count++;if(!value)throw new AssertionError(message);
    }
    private static OfferScreenEvaluator.Line line(String text,int x,int y) {
        return new OfferScreenEvaluator.Line(text,x,y,x+Math.min(480,Math.max(90,text.length()*12)),y+30);
    }
    private static OfferDetailsParser.Details parse(String type,String metric,String items) {
        return OfferDetailsParser.parse(752,Arrays.asList(
                line(type,62,725),line("$29.66",62,815),line("Est. earnings for completed trip",62,885),
                line(items,62,925),line(metric,62,985),
                line("Coles (Kedron)",62,1100),line("Kate Street & Mellor Street",62,1190),
                line("Kedron",62,1240)));
    }
    public static void main(String[] args) {
        OfferDetailsParser.Details full=parse("Pack & Deliver (2)",
                "1 hr 7 min (9.8 km) total","38 items (46 units)");
        ok(full.minutes==67,"1 hr 7 min must be 67: "+full.minutes);
        ok(full.metres==9800,"9.8 km should be 9800 m");
        ok(full.parsedHours==1&&full.parsedMinutes==7,"Audit hour and minute components");
        ok(full.itemCount==38,"38 items detected");
        ok(full.hoursSeen&&full.minutesSeen,"Both units detected");
        ok(full.rawDuration.contains("1 hr 7 min"),"Raw original time retained");
        ok(OfferExtractionGuard.check(full).valid,"67 min example is plausible");
        ok("A$26.56/hr".equals(OfferRate.dollarsPerHour(2966,full.minutes)),
                "Screenshot should show 26.56 not 254.23");
        ok("A$3.03/km".equals(OfferRate.dollarsPerKm(2966,full.metres)),"Keep correct per-km");
        OfferDetailsParser.Details shortOcr=parse("Pack & Deliver (2)",
                "7 min (9.8 km) total","38 items (46 units)");
        ok(shortOcr.minutes==7,"OCR truncated duration should be represented faithfully");
        ok(!OfferExtractionGuard.check(shortOcr).valid,"Implausible 7min must be REVIEW");
        OfferDetailsParser.Details fastCar=parse("Delivery","7 min (9.8 km) total","Delivery details");
        ok(!OfferExtractionGuard.check(fastCar).valid,"84 km/h urban duration is suspect");
        OfferDetailsParser.Details hourOnly=parse("Delivery","1 hr (9.8 km) total","Delivery details");
        ok(!OfferExtractionGuard.check(hourOnly).valid,"Missing minute component is uncertain");
        OfferDetailsParser.Details twoHour=parse("Delivery","2 hours 15 minutes (35.1 km) total","Delivery details");
        ok(twoHour.minutes==135,"2h15 is 135 minutes");
        ok(OfferExtractionGuard.check(twoHour).valid,"Ordinary two-hour delivery valid");
        OfferDetailsParser.Details normal=parse("Delivery","45 min (9.8 km) total","Delivery details");
        ok(normal.minutes==45&&OfferExtractionGuard.check(normal).valid,"minute-only offer still works");
        OfferDetailsParser.Details split=OfferDetailsParser.parse(752,Arrays.asList(
                line("Pack & Deliver (2)",62,720),line("38 items (46 units)",62,920),
                line("1 hr",62,973),line("7 min",160,977),line("9.8 km total",300,980),
                line("Coles (Kedron)",62,1100),line("Kate Street",62,1190)));
        ok(split.minutes==67&&split.metres==9800,"Split nearby OCR fields must combine");
        OfferScreenEvaluator.Assessment clipped=OfferScreenEvaluator.assess(
                new VisualDeclineDetector.Match(true,579,752,.97,"Found"),
                Arrays.asList(line("$29",62,790),line("Match",62,1350)));
        ok(!clipped.valid,"Payout with truncated decimal fraction must be rejected");
        OfferReadVerifier read=new OfferReadVerifier();
        OfferReadVerifier.Result first=read.observe(29.66,full,578,670,1000);
        ok(first.state==OfferReadVerifier.State.WAITING,"First reading must never act");
        ok(read.isPending(),"Second reading scheduled");
        OfferReadVerifier.Result second=read.observe(29.66,full,579,671,1580);
        ok(second.state==OfferReadVerifier.State.CONFIRMED,"Second same read confirms");
        ok(!read.isPending(),"No trailing verifier pending");
        read.reset();
        read.observe(11.02,normal,579,670,2000);
        ok(read.observe(41.02,normal,579,670,2600).state==OfferReadVerifier.State.CHANGED,
                "Payout disagreement should not be accepted");
        ok(read.observe(41.02,normal,579,670,3100).state==OfferReadVerifier.State.CONFIRMED,
                "Two new consistent readings recover");
        OfferDetailsParser.Details otherMerchant=OfferDetailsParser.parse(752,Arrays.asList(
                line("Delivery",62,725),line("45 min (9.8 km) total",62,985),
                line("Different Merchant",62,1100),line("Some Street",62,1190)));
        read.observe(20,normal,579,670,3500);
        ok(read.observe(20,otherMerchant,579,670,3700).state==OfferReadVerifier.State.CHANGED,
                "Different merchant must restart even at same payout");
        ok(read.observe(29.66,shortOcr,579,670,4000).state==OfferReadVerifier.State.UNVERIFIED,
                "Suspicious truncated time blocked without tap");
        ok(read.observe(1.02,normal,579,670,4500).state==OfferReadVerifier.State.UNVERIFIED,
                "Partial $1.02 not actionable");
        read.observe(20,normal,579,670,5000);
        ok(read.observe(20,normal,579,670,10000).state==OfferReadVerifier.State.WAITING,
                "Old first screenshot cannot verify a new one");
        System.out.println("PASS duration/OCR/two-frame regression: "+count+" assertions");
    }
}
