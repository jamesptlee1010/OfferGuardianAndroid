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
    private int cents,minutes,metres,x,y;
    private String merchant="";
    public void reset(){pending=false;firstAt=0;merchant="";}
    public boolean isPending(){return pending;}
    public Result observe(double payout,OfferDetailsParser.Details d,
                          int screenX,int screenY,long atMs) {
        OfferExtractionGuard.Result checked=OfferExtractionGuard.check(d);
        if(!checked.valid){reset();return new Result(State.UNVERIFIED,checked.reason);}
        if(!Double.isFinite(payout)||payout<3||payout>1000) {
            reset();return new Result(State.UNVERIFIED,"Suspicious or incomplete payout");
        }
        int proposedCents=(int)Math.round(payout*100);
        String proposedMerchant=normalize(d.pickup);
        if(pending&&atMs>firstAt&&atMs-firstAt<=MAX_GAP_MS&&
                proposedCents==cents&&d.minutes==minutes&&d.metres==metres&&
                Math.abs(screenX-x)<=3&&Math.abs(screenY-y)<=4&&
                proposedMerchant.equals(merchant)) {
            reset();return new Result(State.CONFIRMED,"Two matching OCR frames");
        }
        boolean changed=pending&&atMs>firstAt&&atMs-firstAt<=MAX_GAP_MS;
        pending=true;firstAt=atMs;cents=proposedCents;
        minutes=d.minutes;metres=d.metres;x=screenX;y=screenY;merchant=proposedMerchant;
        return new Result(changed?State.CHANGED:State.WAITING,
                changed?"Payout, time, distance, merchant or card position changed; rechecking":
                        "Awaiting second matching reading");
    }
    private static String normalize(String input) {
        return input==null?"":input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
}
