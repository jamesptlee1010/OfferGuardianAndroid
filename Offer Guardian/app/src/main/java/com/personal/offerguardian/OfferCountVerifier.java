package com.personal.offerguardian;

import java.util.Locale;

/** Two independent frames confirming a mandatory delivery-count failure. */
public final class OfferCountVerifier {
    private static final long MAX_GAP_MS=4500;
    private boolean pending;
    private long at;
    private int count,cents,x,y;
    private String pickup="";
    public void reset(){pending=false;at=0;pickup="";}
    public boolean isPending(){return pending;}
    public boolean observe(double payout,OfferDetailsParser.Details d,int sx,int sy,long now) {
        if(d==null||d.deliveryCount<1||d.deliveryCount>12||
                d.pickup==null||d.pickup.trim().length()<3||
                !Double.isFinite(payout)||payout<3||payout>1000||now<=0) {
            reset();return false;
        }
        int price=(int)Math.round(payout*100);
        String merchant=d.pickup.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
        if(pending&&now>at&&now-at<=MAX_GAP_MS&&count==d.deliveryCount&&cents==price&&
                Math.abs(sx-x)<=3&&Math.abs(sy-y)<=4&&pickup.equals(merchant)) {
            reset();return true;
        }
        pending=true;at=now;count=d.deliveryCount;cents=price;x=sx;y=sy;pickup=merchant;
        return false;
    }
}
