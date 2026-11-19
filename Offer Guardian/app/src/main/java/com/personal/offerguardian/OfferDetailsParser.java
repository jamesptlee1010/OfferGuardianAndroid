package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java conservative OCR extraction from the already verified Uber offer card.
 * Card values are the advertised offer data only; neither exact delivery addresses
 * nor completed-trip earnings are available from the offer card alone.
 */
public final class OfferDetailsParser {
    private OfferDetailsParser() {}
    private static final Pattern KM = Pattern.compile("(?i)(\\d{1,3}(?:[.,]\\d+)?)\\s*k\\s*m\\b");
    private static final Pattern TIME = Pattern.compile("(?i)(\\d{1,3})\\s*(?:min|minutes)\\b");
    private static final Pattern MONEY = Pattern.compile("(?i)(?:AUD\\s*)?\\$\\s*[0-9,.]+");
    private static final Pattern DELIVERY_TYPE = Pattern.compile("(?i)\\bpack\\s*&\\s*deliver\\b|\\bdelivery(?:\\s*\\(\\d+\\))?\\b");
    private static final Pattern ADDRESS_WORD = Pattern.compile("(?i)\\b(?:street|road|avenue|drive|lane|crescent|place|court|terrace|boulevard|highway|way|parade|st|rd|ave)\\b");

    public static final class Details {
        public final String type, pickup, destination;
        public final int minutes, metres;
        Details(String type, String pickup, String destination, int minutes, int metres) {
            this.type=type; this.pickup=pickup; this.destination=destination;
            this.minutes=minutes; this.metres=metres;
        }
    }

    /** OCR boxes must be normalised to 686x1536, matching the X detector. */
    public static Details parse(int xY, List<OfferScreenEvaluator.Line> all) {
        if (all == null) all=Collections.emptyList();
        int top = Math.max(0, xY - 77);
        // Reject any text off the card, including the map above and Android nav bar.
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
        for (OfferScreenEvaluator.Line line:all) {
            if (line.text==null) continue;
            int cy=(line.top+line.bottom)/2;
            if (line.left>=35 && line.left<640 && line.right<=666 &&
                cy>top+12 && cy<1432 && !line.text.trim().isEmpty()) lines.add(line);
        }
        lines.sort(Comparator.comparingInt((OfferScreenEvaluator.Line x)->x.top)
                .thenComparingInt(x->x.left));
        String type="Delivery";
        int minutes=0, metres=0, metricBottom=-1;
        for(OfferScreenEvaluator.Line line:lines) {
            String s=line.text.trim();
            if (s.toLowerCase(Locale.ROOT).contains("pack") &&
                s.toLowerCase(Locale.ROOT).contains("deliver")) type="Pack & Deliver";
            else if (s.matches("(?i).*delivery\\s*\\(\\d+\\).*")) type="Delivery (multiple)";
            Matcher tm=TIME.matcher(s);
            Matcher km=KM.matcher(s);
            if(tm.find() && minutes==0) {
                try {minutes=Integer.parseInt(tm.group(1));} catch(NumberFormatException ignored) {}
            }
            if(km.find() && metres==0) {
                try {metres=(int)Math.round(Double.parseDouble(km.group(1).replace(',','.'))*1000);}
                catch(NumberFormatException ignored) {}
            }
            if(TIME.matcher(s).find() || KM.matcher(s).find()) metricBottom=Math.max(metricBottom,line.bottom);
        }

        // Pickup and destination are identified by their ordering *below* the time/distance
        // line. If the separator is not readable, omit the location rather than guessing.
        String pickup="", destination="";
        if (metricBottom > 0) {
            List<String> values = new ArrayList<>();
            for (OfferScreenEvaluator.Line line:lines) {
                if ((line.top+line.bottom)/2 <= metricBottom+20) continue;
                String value=line.text.trim().replaceAll("\\s+"," ");
                if (unrelated(value) || value.length()<3) continue;
                // Store names generally precede the street/area on Uber's offer card.
                values.add(value);
            }
            if (!values.isEmpty()) {
                pickup=values.get(0);
                if (values.size()>1) {
                    StringBuilder dest=new StringBuilder();
                    for (int i=1;i<values.size();i++) {
                        // Do not include any post-card text, or one-off annotation badges.
                        if(dest.length()>0) dest.append(' ');
                        dest.append(values.get(i));
                    }
                    destination=dest.toString();
                }
            }
        }
        return new Details(type, pickup, destination, minutes, metres);
    }

    private static boolean unrelated(String raw) {
        String s=raw.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        return s.equals("accept") || s.equals("match") ||
            s.startsWith("est. earning") || s.startsWith("estimated earning") ||
            s.contains("verify id") || s.equals("heavy") || s.equals("exclusive") ||
            s.equals("total") || s.matches("\\d+ items?.*") ||
            DELIVERY_TYPE.matcher(s).matches() || MONEY.matcher(s).matches();
    }
}
