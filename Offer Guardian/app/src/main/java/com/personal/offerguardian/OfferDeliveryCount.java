package com.personal.offerguardian;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the delivery COUNT from the green offer-header label, not from the
 * item count, route map, addresses or any other parenthesised number.
 * Zero always means UNKNOWN; never silently treat missing header text as one.
 * A complete, readable bare "Delivery" or "Pack & Deliver" denotes one.
 * A separate two-image verifier is required before any count-based decline.
 */
public final class OfferDeliveryCount {
    private OfferDeliveryCount() {}
    private static final Pattern HEADING=Pattern.compile(
            "(?i)(?:\\bpack\\s*(?:&|and)\\s*deliver\\b|\\bdelivery\\b|\\bpackage\\b)");
    private static final Pattern SUFFIX=Pattern.compile("^\\s*\\(\\s*([1-9][0-9]?)\\s*\\)");
    public static final class Reading {
        public final int count;
        public final String heading, reason;
        Reading(int n,String h,String r){count=n;heading=h;reason=r;}
    }
    public static Reading detect(int xY,List<OfferScreenEvaluator.Line> all) {
        if(all==null)return new Reading(0,"","Header OCR unavailable");
        List<OfferScreenEvaluator.Line> possible=new ArrayList<>();
        for(OfferScreenEvaluator.Line line:all) {
            if(line==null||line.text==null||line.text.trim().isEmpty())continue;
            int mid=(line.top+line.bottom)/2;
            // Header's left-side green pill is close to the detected grey X.
            // This deliberately excludes the price, time, address and map.
            if(mid<xY-115||mid>xY+45||line.left<28||line.left>=445||
                    line.right>560||line.bottom<=line.top)continue;
            possible.add(line);
        }
        possible.sort(Comparator.comparingInt((OfferScreenEvaluator.Line l)->l.top)
                .thenComparingInt(l->l.left));
        int result=0;String raw="";
        for(OfferScreenEvaluator.Line anchor:possible) {
            if(!HEADING.matcher(anchor.text).find() &&
                    !anchor.text.trim().matches("(?i)pack\\s*(?:&|and)"))continue;
            int mid=(anchor.top+anchor.bottom)/2;
            List<OfferScreenEvaluator.Line> sameRow=new ArrayList<>();
            for(OfferScreenEvaluator.Line line:possible) {
                if(Math.abs((line.top+line.bottom)/2-mid)<=23 &&
                        line.left>=anchor.left-12 && line.left<485) sameRow.add(line);
            }
            sameRow.sort(Comparator.comparingInt(l->l.left));
            StringBuilder combined=new StringBuilder();
            for(OfferScreenEvaluator.Line part:sameRow) {
                if(combined.length()>0)combined.append(' ');
                combined.append(part.text.trim());
            }
            String heading=combined.toString().replaceAll("\\s+"," ").trim();
            Matcher m=HEADING.matcher(heading);
            if(!m.find())continue;
            String after=heading.substring(m.end()).trim();
            Matcher n=SUFFIX.matcher(after);
            int found;
            if(n.find()) {
                try { found=Integer.parseInt(n.group(1)); }
                catch(NumberFormatException ex) {found=0;}
                // Do not treat truncated/incomplete suffixes as a single order.
                if(found<1||found>12)found=0;
            } else if(after.startsWith("(")||after.startsWith(")")||
                    after.matches("^[0-9].*") || heading.endsWith("("))found=0;
            else found=1;
            if(found==0)return new Reading(0,heading,"Delivery header count incomplete or ambiguous");
            if(result!=0 && result!=found)
                return new Reading(0,heading,"Conflicting delivery counts in offer header");
            result=found;raw=heading;
        }
        if(result==0)return new Reading(0,"","Delivery header missing or unreadable");
        return new Reading(result,raw,"Delivery count read from card header");
    }
}
