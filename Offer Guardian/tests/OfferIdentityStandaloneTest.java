package com.personal.offerguardian;
import java.util.ArrayList;
import java.util.List;

public final class OfferIdentityStandaloneTest {
    static OfferScreenEvaluator.Line line(String s,int y) {
        return new OfferScreenEvaluator.Line(s,60,y,580,y+30);
    }
    public static void main(String[] args) {
        VisualDeclineDetector.Match v=new VisualDeclineDetector.Match(true,579,659,.99,
                "X found",VisualDeclineDetector.Theme.LIGHT,585);
        List<OfferScreenEvaluator.Line> ok=new ArrayList<>();
        ok.add(line("Delivery (2)",645));
        ok.add(line("$20.00",713));
        ok.add(line("Est. earnings for completed trip",814));
        if(!OfferScreenEvaluator.hasOfferIdentity(v,ok))throw new AssertionError("Real offer words should verify");
        List<OfferScreenEvaluator.Line> map=new ArrayList<>();
        map.add(line("70 m How Rd",600));map.add(line("$20.00",713));
        if(OfferScreenEvaluator.hasOfferIdentity(v,map))throw new AssertionError("Navigation alone cannot verify an Uber offer");
        System.out.println("Waze OCR offer identity tests PASS");
    }
}
