package com.hmdm.launcher.locker24;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Both methods mutate the given list: callers must own it and synchronize access.
 */
public final class RollingCounter {
    public static final long WINDOW_MS = 86_400_000L;
    public static final int CAP = 500;
    private static final long FUTURE_TOLERANCE_MS = 60_000L;

    private RollingCounter() {
    }

    public static void record(List<Long> stamps, long now) {
        prune(stamps, now);
        stamps.add(now);
        Collections.sort(stamps);
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
            Long boxed = it.next();
            if (boxed == null) {
                it.remove();
                continue;
            }
            long stamp = boxed;
            if (stamp <= now - WINDOW_MS || stamp > now + FUTURE_TOLERANCE_MS) {
                it.remove();
            }
        }
    }
}
