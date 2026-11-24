package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen geometry is part of the safety check. OCR payouts must be located inside
 * the SAME offer card as the matching gray X, not elsewhere on the map or screen.
 * This file is pure Java so the actual rule can be unit tested without Android.
 */
public final class OfferScreenEvaluator {
    private OfferScreenEvaluator() {}

    public static final class Line {
        public final String text;
        public final int left, top, right, bottom;
        public Line(String text, int left, int top, int right, int bottom) {
            this.text = text;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }
    public static final class Assessment {
        public final boolean valid;
        public final double payout;
        public final String reason, rawPayout;
        Assessment(boolean valid, double payout, String reason) {
            this(valid,payout,reason,"");
        }
        Assessment(boolean valid,double payout,String reason,String rawPayout) {
            this.valid=valid; this.payout=payout; this.reason=reason;
            this.rawPayout=rawPayout;
        }
    }

    /** Input positions normalised to VisualDeclineDetector.WIDTH/HEIGHT. */
    public static Assessment assess(VisualDeclineDetector.Match visual, List<Line> text) {
        if (visual == null || !visual.found || visual.confidence < .90)
            return new Assessment(false, 0, "Grey X / offer border not verified");
        if (text == null || text.isEmpty())
            return new Assessment(false, 0, "Screenshot OCR returned no text");
        List<String> priceCandidates = new ArrayList<>();
        for (Line line : text) {
            if (line.text == null) continue;
            int cy = (line.top + line.bottom) / 2;
            // Payout is the prominent large text directly beneath the card's header.
            // Restrict both horizontally and vertically to avoid trip-distance,
            // addresses, other overlays, historical payouts and unrelated text.
            if (line.left >= 34 && line.left < 350 && line.right <= 440 &&
                    cy >= visual.y + 27 && cy <= visual.y + 128) {
                priceCandidates.add(line.text);
            }
        }
        if (priceCandidates.isEmpty())
            return new Assessment(false, 0, "OCR did not find a price in the offer card");
        String originalReading=String.join(" | ",priceCandidates);
        // Uber also displays low whole-dollar offers as "$5" (not "$5.00").
        // Allow a single-digit whole-dollar payout ONLY when it occupies the
        // entire payout region. Keep rejecting "$29" as possibly clipped $29.xx.
        // Every accepted value still needs a second independent agreeing frame.
        boolean exactCents=java.util.regex.Pattern.compile(
                "(?i)\\$\\s*[0-9,]+[.,][0-9]{2}\\b")
                .matcher(originalReading).find();
        boolean smallWholeDollar=originalReading.trim().matches("(?i)(?:AUD\\s*)?\\$\\s*[3-9]");
        if(!exactCents && !smallWholeDollar)
            return new Assessment(false,0,"Payout missing complete cents or unambiguous single-digit dollar amount");
        // The X and border already establish that this is an offer; do not depend
        // on Uber's text-based Accept/Match control being present in the OCR.
        priceCandidates.add("Accept");
        OfferDetector.Result result = OfferDetector.detect(priceCandidates);
        if (!result.valid)
            return new Assessment(false, 0, "Card price: " + result.explanation);
        return new Assessment(true, result.payout, "Price and X verified in the same card",originalReading);
    }
    /**
     * When a navigation app owns the foreground, validate that the visible
     * content really belongs to an Uber delivery offer before allowing an X tap.
     * A visual match and one price alone are not enough to confirm an offer.
     */
    public static boolean hasOfferIdentity(VisualDeclineDetector.Match visual,List<Line> lines) {
        if(visual==null||!visual.found||lines==null)return false;
        boolean delivery=false,earnings=false;
        for(Line line:lines) {
            if(line==null||line.text==null||line.left<30||line.right>666)continue;
            int cy=(line.top+line.bottom)/2;
            if(cy<visual.y-72||cy>visual.y+275)continue;
            String s=line.text.toLowerCase(java.util.Locale.ROOT);
            if(s.contains("delivery")||s.contains("pack & deliver")||s.contains("pack and deliver"))
                delivery=true;
            if((s.contains("earn")&&s.contains("trip"))||s.contains("est. earning")||
                    s.contains("completed trip"))earnings=true;
        }
        return delivery&&earnings;
    }

}
