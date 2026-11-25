package com.personal.offerguardian;

/**
 * Tracks exactly one screenshot/OCR pipeline at a time. All methods are called
 * from the Accessibility service's main thread (including ML Kit listeners).
 * A late callback from an expired pipeline must NEVER modify a new pipeline or
 * use its old OCR to dispatch a gesture.
 */
public final class OfferScanWatchdog {
    private long nextToken;
    private long activeToken;
    private long startedAt;
    private long lastFinishedAt;
    private int recoveries;

    public long begin(long now) {
        if (activeToken != 0) throw new IllegalStateException("Capture already active");
        activeToken = ++nextToken;
        startedAt = now;
        return activeToken;
    }

    public boolean busy() { return activeToken != 0; }
    public boolean owns(long token) { return token != 0 && activeToken == token; }
    public long elapsed(long now) { return busy() ? Math.max(0L, now - startedAt) : 0L; }
    public boolean overdue(long now, long limitMs) {
        return busy() && now >= startedAt && now - startedAt >= limitMs;
    }
    public boolean safeToProcess(long token, long now, long limitMs) {
        return owns(token) && !overdue(now, limitMs);
    }
    public boolean finish(long token, long now) {
        if (!owns(token)) return false;
        activeToken = 0;
        startedAt = 0;
        lastFinishedAt = now;
        return true;
    }
    public long recover(long now, long limitMs) {
        if (!overdue(now, limitMs)) return 0;
        long previous = activeToken;
        activeToken = 0;
        startedAt = 0;
        recoveries++;
        return previous;
    }
    public void invalidate() {
        activeToken = 0;
        startedAt = 0;
    }
    public long lastFinishedAt() { return lastFinishedAt; }
    public int recoveries() { return recoveries; }
}
