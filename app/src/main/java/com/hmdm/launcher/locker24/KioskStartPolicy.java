package com.hmdm.launcher.locker24;

/**
 * Decides how a kiosk start treats the existing kiosk task.
 *
 * After the locked kiosk package is replaced, Android ends lock task mode but can still report
 * LOCKED for a moment; bringing that dead task forward shows a black screen. An update is detected
 * by the package's lastUpdateTime (wall clock), and exactly one start per update is forced fresh.
 * A failed install leaves lastUpdateTime unchanged, so it never forces a fresh start.
 */
public final class KioskStartPolicy {
    public static final long UPDATE_WINDOW_MS = 120_000;
    public static final long CLOCK_SKEW_MS = 60_000;
    public static final long RETRY_CHECK_MS = 3_000;

    private KioskStartPolicy() {
    }

    public static final class Decision {
        public final boolean clearTask;
        public final boolean forcedByUpdate;
        public final long newHandled;

        Decision(boolean clearTask, boolean forcedByUpdate, long newHandled) {
            this.clearTask = clearTask;
            this.forcedByUpdate = forcedByUpdate;
            this.newHandled = newHandled;
        }
    }

    /**
     * @param updateTracking  false keeps the upstream rule (clear the task only when lock task is not running)
     * @param lastUpdateTime  PackageInfo.lastUpdateTime of the kiosk app, 0 when unknown
     * @param handledUpdate   lastUpdateTime already answered by a fresh start (or by a reboot)
     */
    public static Decision decide(boolean updateTracking, long lastUpdateTime, long handledUpdate,
                                  boolean lockTaskRunning, long nowWall) {
        boolean upstreamClear = !lockTaskRunning;
        if (!updateTracking || lastUpdateTime <= 0 || lastUpdateTime == handledUpdate) {
            return new Decision(upstreamClear, false, handledUpdate);
        }
        if (!withinUpdateWindow(lastUpdateTime, nowWall)) {
            return new Decision(upstreamClear, false, lastUpdateTime);
        }
        return new Decision(true, true, lastUpdateTime);
    }

    static boolean withinUpdateWindow(long lastUpdateTime, long nowWall) {
        long age = nowWall - lastUpdateTime;
        return age <= UPDATE_WINDOW_MS && age > -CLOCK_SKEW_MS;
    }

    public static boolean shouldRetry(boolean forcedByUpdate, boolean alreadyRetried, boolean lockTaskLocked,
                                      boolean kioskRequired, boolean adminUnlocked) {
        return forcedByUpdate && !alreadyRetried && !lockTaskLocked && kioskRequired && !adminUnlocked;
    }
}
