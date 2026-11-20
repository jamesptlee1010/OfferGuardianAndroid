package com.personal.offerguardian;

import java.util.Locale;

/** Monotonic duration measurements; avoid wall clock changes during an offer. */
public final class OfferScanTiming {
    private OfferScanTiming(){}
    public static String summary(long scanStart,long screenshotReady,
                                 long visualReady,long ocrReady) {
        return String.format(Locale.US,
            "capture %dms | X %dms | OCR %dms | scan %dms",
            nonnegative(screenshotReady-scanStart),
            nonnegative(visualReady-screenshotReady),
            nonnegative(ocrReady-visualReady),
            nonnegative(ocrReady-scanStart));
    }
    private static long nonnegative(long d){return Math.max(0,d);}
}
