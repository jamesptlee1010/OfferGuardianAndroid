package com.personal.offerguardian;
/** Explicit modes: never silently substitute a different method during a diagnostic. */
final class TouchMethod {
    static final String KEY="touch_method_v31";
    static final String[] LABELS={"Original (native, then 90/140/200 ms)","Native click only","100 ms gesture","200 ms gesture","300 ms gesture"};
    static int normalize(int n){return n>=0&&n<LABELS.length?n:0;}
    static boolean tryNative(int mode){return normalize(mode)<=1;}
    static boolean allowGesture(int mode){return normalize(mode)!=1;}
    static int duration(int mode,int attempt){switch(normalize(mode)){case 2:return 100;case 3:return 200;case 4:return 300;default:return UberDeclineRetry.holdMillis(attempt);}}
}
