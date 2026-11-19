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
        public final String reason;
        Assessment(boolean valid, double payout, String reason) {
            this.valid=valid; this.payout=payout; this.reason=reason;
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
        // The X and border already establish that this is an offer; do not depend
        // on Uber's text-based Accept/Match control being present in the OCR.
        priceCandidates.add("Accept");
        OfferDetector.Result result = OfferDetector.detect(priceCandidates);
        if (!result.valid)
            return new Assessment(false, 0, "Card price: " + result.explanation);
        return new Assessment(true, result.payout, "Price and X verified in the same card");
    }
}
