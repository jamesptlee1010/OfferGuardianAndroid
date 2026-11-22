package com.personal.offerguardian;

/** Fail closed when an OCR field is missing or physically implausible. No invented durations. */
public final class OfferExtractionGuard {
    private OfferExtractionGuard() {}
    public static final class Result {
        public final boolean valid;
        public final String reason;
        private Result(boolean valid,String reason){this.valid=valid;this.reason=reason;}
    }
    private static Result fail(String reason){return new Result(false,reason);}
    public static Result check(OfferDetailsParser.Details d) {
        if(d==null)return fail("No offer details read");
        if(d.conflictingDuration)return fail("Conflicting times in the same offer card");
        if(d.minutes<=0||d.minutes>720)return fail("Duration unreadable or outside supported range");
        if(d.metres<=0||d.metres>300000)return fail("Distance unreadable or outside supported range");
        // A lone '1 hr' may really be '1 hr 7 min' with the minutes missed.
        if(d.hoursSeen&&!d.minutesSeen)return fail("Hours detected without a readable minute component");
        double avgKmh=d.metres*0.06/d.minutes;
        if(avgKmh>80.0)return fail("Improbable travel speed: possible missing hour in OCR");
        if("Pack & Deliver".equals(d.type)&&d.itemCount>=10&&d.minutes<20)
            return fail("Shopping order too short for item count: possible missing hour");
        if(d.pickup==null||d.pickup.trim().length()<3)
            return fail("Pickup merchant not clearly readable");
        return new Result(true,"Duration, distance and merchant pass validation");
    }
}
