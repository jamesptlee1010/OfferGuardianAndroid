package com.personal.offerguardian;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming CSV decoder for OfferGuardian History exports (old and current).
 * Does not depend on Android; can be regression-tested with real legacy CSVs.
 * Historic OCR rates are NEVER used as proof of extraction reliability.
 */
public final class OfferHistoryCsvImport {
    private OfferHistoryCsvImport() {}
    private static final int MAX_FIELD_CHARS=64*1024;
    private static final int MAX_COLUMNS=80;
    private static final Pattern EXPLICIT_COUNT=Pattern.compile("\\(([1-9]|1[0-2])\\)");
    private static final Pattern NOTE_COUNT=Pattern.compile("(?i)\\bconfirm\\s+([1-9]|1[0-2])\\s+deliveries\\b");

    public static final class Entry {
        public long seenAt;
        public int payoutCents, minutes, metres, deliveryCount, verifiedFrames, parsedHours, parsedMinutes;
        public String type, pickup, destination, outcome, action;
        public String rawPayout, rawDuration, rawDistance, extractionStatus, extractionNote;
    }
    public interface Sink { void onRow(Entry entry); }
    public static final class Report {
        public int rows, invalid;
    }

    public static Report read(Reader input,Sink sink) throws IOException {
        PushbackReader csv=new PushbackReader(input,2);
        List<String> header=nextRow(csv);
        if(header==null)throw new IOException("CSV is empty");
        if(!header.isEmpty() && header.get(0).startsWith("\uFEFF"))
            header.set(0,header.get(0).substring(1));
        Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<header.size();i++) {
            String column=header.get(i).trim().toLowerCase(Locale.US);
            if(columns.put(column,i)!=null)throw new IOException("Duplicate CSV column: "+column);
        }
        for(String required:new String[]{"offer time","estimated payout aud","type","pickup",
                "destination shown","estimated minutes","estimated km","outcome","automation action"}) {
            if(!columns.containsKey(required))
                throw new IOException("Not an OfferGuardian History export (missing '"+required+"')");
        }
        Report result=new Report();
        List<String> row;
        while((row=nextRow(csv))!=null) {
            if(row.size()==1 && row.get(0).trim().isEmpty())continue;
            result.rows++;
            if(row.size()!=header.size()) {result.invalid++;continue;}
            try {sink.onRow(decode(row,columns));}
            catch(IllegalArgumentException invalid) {result.invalid++;}
        }
        return result;
    }

    private static Entry decode(List<String> row,Map<String,Integer> cols) {
        Entry e=new Entry();
        String timestamp=value(row,cols,"offer time");
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
        f.setLenient(false);
        ParsePosition position=new ParsePosition(0);
        Date d=f.parse(timestamp,position);
        if(d==null || position.getIndex()!=timestamp.length() || d.getTime()<=0)
            throw new IllegalArgumentException("Invalid offer date");
        e.seenAt=d.getTime();
        e.payoutCents=money(value(row,cols,"estimated payout aud"));
        if(e.payoutCents<=0)throw new IllegalArgumentException("Invalid offer payout");
        e.type=limit(text(value(row,cols,"type")));
        e.pickup=limit(text(value(row,cols,"pickup")));
        e.destination=limit(text(value(row,cols,"destination shown")));
        if(e.type.isEmpty())throw new IllegalArgumentException("Missing offer type");
        e.minutes=whole(value(row,cols,"estimated minutes"),1440);
        String distance=value(row,cols,"estimated km");
        if(distance.isEmpty())e.metres=0;
        else {
            try {
                BigDecimal km=new BigDecimal(distance.replace(",", ""));
                if(km.signum()<0 || km.compareTo(new BigDecimal("2500"))>0)
                    throw new IllegalArgumentException("Invalid distance");
                e.metres=km.movePointRight(3).setScale(0,RoundingMode.HALF_UP).intValueExact();
            } catch(ArithmeticException|NumberFormatException err) {
                throw new IllegalArgumentException("Invalid distance",err);
            }
        }
        e.outcome=value(row,cols,"outcome").toUpperCase(Locale.US);
        if(e.outcome.isEmpty())e.outcome="UNKNOWN";
        if(!e.outcome.equals("UNKNOWN") && !e.outcome.equals("ACCEPTED")
                && !e.outcome.equals("DECLINED") && !e.outcome.equals("EXPIRED"))
            throw new IllegalArgumentException("Invalid outcome");
        e.action=limit(text(value(row,cols,"automation action")));
        if(e.action.isEmpty())e.action="NONE";
        e.rawPayout=limit(text(value(row,cols,"raw payout ocr")));
        e.rawDuration=limit(text(value(row,cols,"raw duration ocr")));
        e.rawDistance=limit(text(value(row,cols,"raw distance ocr")));
        e.extractionNote=limit(text(value(row,cols,"extraction note")));
        e.verifiedFrames=whole(value(row,cols,"verified frames"),1000);
        e.parsedHours=whole(value(row,cols,"parsed hours"),24);
        e.parsedMinutes=whole(value(row,cols,"parsed minute component"),59);
        String status=value(row,cols,"extraction status").toUpperCase(Locale.US);
        // Legacy exports did not include any independent verification evidence.
        // Never believe the hourly rate displayed in a legacy CSV as proof.
        if((status.equals("VERIFIED")||status.equals("CORRECTED"))
                && e.verifiedFrames>=2 && e.minutes>0) e.extractionStatus=status;
        else if(status.equals("COUNT_ONLY")||status.equals("HOUR_BOUND"))e.extractionStatus=status;
        else e.extractionStatus="LEGACY_UNVERIFIED";
        String count=value(row,cols,"delivery count");
        if(!count.isEmpty()&&!count.equalsIgnoreCase("UNKNOWN")&&!count.equals("0"))
            e.deliveryCount=whole(count,12);
        if(e.deliveryCount==0) {
            Matcher m=EXPLICIT_COUNT.matcher(e.type);
            if(m.find())e.deliveryCount=Integer.parseInt(m.group(1));
        }
        if(e.deliveryCount==0) {
            Matcher m=NOTE_COUNT.matcher(e.extractionNote);
            if(m.find())e.deliveryCount=Integer.parseInt(m.group(1));
        }
        // Generic "Pack & Deliver" and "Delivery (multiple)" are ambiguous in
        // older exports, so we do not guess single/double/triple from them.
        return e;
    }

    private static String value(List<String> row,Map<String,Integer> columns,String name) {
        Integer index=columns.get(name);
        return index==null?"":row.get(index).trim();
    }
    private static String text(String value) {
        // Reverse the protective prefix added by OfferGuardian's CSV exporter.
        if(value.length()>1 && value.charAt(0)=='\'' && "=+-@".indexOf(value.charAt(1))>=0)
            return value.substring(1);
        return value;
    }
    private static String limit(String s) {
        if(s.length()>MAX_FIELD_CHARS)throw new IllegalArgumentException("Field too long");
        return s;
    }
    private static int money(String value) {
        try {
            String clean=value.replace("A$","").replace("$","").replace(",","").trim();
            BigDecimal decimal=new BigDecimal(clean);
            if(decimal.signum()<0 || decimal.compareTo(new BigDecimal("1000000"))>0)
                throw new IllegalArgumentException("Invalid payout");
            return decimal.movePointRight(2).intValueExact();
        } catch(NumberFormatException|ArithmeticException err) {
            throw new IllegalArgumentException("Invalid payout",err);
        }
    }
    private static int whole(String value,int upper) {
        if(value.isEmpty())return 0;
        try {
            int n=Integer.parseInt(value);
            if(n<0||n>upper)throw new IllegalArgumentException("Number out of range");
            return n;
        } catch(NumberFormatException e) {throw new IllegalArgumentException("Invalid number",e);}
    }

    /** RFC4180-style reader: handles escaped quotes, commas and embedded newlines. */
    private static List<String> nextRow(PushbackReader reader) throws IOException {
        ArrayList<String> fields=new ArrayList<>();
        StringBuilder field=new StringBuilder();
        boolean quoted=false,atFieldStart=true,closed=false,readAny=false;
        int n;
        while((n=reader.read())!=-1) {
            char c=(char)n;
            // Excel sometimes prefixes UTF-8 CSV files with a BOM.
            if(!readAny && c=='\uFEFF')continue;
            readAny=true;
            if(quoted) {
                if(c=='"') {
                    int peek=reader.read();
                    if(peek=='"')field.append('"');
                    else {quoted=false;closed=true;if(peek!=-1)reader.unread(peek);}
                } else field.append(c);
            } else if(c==',') {
                fields.add(field.toString());field.setLength(0);atFieldStart=true;closed=false;
                if(fields.size()>MAX_COLUMNS)throw new IOException("Too many CSV columns");
            } else if(c=='\n'||c=='\r') {
                if(c=='\r') {int peek=reader.read();if(peek!='\n'&&peek!=-1)reader.unread(peek);}
                fields.add(field.toString());return fields;
            } else if(c=='"' && atFieldStart) {quoted=true;atFieldStart=false;}
            else {
                if(closed && c!=' ' && c!='\t')throw new IOException("Malformed quoted CSV field");
                if(!closed)field.append(c);
                atFieldStart=false;
            }
            if(field.length()>MAX_FIELD_CHARS)throw new IOException("CSV field exceeds size limit");
        }
        if(quoted)throw new IOException("CSV ended inside a quoted field");
        if(!readAny && fields.isEmpty()&&field.length()==0)return null;
        fields.add(field.toString());return fields;
    }
}
