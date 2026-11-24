package com.personal.offerguardian;

/** Only Uber Driver is permitted to trigger screenshots or decline gestures. */
public final class OfferSurfacePolicy {
    public static final String UBER="com.ubercab.driver";
    private OfferSurfacePolicy() {}
    public enum Host { UBER, OTHER }
    public static Host classify(String packageName) {
        return UBER.equals(packageName)?Host.UBER:Host.OTHER;
    }
    public static boolean shouldScan(Host host) {return host==Host.UBER;}
    /** Never dispatch a gesture for another foreground app. */
    public static boolean allowDecline(Host host,boolean cardVerified,
                                       boolean offerTextVerified,int stableFrames,
                                       double xConfidence) {
        return host==Host.UBER && cardVerified && xConfidence>=.90;
    }
}
