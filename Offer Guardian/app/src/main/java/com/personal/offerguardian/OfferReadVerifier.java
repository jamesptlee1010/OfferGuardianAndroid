package com.personal.offerguardian;

import java.util.Locale;

/** Independent two-frame confirmation BEFORE any rule, history payout or action. */
public final class OfferReadVerifier {
    public enum State { WAITING, CONFIRMED, UNVERIFIED, CHANGED }
    public static final class Result {
        public final State state;
        public final String reason;
        Result(State state,String reason){this.state=state;this.reason=reason;}
    }
    private static final long MAX_GAP_MS=4500;
    private boolean pending;
    private long firstAt;
    private int cents,minutes,metres,deliveryCount,x,y,confirmedDistanceMetres;
    /** Independently confirmed optional distance; 0 when absent or inconsistent. */
    public int confirmedDistanceMetres(){return confirmedDistanceMetres;}
    private String merchant="";
    public void reset(){pending=false;firstAt=0;merchant="";confirmedDistanceMetres=0;}
    public boolean isPending(){return pending;}
    public Result observe(double payout,OfferDetailsParser.Details d,
                          int screenX,int screenY,long atMs) {
        return observe(payout,d,screenX,screenY,atMs,false);
    }
    public Result observe(double payout,OfferDetailsParser.Details d,
                          int screenX,int screenY,long atMs,boolean countRequired) {
        if(countRequired && (d==null||d.deliveryCount<=0)) {
            reset();return new Result(State.UNVERIFIED,"Delivery header count unreadable");
        }
        OfferExtractionGuard.Result checked=OfferExtractionGuard.check(d);
        if(!checked.valid){reset();return new Result(State.UNVERIFIED,checked.reason);}
        if(!Double.isFinite(payout)||payout<3||payout>1000) {
            reset();return new Result(State.UNVERIFIED,"Suspicious or incomplete payout");
        }
        int proposedCents=(int)Math.round(payout*100);
        String proposedMerchant=normalize(d.pickup);
        if(pending&&atMs>firstAt&&atMs-firstAt<=MAX_GAP_MS&&
                proposedCents==cents&&d.minutes==minutes&&
                (!countRequired||d.deliveryCount==deliveryCount)&&
                Math.abs(screenX-x)<=3&&Math.abs(screenY-y)<=4&&
                proposedMerchant.equals(merchant)) {
            int verifiedDistance=d.metres>0 && d.metres==metres?d.metres:0;
            reset();confirmedDistanceMetres=verifiedDistance;
            return new Result(State.CONFIRMED,"Two matching OCR frames");
        }
        boolean changed=pending&&atMs>firstAt&&atMs-firstAt<=MAX_GAP_MS;
        pending=true;firstAt=atMs;cents=proposedCents;
        minutes=d.minutes;metres=d.metres;deliveryCount=d.deliveryCount;x=screenX;y=screenY;merchant=proposedMerchant;
        return new Result(changed?State.CHANGED:State.WAITING,
                changed?"Payout, time, delivery count, merchant or card position changed; rechecking":
                        "Awaiting second matching reading");
    }
    private static String normalize(String input) {
        return input==null?"":input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+","");
    }
}
