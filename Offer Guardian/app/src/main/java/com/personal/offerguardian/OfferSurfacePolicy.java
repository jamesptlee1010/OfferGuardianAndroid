package com.personal.offerguardian;

/** Which foreground app can legitimately present an Uber offer on the device. */
public final class OfferSurfacePolicy {
    public static final String UBER="com.ubercab.driver";
    public static final String WAZE="com.waze";
    private OfferSurfacePolicy() {}
    public enum Host { UBER, WAZE, OTHER }
    public static Host classify(String packageName) {
        if(UBER.equals(packageName))return Host.UBER;
        if(WAZE.equals(packageName))return Host.WAZE;
        return Host.OTHER;
    }
    public static boolean shouldScan(Host host) {return host!=Host.OTHER;}
    public static boolean shouldScan(Host host,boolean lowResource) {return host==Host.UBER || (!lowResource && host==Host.WAZE);}
    /** More rigorous safeguards for sending a gesture while a navigation app is on screen. */
    public static boolean allowDecline(Host host,boolean cardVerified,
                                       boolean offerTextVerified,int stableFrames,
                                       double xConfidence) {
        if(!cardVerified||xConfidence<.90)return false;
        if(host==Host.UBER)return true;
        return host==Host.WAZE && offerTextVerified && stableFrames>=2 && xConfidence>=.94;
    }
}
