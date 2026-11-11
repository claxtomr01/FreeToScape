package com.freetoscape;

final class RefreshThrottle {
    private long nextAllowed;
    private boolean busy, queued;
    synchronized boolean request(long now) {
        if (busy || queued || now < nextAllowed) return false;
        queued = true;
        nextAllowed = now + 30_000;
        return true;
    }
    synchronized void started(boolean manual) { busy = true; if (manual) queued = false; }
    synchronized void finished() { busy = false; }
    synchronized boolean busy() { return busy || queued; }
    synchronized long secondsRemaining(long now) { return Math.max(0, (nextAllowed - now + 999) / 1000); }
}
