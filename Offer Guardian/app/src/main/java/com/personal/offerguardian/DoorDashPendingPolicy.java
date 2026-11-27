package com.personal.offerguardian;
/** A dispatched first decline owns its pending confirmation until it expires. */
final class DoorDashPendingPolicy {
    private DoorDashPendingPolicy(){}
    static boolean allowIdentityReset(int stage,boolean verifiedNewAfterDispatch){
        return stage!=1&&(stage!=2||verifiedNewAfterDispatch);
    }
    static boolean expired(int stage,long started,long deadline,long now){
        return stage==1&&(deadline<=now||now-started>12000);
    }
    static boolean timerMatches(int stage,int id,int originalId,int seconds,long started,long deadline,long now,int retries){
        if((stage!=1&&stage!=2)||id<0||deadline-now<=1200||now-started>12000)return false;
        if(stage==2&&(retries>=1||now-started<1200||id!=originalId))return false;
        return seconds<0?id==originalId&&now-started<5000:seconds>0&&Math.abs(now+seconds*1000L-deadline)<=7500;
    }
}
