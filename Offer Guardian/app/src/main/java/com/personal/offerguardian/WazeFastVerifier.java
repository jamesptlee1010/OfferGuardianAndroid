package com.personal.offerguardian;

import java.util.Locale;

/**
 * Two fresh, matching Waze offer frames are required before a decline gesture.
 * This is intentionally independent of Android so its safety logic is testable.
 * No screenshot or gestures are taken by this class.
 */
public final class WazeFastVerifier {
    public static final long MAX_CONFIRM_GAP_MS = 2000;
    private boolean pending;
    private long firstAtMs;
    private double payout;
    private int x,y,cardTop;
    private String merchant;

    public void reset() {pending=false;firstAtMs=0;merchant="";}
    public boolean isPending(){return pending;}
    public long firstAtMs(){return firstAtMs;}

    /** Returns true only when a SECOND fresh image matches the same safe offer. */
    public boolean observe(double price,int x,int y,int top,double confidence,
                           boolean offerTextConfirmed,String pickup,long nowMs) {
        String normalized = normalize(pickup);
        if(!offerTextConfirmed || confidence < .94 || !Double.isFinite(price) || price<=0 ||
                x<0 || y<0 || top<0 || normalized.isEmpty()) {reset();return false;}
        if(pending && nowMs > firstAtMs && nowMs-firstAtMs<=MAX_CONFIRM_GAP_MS &&
                Math.abs(price-payout)<.005 && Math.abs(x-this.x)<=3 &&
                Math.abs(y-this.y)<=4 && Math.abs(top-cardTop)<=4 &&
                merchant.equals(normalized)) {
            reset();return true;
        }
        pending=true;firstAtMs=nowMs;payout=price;
        this.x=x;this.y=y;this.cardTop=top;merchant=normalized;
        return false;
    }

    private static String normalize(String value) {
        return value==null?"":value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
}
