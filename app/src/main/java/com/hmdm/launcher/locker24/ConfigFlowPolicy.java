package com.hmdm.launcher.locker24;

/** The key and merge decisions ConfigFlows makes for the ConfigFlowGate. */
public final class ConfigFlowPolicy {
    public static final Object BACKGROUND = new Object();

    private ConfigFlowPolicy() {
    }

    public static Object key(Object notifier) {
        return notifier != null ? notifier : BACKGROUND;
    }

    public static boolean mergeUserInteraction(boolean pending, boolean next) {
        return pending || next;
    }

    public static boolean mergeSkipLoad(boolean pending, boolean next) {
        return pending && next;
    }

    public static ForcedApps mergeForced(ForcedApps pending, ForcedApps next) {
        return ForcedApps.union(pending, next);
    }
}
