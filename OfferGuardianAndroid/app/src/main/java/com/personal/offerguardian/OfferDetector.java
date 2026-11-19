package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java, conservative parsing; no guessed payouts and no parsing of kilometre figures. */
public final class OfferDetector {
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?<![\\w$])(?:AUD\\s*|A\\s*)?\\$\\s*" +
            "((?:\\d{1,3}(?:,\\d{3})+|\\d{1,4})(?:\\.\\d{1,2})?|\\d{1,4},\\d{2})(?![\\d.,])");
    private static final Pattern ACCEPT = Pattern.compile(
            "(?i)\\baccept\\b|\\bswipe to accept\\b");
    private OfferDetector() {}

    public static final class Result {
        public final boolean valid;
        public final double payout;
        public final String explanation;
        Result(boolean valid, double payout, String explanation) {
            this.valid = valid;
            this.payout = payout;
            this.explanation = explanation;
        }
    }

    /** Each input is the visible text or content description of one accessibility node. */
    public static Result detect(List<String> visibleLabels) {
        if (visibleLabels == null || visibleLabels.isEmpty()) {
            return new Result(false, 0.0, "Nothing readable on screen");
        }
        Set<Integer> cents = new HashSet<>();
        boolean accept = false;
        List<String> labels = new ArrayList<>();
        for (String raw : visibleLabels) {
            if (raw == null) continue;
            String label = raw.trim();
            if (label.isEmpty()) continue;
            labels.add(label);
            if (ACCEPT.matcher(label).find()) accept = true;
            Matcher m = MONEY.matcher(label);
            while (m.find()) {
                String numeric = m.group(1);
                // Comma can denote thousands (1,234.56) or cents (13,40).
                if (numeric.matches("\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?"))
                    numeric = numeric.replace(",", "");
                else numeric = numeric.replace(',', '.');
                try {
                    double amount = Double.parseDouble(numeric);
                    if (amount >= 0 && amount <= 100000) {
                        cents.add((int) Math.round(amount * 100.0));
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        if (!accept) return new Result(false, 0, "Accept control not visible; no action");
        if (cents.isEmpty()) return new Result(false, 0, "No readable A$ payout; no action");
        if (cents.size() != 1) return new Result(false, 0, "Multiple different dollar amounts; no action");
        int amount = cents.iterator().next();
        if (amount < 100 || amount > 100000) {
            return new Result(false, 0, "Payout outside expected range; no action");
        }
        return new Result(true, amount / 100.0,
                String.format(Locale.US, "Read offer A$%.2f", amount / 100.0));
    }
}
