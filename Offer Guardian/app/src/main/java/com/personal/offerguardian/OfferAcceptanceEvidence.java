package com.personal.offerguardian;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Conservative second-stage confirmation. A tap or the disappearance of an
 * offer is not evidence that it was accepted. A confirmed active pickup screen
 * must mention the SAME merchant as the offer, and navigation/pickup language.
 * This deliberately leaves some genuine accepts UNKNOWN rather than inventing
 * outcomes. The Match button by itself is never confirmation of assignment.
 */
public final class OfferAcceptanceEvidence {
    private OfferAcceptanceEvidence() {}

    static String normalize(String s) {
        if(s==null)return "";
        String ascii=Normalizer.normalize(s,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","");
        return " "+ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ")
                .replaceAll("\\s+"," ").trim()+" ";
    }

    public static boolean confirmsActivePickup(String screenText,String merchant) {
        String s=normalize(screenText),m=normalize(merchant).trim();
        if(m.length()<5 || s.length()<12) return false;
        // Preserve both store identity and locality in most multi-word names.
        // A shorter merchant identifier is too weak to infer which job is active.
        if(!s.contains(" "+m+" "))return false;
        boolean active= contains(s,"heading to") || contains(s,"head to") ||
                contains(s,"navigate to") || contains(s,"navigation to") ||
                contains(s,"on your way to") || contains(s,"go to pickup") ||
                contains(s,"pick up order") || contains(s,"pickup details") ||
                contains(s,"confirm pickup") || contains(s,"arrived at pickup") ||
                contains(s,"start delivery");
        if(!active)return false;
        // These are offer-search / request states, not an assigned delivery.
        if(contains(s,"finding trips") || contains(s,"matching you") ||
                contains(s,"looking for orders"))return false;
        return true;
    }

    private static boolean contains(String screen,String phrase) {
        return screen.contains(" "+phrase+" ");
    }

    /** Requires two separate, consistent screenshots of the confirmed active job. */
    public static final class StableConfirmation {
        private int matches=0;
        public boolean observe(boolean confirmed) {
            matches=confirmed?Math.min(matches+1,2):0;
            return matches>=2;
        }
        public void reset() {matches=0;}
    }
}
