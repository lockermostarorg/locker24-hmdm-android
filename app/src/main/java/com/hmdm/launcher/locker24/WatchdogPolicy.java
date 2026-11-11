package com.hmdm.launcher.locker24;

public final class WatchdogPolicy {
    public enum Action { NONE, RELAUNCH }

    private static final int LOCK_TASK_LOST_LIMIT = 2;
    public static final int LOCK_LOST_RELAUNCH_LIMIT = 3;

    private final int launcherInFrontLimit;
    private int lockTaskLostCount;
    private int launcherInFrontCount;
    private boolean lastInteractiveInFront;
    private boolean screenOffRelaunched;
    private int lockLostRelaunches;
    private boolean lastRelaunchLockLost;

    public WatchdogPolicy(int launcherInFrontLimit) {
        this.launcherInFrontLimit = Math.max(1, launcherInFrontLimit);
    }

    public Action evaluate(boolean required, boolean adminUnlocked, boolean lockTaskLocked, boolean launcherInFront) {
        return evaluate(required, adminUnlocked, lockTaskLocked, launcherInFront, true);
    }

    public Action evaluate(boolean required, boolean adminUnlocked, boolean lockTaskLocked, boolean launcherInFront,
                           boolean interactive) {
        if (interactive) {
            screenOffRelaunched = false;
        }
        if (lockTaskLocked || adminUnlocked || (interactive && !required)) {
            lockLostRelaunches = 0;
        }
        lastRelaunchLockLost = false;
        Action action = evaluateCore(required, adminUnlocked, lockTaskLocked, launcherInFront, interactive);
        if (action == Action.RELAUNCH && !interactive) {
            if (screenOffRelaunched) {
                return Action.NONE;
            }
            screenOffRelaunched = true;
        }
        if (action == Action.RELAUNCH && !lockTaskLocked) {
            lockLostRelaunches++;
            lastRelaunchLockLost = true;
        }
        return action;
    }

    /**
     * True after LOCK_LOST_RELAUNCH_LIMIT lock-lost relaunches in a row without a tick
     * that saw lock task locked. The lock-lost path then stays quiet until a locked
     * tick, an admin unlock or an interactive tick where kiosk is not required.
     */
    public boolean isLockLostBackedOff() {
        return lockLostRelaunches >= LOCK_LOST_RELAUNCH_LIMIT;
    }

    /** The caller did not perform the RELAUNCH just returned, so it does not count toward the backoff. */
    public void onRelaunchSkipped() {
        if (lastRelaunchLockLost && lockLostRelaunches > 0) {
            lockLostRelaunches--;
        }
        lastRelaunchLockLost = false;
    }

    private Action evaluateCore(boolean required, boolean adminUnlocked, boolean lockTaskLocked,
                                boolean launcherInFront, boolean interactive) {
        if (interactive) {
            lastInteractiveInFront = launcherInFront;
        } else {
            launcherInFront = lastInteractiveInFront;
        }
        if (!required || adminUnlocked) {
            lastInteractiveInFront = false;
            reset();
            return Action.NONE;
        }
        if (!lockTaskLocked) {
            launcherInFrontCount = 0;
            if (isLockLostBackedOff()) {
                lockTaskLostCount = 0;
                return Action.NONE;
            }
            lockTaskLostCount++;
            if (lockTaskLostCount >= LOCK_TASK_LOST_LIMIT) {
                reset();
                return Action.RELAUNCH;
            }
            return Action.NONE;
        }
        lockTaskLostCount = 0;
        if (launcherInFront) {
            launcherInFrontCount++;
            if (launcherInFrontCount >= launcherInFrontLimit) {
                lastInteractiveInFront = false;
                reset();
                return Action.RELAUNCH;
            }
            return Action.NONE;
        }
        reset();
        return Action.NONE;
    }

    private void reset() {
        lockTaskLostCount = 0;
        launcherInFrontCount = 0;
    }

    public static final int DEFAULT_SECONDS = 30;
    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 240;

    public static int intervalSeconds(String value) {
        int seconds = DEFAULT_SECONDS;
        try {
            if (value != null) {
                seconds = Integer.parseInt(value.trim());
            }
        } catch (NumberFormatException e) {
            seconds = DEFAULT_SECONDS;
        }
        return Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
    }
}
