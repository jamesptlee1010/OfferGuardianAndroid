package com.personal.offerguardian;

import java.util.Locale;

/** Two independent images must agree on payout, hour LOWER bound, km, merchant and X. */
public final class OfferBoundVerifier {
    private static final long MAX_GAP_MS=4500;
    private boolean pending;
    private long at;
    private int cents, hours, metres, deliveryCount, x, y;
    private String pickup="";

    public void reset(){pending=false;at=0;pickup="";}
    public boolean isPending(){return pending;}
    public boolean observe(double payout,OfferDetailsParser.Details d,int sx,int sy,long now) {
        return observe(payout,d,sx,sy,now,false);
    }
    public boolean observe(double payout,OfferDetailsParser.Details d,int sx,int sy,long now,
                           boolean countRequired) {
        if(countRequired && (d==null||d.deliveryCount<=0)) {reset();return false;}
        if(!OfferSafeDecline.hasReliableHourBound(d) || !Double.isFinite(payout) ||
                payout < 3 || payout > 1000 || now<=0) {reset();return false;}
        int pc=(int)Math.round(payout*100);
        String merchant=d.pickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
        if(pending && now>at && now-at<=MAX_GAP_MS && cents==pc && hours==d.parsedHours &&
                (!countRequired||d.deliveryCount==deliveryCount) && Math.abs(x-sx)<=3 && Math.abs(y-sy)<=4 &&
                pickup.equals(merchant)) {reset();return true;}
        pending=true;at=now;cents=pc;hours=d.parsedHours;metres=d.metres;
        x=sx;y=sy;deliveryCount=d.deliveryCount;pickup=merchant;
        return false;
    }
}
