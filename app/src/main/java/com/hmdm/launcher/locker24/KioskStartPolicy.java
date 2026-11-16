package com.hmdm.launcher.locker24;

public final class KioskStartPolicy {
    public static final long REPLACED_WINDOW_MS = 120_000;
    public static final long ENGAGE_CHECK_MS = 1_000;
    public static final long RETRY_DEBOUNCE_MS = 5_000;

    private KioskStartPolicy() {
    }

    /**
     * True when the kiosk app was installed or replaced at most REPLACED_WINDOW_MS ago.
     * Both values are elapsedRealtime, so a mark from before a reboot reads as in the future and is ignored.
     */
    public static boolean replacedRecently(long replacedAt, long now) {
        return replacedAt > 0 && now >= replacedAt && now - replacedAt <= REPLACED_WINDOW_MS;
    }

    /**
     * After the kiosk package is replaced, Android ends lock task mode but can still report
     * LOCKED for a moment; bringing that dead task forward shows a black screen, so a recent
     * replacement always starts the app fresh.
     */
    public static boolean clearTask(boolean lockTaskLocked, boolean replacedRecently) {
        return !lockTaskLocked || replacedRecently;
    }

    public static boolean shouldRetry(boolean alreadyRetried, boolean lockTaskLocked, boolean kioskRequired,
                                      boolean adminUnlocked, long lastRetryAt, long now) {
        if (alreadyRetried || lockTaskLocked || !kioskRequired || adminUnlocked) {
            return false;
        }
        return lastRetryAt <= 0 || now - lastRetryAt >= RETRY_DEBOUNCE_MS;
    }

    public static boolean consumeReplacedMark(long markAtStart, boolean lockTaskLocked) {
        return markAtStart > 0 && lockTaskLocked;
    }
}
