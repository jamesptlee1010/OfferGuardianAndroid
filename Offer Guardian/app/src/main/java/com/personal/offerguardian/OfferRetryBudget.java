package com.personal.offerguardian;

/** Limits fast re-reads of a partially OCR'd card so they cannot spin indefinitely. */
public final class OfferRetryBudget {
    private static final int MAX_FAST_READS=5;
    private static final long RESET_AFTER_MS=10000;
    private int x=-999,y=-999,count;
    private long lastAt;
    public void reset(){count=0;x=-999;y=-999;lastAt=0;}
    public boolean request(int cardX,int cardY,long now) {
        if(Math.abs(cardX-x)>5||Math.abs(cardY-y)>5||now-lastAt>RESET_AFTER_MS) {
            x=cardX;y=cardY;count=0;
        }
        lastAt=now;
        if(count>=MAX_FAST_READS)return false;
        count++;
        return true;
    }
    public int used(){return count;}
}
