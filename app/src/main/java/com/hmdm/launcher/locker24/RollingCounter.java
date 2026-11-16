package com.hmdm.launcher.locker24;

import java.util.Iterator;
import java.util.List;

public final class RollingCounter {
    public static final long WINDOW_MS = 86_400_000L;
    public static final int CAP = 500;
    private static final long FUTURE_TOLERANCE_MS = 60_000L;

    private RollingCounter() {
    }

    public static void record(List<Long> stamps, long now) {
        prune(stamps, now);
        stamps.add(now);
        int excess = stamps.size() - CAP;
        if (excess > 0) {
            stamps.subList(0, excess).clear();
        }
    }

    public static int count(List<Long> stamps, long now) {
        prune(stamps, now);
        return stamps.size();
    }

    private static void prune(List<Long> stamps, long now) {
        for (Iterator<Long> it = stamps.iterator(); it.hasNext(); ) {
            long stamp = it.next();
            if (stamp <= now - WINDOW_MS || stamp > now + FUTURE_TOLERANCE_MS) {
                it.remove();
            }
        }
    }
}
