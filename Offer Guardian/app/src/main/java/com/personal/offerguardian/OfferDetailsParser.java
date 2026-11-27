package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative, pure-Java extraction from OCR lines INSIDE the verified offer card.
 * Duration units must be converted together; a trailing "7 min" is NOT the duration
 * of an offer that also says "1 hr".
 */
public final class OfferDetailsParser {
    private OfferDetailsParser() {}
    private static final Pattern KM=Pattern.compile("(?i)(\\d{1,3}(?:[.,]\\d+)?)\\s*k\\s*m\\b");
    private static final Pattern HOURS=Pattern.compile("(?i)(\\d{1,2})\\s*(?:hours?|hrs?|h)\\b");
    private static final Pattern MINS=Pattern.compile("(?i)(\\d{1,3})\\s*(?:minutes?|mins?|m)(?![a-z])");
    private static final Pattern ITEM_COUNT=Pattern.compile("(?i)(\\d{1,3})\\s*items?\\b");
    private static final Pattern MONEY=Pattern.compile("(?i)(?:AUD\\s*)?\\$\\s*[0-9,.]+");
    private static final Pattern DELIVERY_TYPE=Pattern.compile("(?i)\\bpack\\s*&\\s*deliver\\b|\\bdelivery(?:\\s*\\(\\d+\\))?\\b");

    public static final class Details {
        public final String type, pickup, destination;
        public final int minutes, metres, itemCount, parsedHours, parsedMinutes, deliveryCount;
        public final String rawDuration, rawDistance;
        public final boolean hoursSeen, minutesSeen, conflictingDuration;
        Details(String type,String pickup,String destination,int minutes,int metres,int itemCount,
                int parsedHours,int parsedMinutes,int deliveryCount,String rawDuration,String rawDistance,
                boolean hoursSeen,boolean minutesSeen,boolean conflictingDuration) {
            this.type=type;this.pickup=pickup;this.destination=destination;
            this.minutes=minutes;this.metres=metres;this.itemCount=itemCount;
            this.parsedHours=parsedHours;this.parsedMinutes=parsedMinutes;this.deliveryCount=deliveryCount;
            this.rawDuration=rawDuration;this.rawDistance=rawDistance;
            this.hoursSeen=hoursSeen;this.minutesSeen=minutesSeen;
            this.conflictingDuration=conflictingDuration;
        }
    }

    // Correct only an isolated OCR lookalike in an explicit numeric unit slot.
    static String numericTime(String raw) {
        return raw.replaceAll("(?i)(?<=hr)([lI|])(?=\\s*min\\b)", " 1")
                .replaceAll("(?i)(?<![a-z0-9])([lI|])(?=\\s*(?:hours?|hrs?|h|minutes?|mins?|m)(?![a-z]))", "1");
    }

    /** OCR boxes must be normalised to 686x1536, matching the X detector. */
    public static Details parse(int xY,List<OfferScreenEvaluator.Line> all) {
        if(all==null)all=Collections.emptyList();
        int top=Math.max(0,xY-77);
        List<OfferScreenEvaluator.Line> lines=new ArrayList<>();
        for(OfferScreenEvaluator.Line line:all) {
            if(line==null||line.text==null)continue;
            int cy=(line.top+line.bottom)/2;
            if(line.left>=35&&line.left<640&&line.right<=666&&
                    cy>top+12&&cy<1432&&!line.text.trim().isEmpty())lines.add(line);
        }
        lines.sort(Comparator.comparingInt((OfferScreenEvaluator.Line x)->x.top)
                .thenComparingInt(x->x.left));
        String type="Delivery";
        int itemCount=0,metres=0,metricBottom=-1,minutes=0,parsedHours=0,parsedMinutes=0;
        String rawDistance="",rawDuration="";
        boolean hoursSeen=false,minutesSeen=false,conflicting=false;
        for(OfferScreenEvaluator.Line line:lines) {
            String s=line.text.trim();
            String lower=s.toLowerCase(Locale.ROOT);
            if(lower.contains("pack")&&lower.contains("deliver"))type="Pack & Deliver";
            else if(s.matches("(?i).*delivery\\s*\\(\\d+\\).*"))type="Delivery (multiple)";
            Matcher items=ITEM_COUNT.matcher(s);
            if(items.find())try{itemCount=Integer.parseInt(items.group(1));}catch(NumberFormatException ignored){}
        }
        // Use the kilometre line as an anchor. ML Kit frequently splits "1 hr",
        // "7 min", and "9.8 km" into separate but neighbouring text lines.
        // Never select a minute value elsewhere on the card without this anchor.
        for(OfferScreenEvaluator.Line kmLine:lines) {
            Matcher k=KM.matcher(kmLine.text);
            if(!k.find())continue;
            int cy=(kmLine.top+kmLine.bottom)/2;
            StringBuilder combined=new StringBuilder();
            int bottom=kmLine.bottom;
            for(OfferScreenEvaluator.Line nearby:lines) {
                int otherCy=(nearby.top+nearby.bottom)/2;
                if(Math.abs(otherCy-cy)>46)continue;
                if(combined.length()>0)combined.append(' ');
                combined.append(nearby.text.trim());
                bottom=Math.max(bottom,nearby.bottom);
            }
            String joined=combined.toString().replaceAll("\\s+"," ").trim();
            String numeric=numericTime(joined);
            Matcher hm=HOURS.matcher(numeric),mm=MINS.matcher(numeric);
            int hours=0,mins=0;
            boolean h=hm.find(),m=mm.find();
            if(h)try{hours=Integer.parseInt(hm.group(1));}catch(NumberFormatException ignored){}
            if(m)try{mins=Integer.parseInt(mm.group(1));}catch(NumberFormatException ignored){}
            if(!h&&!m)continue;
            int candidate=hours*60+mins;
            if(metricBottom>0&&candidate!=minutes)conflicting=true;
            if(metricBottom<0||candidate>minutes) {
                minutes=candidate;parsedHours=hours;parsedMinutes=mins;
                hoursSeen=h;minutesSeen=m;
                rawDuration=joined;
                rawDistance=k.group(0);
                try{metres=(int)Math.round(Double.parseDouble(k.group(1).replace(',','.'))*1000);}
                catch(NumberFormatException ignored){metres=0;}
            }
            metricBottom=Math.max(metricBottom,bottom);
        }
        // A live price/hourly decision must not depend on OCR successfully
        // reading kilometres. Uber normally marks the time row with "total";
        // use that card-local anchor when the kilometre label is absent or
        // disconnected from the duration. Do not search arbitrary map labels.
        if(minutes<=0)for(OfferScreenEvaluator.Line timeLine:lines) {
            if(!timeLine.text.toLowerCase(Locale.ROOT).contains("total"))continue;
            int cy=(timeLine.top+timeLine.bottom)/2;
            StringBuilder combined=new StringBuilder();
            int bottom=timeLine.bottom;
            for(OfferScreenEvaluator.Line nearby:lines) {
                int other=(nearby.top+nearby.bottom)/2;
                if(Math.abs(other-cy)>54)continue;
                if(combined.length()>0)combined.append(' ');
                combined.append(nearby.text.trim());
                bottom=Math.max(bottom,nearby.bottom);
            }
            String joined=combined.toString().replaceAll("\\s+"," ").trim();
            String numeric=numericTime(joined);
            Matcher h=HOURS.matcher(numeric), m=MINS.matcher(numeric);
            boolean hasH=h.find(),hasM=m.find();
            if(!hasH&&!hasM)continue;
            int hours=0,mins=0;
            try {if(hasH)hours=Integer.parseInt(h.group(1));
                 if(hasM)mins=Integer.parseInt(m.group(1));}
            catch(NumberFormatException bad){continue;}
            minutes=hours*60+mins;parsedHours=hours;parsedMinutes=mins;
            hoursSeen=hasH;minutesSeen=hasM;rawDuration=joined;
            Matcher k=KM.matcher(joined);
            if(k.find()){
                rawDistance=k.group(0);
                try{metres=(int)Math.round(Double.parseDouble(k.group(1).replace(',','.'))*1000);}
                catch(NumberFormatException ignored){metres=0;}
            }
            metricBottom=bottom;
            break;
        }
        // If a km label is present but its time wasn't recognised, retain the
        // raw text for diagnostics and mark minutes unknown, never guess.
        if(metricBottom<0)for(OfferScreenEvaluator.Line line:lines) {
            Matcher k=KM.matcher(line.text);
            if(k.find()) {
                metricBottom=line.bottom;
                rawDuration=line.text.trim();rawDistance=k.group(0);
                try{metres=(int)Math.round(Double.parseDouble(k.group(1).replace(',','.'))*1000);}
                catch(NumberFormatException ignored){}
                break;
            }
        }
        String pickup="",destination="";
        if(metricBottom>0) {
            List<String> values=new ArrayList<>();
            for(OfferScreenEvaluator.Line line:lines) {
                if((line.top+line.bottom)/2<=metricBottom+20)continue;
                String value=line.text.trim().replaceAll("\\s+"," ");
                if(unrelated(value)||value.length()<3)continue;
                values.add(value);
            }
            if(!values.isEmpty()) {
                pickup=values.get(0);
                if(values.size()>1) {
                    StringBuilder dest=new StringBuilder();
                    for(int i=1;i<values.size();i++) {
                        if(dest.length()>0)dest.append(' ');
                        dest.append(values.get(i));
                    }
                    destination=dest.toString();
                }
            }
        }
        OfferDeliveryCount.Reading heading=OfferDeliveryCount.detect(xY,all);
        return new Details(type,pickup,destination,minutes,metres,itemCount,
                parsedHours,parsedMinutes,heading.count,rawDuration,rawDistance,
                hoursSeen,minutesSeen,conflicting);
    }

    private static boolean unrelated(String raw) {
        String s=raw.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        return s.equals("accept")||s.equals("match")||
                s.startsWith("est. earning")||s.startsWith("estimated earning")||
                s.contains("verify id")||s.equals("heavy")||s.equals("exclusive")||
                s.equals("total")||s.matches("\\d+ items?.*")||
                DELIVERY_TYPE.matcher(s).matches()||MONEY.matcher(s).matches();
    }
}
