package com.hmdm.launcher.locker24;

public final class WatchdogPolicy {
    public enum Action { NONE, RELAUNCH }

    private static final int LOCK_TASK_LOST_LIMIT = 2;

    private final int launcherInFrontLimit;
    private int lockTaskLostCount;
    private int launcherInFrontCount;
    private boolean lastInteractiveInFront;
    private boolean screenOffRelaunched;

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
        Action action = evaluateCore(required, adminUnlocked, lockTaskLocked, launcherInFront, interactive);
        if (action == Action.RELAUNCH && !interactive) {
            if (screenOffRelaunched) {
                return Action.NONE;
            }
            screenOffRelaunched = true;
        }
        return action;
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
}
