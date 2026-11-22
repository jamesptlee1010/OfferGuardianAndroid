package com.personal.offerguardian;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * History-screen display filters only. No reference to Prefs or OfferRuleEngine:
 * these limits can never change live offer decisions.
 *
 * Monetary limits are exact cents; distances are exact metres. An hourly
 * threshold in cents/hour is compared with payoutCents*60/minutes, and a
 * dollars/km threshold with payoutCents*1000/metres. No rounded display rate
 * is used to decide whether an offer qualifies.
 */
public final class OfferHistoryFilters {
    public static final int MIN_PAYMENT=0, MAX_PAYMENT=1,
        MIN_HOURLY=2, MAX_HOURLY=3,
        MIN_PER_KM=4, MAX_PER_KM=5,
        MIN_DISTANCE=6, MAX_DISTANCE=7, FIELD_COUNT=8;
    public static final String[] FIELD_LABELS = {
        "Minimum payment", "Maximum payment", "Minimum hourly rate", "Maximum hourly rate",
        "Minimum A$/km", "Maximum A$/km", "Minimum distance", "Maximum distance"
    };

    public static final class Sql {
        public final String where;
        public final List<String> args;
        private Sql(String where,List<String> args) {
            this.where=where;
            this.args=args;
        }
    }

    private final Long[] thresholds;
    private final String[] inputs;
    private final boolean matchAll;

    private OfferHistoryFilters(Long[] thresholds,String[] inputs,boolean matchAll) {
        this.thresholds=thresholds;
        this.inputs=inputs;
        this.matchAll=matchAll;
    }
    public static OfferHistoryFilters none() {
        return fromInputs(new String[FIELD_COUNT],true);
    }
    public static OfferHistoryFilters fromInputs(String[] raw,boolean matchAll) {
        if(raw==null||raw.length!=FIELD_COUNT) throw new IllegalArgumentException("Invalid filter fields");
        Long[] amounts=new Long[FIELD_COUNT];
        String[] inputs=new String[FIELD_COUNT];
        for(int i=0;i<FIELD_COUNT;i++) {
            String value=raw[i]==null?"":raw[i].trim();
            inputs[i]=value;
            if(value.isEmpty())continue;
            try {
                BigDecimal number=new BigDecimal(value.replace(',','.'));
                if(number.signum()<0 || number.compareTo(new BigDecimal("999999"))>0)
                    throw new IllegalArgumentException(FIELD_LABELS[i]+" must be between 0 and 999999.");
                // Currency: 2 decimal places; distance km: up to 3 decimals.
                amounts[i]=number.movePointRight(i>=MIN_DISTANCE?3:2).longValueExact();
            } catch(NumberFormatException|ArithmeticException e) {
                throw new IllegalArgumentException(FIELD_LABELS[i]+" must be a valid non-negative number"+
                    (i>=MIN_DISTANCE?" (up to 3 decimal places).":" (up to 2 decimal places)."));
            }
        }
        for(int i=0;i<FIELD_COUNT;i+=2) {
            if(amounts[i]!=null && amounts[i+1]!=null && amounts[i]>amounts[i+1])
                throw new IllegalArgumentException(FIELD_LABELS[i]+" cannot exceed "+FIELD_LABELS[i+1].toLowerCase(Locale.US)+".");
        }
        return new OfferHistoryFilters(amounts,inputs,matchAll);
    }
    public String input(int i) {return inputs[i];}
    public boolean matchAll() {return matchAll;}
    public boolean active() {return activeGroups()>0;}
    public int activeGroups() {
        int n=0;for(int i=0;i<FIELD_COUNT;i+=2)
            if(thresholds[i]!=null||thresholds[i+1]!=null)n++;
        return n;
    }
    public String summary() {
        if(!active())return "No history filters";
        String[] labels={"Payment", "Hourly", "A$/km", "Distance"};
        StringBuilder s=new StringBuilder(matchAll?"ALL: ":"ANY: ");
        boolean first=true;
        for(int i=0;i<FIELD_COUNT;i+=2) {
            if(thresholds[i]==null&&thresholds[i+1]==null)continue;
            if(!first)s.append(" · ");
            first=false;
            s.append(labels[i/2]).append(' ');
            String suffix=i==MIN_DISTANCE?" km":i==MIN_HOURLY?"/hr":i==MIN_PER_KM?"/km":"";
            String prefix=i==MIN_DISTANCE?"":"A$";
            if(thresholds[i]!=null)s.append("≥").append(prefix).append(inputs[i]).append(suffix);
            if(thresholds[i]!=null&&thresholds[i+1]!=null)s.append(' ');
            if(thresholds[i+1]!=null)s.append("≤").append(prefix).append(inputs[i+1]).append(suffix);
        }
        return s.toString();
    }

    /** A metric range is one condition, including both its lower and upper bounds. */
    public Sql toSql() {
        List<String> groups=new ArrayList<>();
        List<String> args=new ArrayList<>();
        for(int i=0;i<FIELD_COUNT;i+=2) {
            if(thresholds[i]==null&&thresholds[i+1]==null)continue;
            List<String> checks=new ArrayList<>();
            String expression;
            switch(i) {
                case MIN_PAYMENT:expression="payout_cents";break;
                case MIN_HOURLY:
                    checks.add("extraction_status IN ('VERIFIED','CORRECTED')");
                    checks.add("minutes > 0");
                    expression="payout_cents * 60";break;
                case MIN_PER_KM:
                    checks.add("extraction_status IN ('VERIFIED','CORRECTED')");
                    checks.add("metres > 0");
                    expression="payout_cents * 1000";break;
                default:
                    checks.add("extraction_status IN ('VERIFIED','CORRECTED')");
                    checks.add("metres > 0");
                    expression="metres";break;
            }
            if(thresholds[i]!=null) {
                checks.add(expression+(i==MIN_HOURLY?" >= ? * minutes":i==MIN_PER_KM?" >= ? * metres":" >= ?"));
                args.add(Long.toString(thresholds[i]));
            }
            if(thresholds[i+1]!=null) {
                checks.add(expression+(i==MIN_HOURLY?" <= ? * minutes":i==MIN_PER_KM?" <= ? * metres":" <= ?"));
                args.add(Long.toString(thresholds[i+1]));
            }
            groups.add("("+String.join(" AND ",checks)+")");
        }
        return new Sql(groups.isEmpty()?"1=1":"("+String.join(matchAll?" AND ":" OR ",groups)+")",args);
    }

    /** Reference matcher used by standalone regression tests. */
    public boolean matches(int cents,int minutes,int metres,boolean verified) {
        if(!active())return true;
        boolean any=false;
        for(int i=0;i<FIELD_COUNT;i+=2) {
            if(thresholds[i]==null&&thresholds[i+1]==null)continue;
            boolean ok=true;
            long value;
            long compareFactor=1;
            if(i==MIN_PAYMENT) {value=cents;}
            else if(i==MIN_HOURLY) {value=cents*60L;compareFactor=minutes;ok=verified&&minutes>0;}
            else if(i==MIN_PER_KM) {value=cents*1000L;compareFactor=metres;ok=verified&&metres>0;}
            else {value=metres;ok=verified&&metres>0;}
            if(thresholds[i]!=null && value<thresholds[i]*compareFactor)ok=false;
            if(thresholds[i+1]!=null && value>thresholds[i+1]*compareFactor)ok=false;
            if(matchAll && !ok)return false;
            any|=ok;
        }
        return matchAll||any;
    }
}
