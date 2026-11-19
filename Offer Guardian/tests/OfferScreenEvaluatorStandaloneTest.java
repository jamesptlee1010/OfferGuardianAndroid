package com.personal.offerguardian;
import java.util.Arrays;
import java.util.Collections;

public class OfferScreenEvaluatorStandaloneTest {
    static VisualDeclineDetector.Match visual(int y) {
        return new VisualDeclineDetector.Match(true,579,y,.97,"Found");
    }
    static void yes(int y,double payout) {
        String amount = String.format(java.util.Locale.US,"$%.2f",payout);
        OfferScreenEvaluator.Assessment a = OfferScreenEvaluator.assess(visual(y),
            Arrays.asList(new OfferScreenEvaluator.Line(amount,60,y+33,311,y+104),
                new OfferScreenEvaluator.Line("20.3 km",100,y+250,300,y+285),
                new OfferScreenEvaluator.Line("Match",300,y+581,380,y+608)));
        if (!a.valid || Math.abs(a.payout-payout)>.0001)
            throw new AssertionError("Failed " + amount + ": " + a.reason);
    }
    static void no(VisualDeclineDetector.Match match,OfferScreenEvaluator.Line... lines) {
        OfferScreenEvaluator.Assessment a=OfferScreenEvaluator.assess(match,Arrays.asList(lines));
        if(a.valid) throw new AssertionError("Unexpectedly accepted screenshot: "+a.payout);
    }
    public static void main(String[] args) {
        yes(752,11.47); yes(752,12.41); yes(666,30.59);
        yes(752,13.18); yes(737,7.04); yes(651,18.11);
        no(visual(752),new OfferScreenEvaluator.Line("$18.11",60,1100,320,1190));
        no(new VisualDeclineDetector.Match(false,-1,-1,0,"not found"),
            new OfferScreenEvaluator.Line("$13.18",60,789,310,858));
        no(visual(752),new OfferScreenEvaluator.Line("$12.41",60,785,300,855),
                new OfferScreenEvaluator.Line("$28.11",60,794,280,860));
        no(visual(752),new OfferScreenEvaluator.Line("$12.41",400,789,650,857));
        no(visual(752),new OfferScreenEvaluator.Line("No readable dollars",60,786,318,857));
        System.out.println("Offer screen geometry: 11 tests PASSED");
    }
}
