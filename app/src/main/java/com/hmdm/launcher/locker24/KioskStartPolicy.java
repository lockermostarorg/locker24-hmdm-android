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
    public static final long UPDATE_WINDOW_MS = 30_000;
    public static final long CLOCK_SKEW_MS = 60_000;
    public static final long RETRY_CHECK_MS = 3_000;
    public static final long SETTLED_CHECK_MS = 10_000;
    public static final long ENGAGED_AFTER_UPDATE_MS = 5_000;
    public static final long RECENT_FRESH_START_MS = 5_000;

    private KioskStartPolicy() {
    }

    public static final class Decision {
        public final boolean clearTask;
        public final boolean forcedByUpdate;
        public final long newHandled;
        public final boolean joinsFreshStart;

        Decision(boolean clearTask, boolean forcedByUpdate, long newHandled) {
            this(clearTask, forcedByUpdate, newHandled, false);
        }

        Decision(boolean clearTask, boolean forcedByUpdate, long newHandled, boolean joinsFreshStart) {
            this.clearTask = clearTask;
            this.forcedByUpdate = forcedByUpdate;
            this.newHandled = newHandled;
            this.joinsFreshStart = joinsFreshStart;
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

    /**
     * As decide(), and a start that is not forced while lock task is gone right after a fresh start
     * or retry keeps the task: that task is the fresh start still coming up, not a dead one, and
     * clearing it would cold-start the app a second time.
     */
    public static Decision decide(boolean updateTracking, long lastUpdateTime, long handledUpdate,
                                  boolean lockTaskRunning, long nowWall, long lastFreshStartAt, long nowElapsed) {
        Decision decision = decide(updateTracking, lastUpdateTime, handledUpdate, lockTaskRunning, nowWall);
        if (updateTracking && !decision.forcedByUpdate && !lockTaskRunning
                && freshStartRecent(lastFreshStartAt, nowElapsed)) {
            return new Decision(false, false, decision.newHandled, true);
        }
        return decision;
    }

    static boolean withinUpdateWindow(long lastUpdateTime, long nowWall) {
        long age = nowWall - lastUpdateTime;
        return age <= UPDATE_WINDOW_MS && age > -CLOCK_SKEW_MS;
    }

    public static boolean shouldRetry(boolean lockTaskLocked, boolean kioskRequired, boolean adminUnlocked) {
        return !lockTaskLocked && kioskRequired && !adminUnlocked;
    }

    /**
     * Lock task holding more than ENGAGED_AFTER_UPDATE_MS after an update means the updated app is
     * alive, so the update needs no forced fresh start. The margin skips the moment right after a
     * replacement where lock task can still read LOCKED for the dead task.
     */
    public static boolean engagedAnswersUpdate(boolean lockTaskLocked, long lastUpdateTime, long handledUpdate,
                                               long nowWall) {
        return lockTaskLocked && lastUpdateTime > 0 && lastUpdateTime != handledUpdate
                && nowWall - lastUpdateTime > ENGAGED_AFTER_UPDATE_MS;
    }

    /** True when the install happened before the current boot (BOOT_COMPLETED can arrive late). */
    public static boolean installPredatesBoot(long lastUpdateTime, long nowWall, long elapsedSinceBoot) {
        return lastUpdateTime > 0 && lastUpdateTime < nowWall - elapsedSinceBoot;
    }

    public static boolean freshStartRecent(long lastFreshStartAt, long nowElapsed) {
        return lastFreshStartAt > 0 && nowElapsed >= lastFreshStartAt
                && nowElapsed - lastFreshStartAt < RECENT_FRESH_START_MS;
    }

    public enum ReplaceAction { RELAUNCH, RECHECK_LOCKED, RECHECK_FRESH_START }

    /**
     * What the package receiver does when the kiosk app was replaced. A fresh start or retry issued
     * a moment ago has not engaged lock task yet, so relaunching the launcher now would start the
     * app a second time; the delayed recheck, the retry check and the watchdog stay as backstops.
     */
    public static ReplaceAction onKioskAppReplaced(boolean lockTaskRunning, long lastFreshStartAt,
                                                   long nowElapsed) {
        if (lockTaskRunning) {
            return ReplaceAction.RECHECK_LOCKED;
        }
        if (freshStartRecent(lastFreshStartAt, nowElapsed)) {
            return ReplaceAction.RECHECK_FRESH_START;
        }
        return ReplaceAction.RELAUNCH;
    }
}
