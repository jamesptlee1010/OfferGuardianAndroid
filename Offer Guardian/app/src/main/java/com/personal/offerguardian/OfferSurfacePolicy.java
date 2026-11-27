package com.personal.offerguardian;

/** Only Uber Driver and DoorDash are supported screenshot hosts; the Uber X gesture policy remains Uber-only. */
public final class OfferSurfacePolicy {
    public static final String UBER="com.ubercab.driver";
    private OfferSurfacePolicy() {}
    public static final String DOORDASH="com.doordash.driverapp";
    public enum Host { UBER, DOORDASH, OTHER }
    public static Host classify(String packageName) {
        return UBER.equals(packageName)?Host.UBER:DOORDASH.equals(packageName)?Host.DOORDASH:Host.OTHER;
    }
    public static boolean shouldScan(Host host) {return host==Host.UBER||host==Host.DOORDASH;}
    /** Never dispatch a gesture for another foreground app. */
    public static boolean allowDecline(Host host,boolean cardVerified,
                                       boolean offerTextVerified,int stableFrames,
                                       double xConfidence) {
        return host==Host.UBER && cardVerified && xConfidence>=.90;
    }
}
