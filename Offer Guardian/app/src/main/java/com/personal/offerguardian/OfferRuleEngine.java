package com.personal.offerguardian;

import java.util.Locale;

/** Pure-Java criteria evaluated against a verified Uber offer. Thresholds are inclusive. */
public final class OfferRuleEngine {
    private OfferRuleEngine() {}

    public enum Mode { ANY, ALL }
    public enum Outcome { SHOW, DECLINE, REVIEW }

    public static final class Config {
        public final boolean priceOn, hourlyOn, kilometreOn, deliveriesOn;
        public final double minPrice, minHourly, minPerKm;
        public final int minDeliveries;
        public final Mode mode;
        public Config(boolean priceOn, double minPrice, boolean hourlyOn, double minHourly,
                      boolean kilometreOn, double minPerKm, Mode mode) {
            this(priceOn,minPrice,hourlyOn,minHourly,kilometreOn,minPerKm,mode,false,2);
        }
        /** The delivery gate is mandatory and never joins ANY/ALL monetary rules. */
        public Config(boolean priceOn,double minPrice,boolean hourlyOn,double minHourly,
                      boolean kilometreOn,double minPerKm,Mode mode,
                      boolean deliveriesOn,int minDeliveries) {
            this.priceOn=priceOn; this.minPrice=minPrice;
            this.hourlyOn=hourlyOn; this.minHourly=minHourly;
            this.kilometreOn=kilometreOn; this.minPerKm=minPerKm;
            this.mode=mode == null ? Mode.ANY : mode;
            this.deliveriesOn=deliveriesOn;this.minDeliveries=minDeliveries;
        }
        public int count() {return (priceOn?1:0)+(hourlyOn?1:0)+(kilometreOn?1:0);}
        public boolean valid() {
            return (count()>0 || deliveriesOn) && (!deliveriesOn || (minDeliveries>=1 && minDeliveries<=4)) &&
                (!priceOn || validValue(minPrice)) &&
                (!hourlyOn || validValue(minHourly)) &&
                (!kilometreOn || validValue(minPerKm));
        }
    }

    public static final class Decision {
        public final Outcome outcome;
        public final String reason;
        public final double payout, hourly, perKm;
        public Decision(Outcome outcome, String reason, double payout, double hourly, double perKm) {
            this.outcome=outcome; this.reason=reason; this.payout=payout;
            this.hourly=hourly; this.perKm=perKm;
        }
    }

    private static boolean validValue(double v) {return Double.isFinite(v) && v>0 && v<=10000;}

    /** Time/distance <=0 mean not captured; unknown measurements can never trigger a tap. */
    public static Decision evaluate(Config config, double payout, int minutes, int metres) {
        return evaluate(config,payout,minutes,metres,0);
    }
    /**
     * deliveryCount=0 means UNKNOWN. Count is a mandatory gate, independent of
     * the price/rate AND/OR mode; missing count NEVER means one delivery.
     */
    public static Decision evaluate(Config config,double payout,int minutes,int metres,
                                    int deliveryCount) {
        if (config==null || !config.valid() || !Double.isFinite(payout) || payout<=0)
            return new Decision(Outcome.REVIEW,"Missing or invalid payout/rules",payout,Double.NaN,Double.NaN);
        double hourly=minutes>0?payout*60.0/minutes:Double.NaN;
        double km=metres>0?payout*1000.0/metres:Double.NaN;
        if(config.deliveriesOn) {
            if(deliveryCount<=0) return new Decision(Outcome.REVIEW,
                    "Delivery count unreadable; awaiting verification",payout,hourly,km);
            if(deliveryCount<config.minDeliveries) return new Decision(Outcome.DECLINE,
                    "Mandatory minimum deliveries: "+deliveryCount+" < "+config.minDeliveries,
                    payout,hourly,km);
        }
        if(config.count()==0)return new Decision(Outcome.SHOW,
                "Mandatory minimum deliveries met ("+deliveryCount+")",payout,hourly,km);
        int passed=0,failed=0,missing=0;
        StringBuilder detail=new StringBuilder();
        if(config.priceOn) {
            boolean met=payout>=config.minPrice;
            if(met)passed++;else failed++;
            append(detail,"payment "+(met?"meets":"below")+" "+money(config.minPrice));
        }
        if(config.hourlyOn) {
            if(Double.isNaN(hourly)) {missing++;append(detail,"hourly rate unavailable");}
            else {
                boolean met=hourly>=config.minHourly;
                if(met)passed++;else failed++;
                append(detail,"hourly "+money(hourly)+(met?" meets ":" below ")+money(config.minHourly));
            }
        }
        if(config.kilometreOn) {
            if(Double.isNaN(km)) {missing++;append(detail,"per-km rate unavailable");}
            else {
                boolean met=km>=config.minPerKm;
                if(met)passed++;else failed++;
                append(detail,"per km "+money(km)+(met?" meets ":" below ")+money(config.minPerKm));
            }
        }
        if(config.mode==Mode.ANY && passed>0)
            return new Decision(Outcome.SHOW,"ANY (OR): "+detail,payout,hourly,km);
        // Conservative rule: if a selected metric is absent, never automatically decline,
        // even if one known condition already fails in ALL mode.
        if(missing>0)
            return new Decision(Outcome.REVIEW,"Awaiting missing details: "+detail,payout,hourly,km);
        if(config.mode==Mode.ALL && failed==0)
            return new Decision(Outcome.SHOW,"ALL (AND): "+detail,payout,hourly,km);
        return new Decision(Outcome.DECLINE,(config.mode==Mode.ALL?"ALL (AND): ":"ANY (OR): ")+detail,payout,hourly,km);
    }
    private static void append(StringBuilder b,String part) {
        if(b.length()>0)b.append("; ");b.append(part);
    }
    private static String money(double v) {return String.format(Locale.US,"A$%.2f",v);}
    public static String summary(Config c) {
        if(c==null || !c.valid())return "Rules need configuration — review offers manually";
        StringBuilder b=new StringBuilder();
        if(c.priceOn)append(b,String.format(Locale.US,"Payment ≥ A$%.2f",c.minPrice));
        if(c.hourlyOn)append(b,String.format(Locale.US,"Hourly ≥ A$%.2f/hr",c.minHourly));
        if(c.kilometreOn)append(b,String.format(Locale.US,"Distance rate ≥ A$%.2f/km",c.minPerKm));
        String monetary=b.toString().replace("; ",c.mode==Mode.ANY?"  OR  ":"  AND  ");
        if(c.deliveriesOn) return "Deliveries ≥ "+c.minDeliveries+" (REQUIRED)"+
                (monetary.isEmpty()?"":"  AND  ("+monetary+")");
        return monetary;
    }
}
