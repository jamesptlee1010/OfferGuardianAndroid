package com.personal.offerguardian;

/** Position is stored as fractions of available travel, not absolute pixels. */
final class QuickDragPolicy {
    private QuickDragPolicy() {}
    static int clamp(int value, int max) { return Math.max(0, Math.min(value, Math.max(0,max))); }
    static int position(float fraction, int max) {
        if (!Float.isFinite(fraction)) fraction=0;
        return clamp(Math.round(Math.max(0f,Math.min(1f,fraction))*Math.max(0,max)),max);
    }
    static float fraction(int value,int max) { return max<=0?0f:(float)clamp(value,max)/max; }
    static boolean mayTap(boolean held, boolean moved, boolean cancelled) { return !held&&!moved&&!cancelled; }
}
