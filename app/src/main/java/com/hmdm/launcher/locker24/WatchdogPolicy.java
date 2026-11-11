package com.hmdm.launcher.locker24;

public final class WatchdogPolicy {
    public enum Action { NONE, RELAUNCH }

    private static final int LOCK_TASK_LOST_LIMIT = 2;

    private final int launcherInFrontLimit;
    private int lockTaskLostCount;
    private int launcherInFrontCount;

    public WatchdogPolicy(int launcherInFrontLimit) {
        this.launcherInFrontLimit = launcherInFrontLimit;
    }

    public Action evaluate(boolean required, boolean adminUnlocked, boolean lockTaskLocked, boolean launcherInFront) {
        if (!required || adminUnlocked) {
            reset();
            return Action.NONE;
        }
        if (!lockTaskLocked) {
            launcherInFrontCount = 0;
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
}
