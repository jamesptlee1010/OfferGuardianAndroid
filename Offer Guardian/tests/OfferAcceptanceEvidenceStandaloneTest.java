package com.personal.offerguardian;

public final class OfferAcceptanceEvidenceStandaloneTest {
    private static int count=0;
    private static void eq(boolean value,boolean expected,String name) {
        if(value!=expected)throw new AssertionError(name+" expected "+expected+" got "+value);
        count++;
    }
    public static void main(String[] args) {
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Heading to Coles (Chermside)\n Navigate", "Coles (Chermside)"),true,"heading to merchant");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Navigate to Sushi Hanamaru (531 Sandgate Rd)","Sushi Hanamaru (531 Sandgate Rd)"),true,"navigation exact merchant");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Pickup details\n KFC (Clayfield)","KFC (Clayfield)"),true,"pickup details with merchant");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Offer: $35.00 KFC (Clayfield) Accept", "KFC (Clayfield)"),false,"offer is not accepted");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Heading to Subway Kedron", "KFC (Clayfield)"),false,"wrong merchant");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Matching you with Coles Chermside...", "Coles Chermside"),false,"match pending");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Head to restaurant", ""),false,"unknown merchant");
        eq(OfferAcceptanceEvidence.confirmsActivePickup(
            "Heading to Cole's (Chermside)!", "Coles (Chermside)"),false,"ambiguous merchant spelling left unknown");
        OfferAcceptanceEvidence.StableConfirmation s=new OfferAcceptanceEvidence.StableConfirmation();
        eq(s.observe(true),false,"first matching frame insufficient");
        eq(s.observe(false),false,"mismatch resets");
        eq(s.observe(true),false,"first new frame insufficient");
        eq(s.observe(true),true,"second matching frame confirms");
        s.reset();
        eq(s.observe(true),false,"reset after new offer");
        System.out.println("Accepted evidence checks passed: "+count);
    }
}
