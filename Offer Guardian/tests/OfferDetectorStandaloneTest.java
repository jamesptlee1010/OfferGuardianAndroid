package com.personal.offerguardian;

import java.util.Arrays;

public class OfferDetectorStandaloneTest {
    static void yes(double expected, String... labels) {
        OfferDetector.Result result = OfferDetector.detect(Arrays.asList(labels));
        if (!result.valid || Math.abs(result.payout - expected) > 0.001)
            throw new AssertionError("Expected " + expected + ", got " + result.explanation);
    }
    static void no(String... labels) {
        OfferDetector.Result result = OfferDetector.detect(Arrays.asList(labels));
        if (result.valid) throw new AssertionError("Expected reject, got " + result.payout);
    }
    public static void main(String[] args) {
        yes(12.50, "Delivery", "$12.50", "Accept");
        yes(30, "A$30.00", "Accept order");
        yes(36.75, "AUD $36.75", "Swipe to accept");
        yes(8, "A$8", "Accept");
        yes(13.4, "A$13,40", "Accept delivery");
        no("$12.50", "Today's earnings");
        no("$12.50", "$31.00", "Accept");
        no("15 min", "9.2 km", "Accept");
        no("$0.50", "Accept");
        no("$12.50", "Decline");
        no("$12.50", "$1,234.56", "Accept");
        no("$5000.00", "Accept");
        yes(50.5, "A$50.50", "Accept");
        no("$3.2.5", "Accept");
        yes(11.47, "Delivery (2)", "$11.47", "Match");
        yes(12.41, "Delivery (2)", "$12.41", "Match");
        yes(30.59, "Pack & Deliver (3)", "$30.59", "Match");
        yes(19.03, "Delivery (2)", "$19.03", "Match");
        no("$9.00", "Matching nearby requests");
        no("$11.47", "Matched");
        System.out.println("OfferDetector: 20 tests passed");
    }
}
