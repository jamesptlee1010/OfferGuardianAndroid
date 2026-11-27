package com.personal.offerguardian;
/** Bounded retry budget for one verified financial offer. Fresh identity/target checks remain mandatory. */
final class UberDeclineRetry {
    private String key="";
    private int attempts=0;
    private long last=0, lastAttempt=0;
    private boolean fallback=false;
    void observe(String next,long now){
        if(key.isEmpty()||!key.equals(next)||now-last>90000){attempts=0;fallback=false;lastAttempt=0;}
        key=next;
        last=now;
    }
    int attempts(){return attempts;}
    boolean canAttempt(){return attempts<3;}
    boolean ready(long now){return canAttempt()&&(lastAttempt==0||now-lastAttempt>=1500);}
    static int holdMillis(int attempt){return attempt==1?90:attempt==2?140:200;}
    void defer(long now){lastAttempt=now;}
    int attempt(long now){lastAttempt=now;return ++attempts;}
    boolean canFallback(){return !fallback;}
    void fallback(){fallback=true;}
    void reset(){key="";attempts=0;last=0;lastAttempt=0;fallback=false;}
}
