package com.personal.offerguardian;

import java.util.Locale;

/** Pure policy: an overlay's owning window, not the app behind it, authorises a tap. */
public final class CrossPlatformPolicy {
    private CrossPlatformPolicy() {}
    public static boolean scan(OfferSurfacePolicy.Host host,boolean uber,boolean dash,
            boolean largeUberWindow,boolean recentUberEvent,boolean freshDashNotice) {
        return (host==OfferSurfacePolicy.Host.UBER&&uber) ||
                (host==OfferSurfacePolicy.Host.DOORDASH&&(dash||uber)) ||
                (uber&&(largeUberWindow||recentUberEvent)) || (dash&&freshDashNotice);
    }
    public static boolean allowUberDecline(OfferSurfacePolicy.Host foreground,boolean ownsX,boolean offerIdentity,double confidence){
        return ownsX&&confidence>=.90&&(foreground==OfferSurfacePolicy.Host.UBER||offerIdentity);
    }
    public static boolean freshNotice(long elapsed,long now){return elapsed>0&&now>=elapsed&&now-elapsed<=16000;}
    public static boolean isDashOfferNotification(String packageName,String... fields){
        if(!OfferSurfacePolicy.DOORDASH.equals(packageName)||fields==null)return false;
        for(String raw:fields){if(raw==null)continue;
            String s=raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceAll("[!]+$","").trim();
            if(s.equals("new delivery")||s.equals("new delivery offer")||s.equals("new offer")||s.equals("new order")||s.equals("new dash offer")||s.equals("new delivery opportunity"))return true;
        }return false;
    }
    public static boolean idleUber(String text){
        if(text==null)return false;
        String s=text.toLowerCase(Locale.ROOT).replace('’','\'');
        boolean online=s.contains("you're online")||s.contains("you are online");
        boolean waiting=s.contains("finding trips")||s.contains("waiting for requests")||s.contains("looking for trips")||s.contains("finding offers");
        return online&&waiting&&!s.contains("pickup")&&!s.contains("drop-off")&&!s.contains("accept")&&!s.contains("navigate")&&!s.contains("delivery")&&!s.contains("match")&&!s.contains("complete trip");
    }
}
