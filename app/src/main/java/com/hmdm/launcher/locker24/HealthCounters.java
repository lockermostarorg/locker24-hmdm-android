package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class HealthCounters {
    private static final String PREFS = "locker24_health";
    private static final String KEY_WATCHDOG_RESTARTS = "watchdog_restarts";
    private static final String KEY_LAST_CONFIG_SYNC_AT = "last_config_sync_at";
    private static final String KEY_LAST_COLLECT_AT = "last_collect_at";

    private static final Object RESTARTS_LOCK = new Object();

    private HealthCounters() {
    }

    public static void recordWatchdogRestart(Context context) {
        synchronized (RESTARTS_LOCK) {
            SharedPreferences prefs = prefs(context);
            List<Long> stamps = parseStamps(prefs.getString(KEY_WATCHDOG_RESTARTS, null));
            RollingCounter.record(stamps, System.currentTimeMillis());
            prefs.edit().putString(KEY_WATCHDOG_RESTARTS, formatStamps(stamps)).apply();
        }
    }

    public static int watchdogRestarts24h(Context context) {
        synchronized (RESTARTS_LOCK) {
            List<Long> stamps = parseStamps(prefs(context).getString(KEY_WATCHDOG_RESTARTS, null));
            return RollingCounter.count(stamps, System.currentTimeMillis());
        }
    }

    public static void setLastConfigSyncAt(Context context, long at) {
        prefs(context).edit().putLong(KEY_LAST_CONFIG_SYNC_AT, at).apply();
    }

    /** 0 when no configuration sync has succeeded yet. */
    public static long getLastConfigSyncAt(Context context) {
        return prefs(context).getLong(KEY_LAST_CONFIG_SYNC_AT, 0);
    }

    public static void setLastCollectAt(Context context, long at) {
        prefs(context).edit().putLong(KEY_LAST_COLLECT_AT, at).apply();
    }

    /** 0 when nothing has been collected yet. */
    public static long getLastCollectAt(Context context) {
        return prefs(context).getLong(KEY_LAST_COLLECT_AT, 0);
    }

    static List<Long> parseStamps(String csv) {
        List<Long> stamps = new ArrayList<>();
        if (csv == null) {
            return stamps;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                stamps.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
            }
        }
        return stamps;
    }

    static String formatStamps(List<Long> stamps) {
        StringBuilder b = new StringBuilder();
        for (Long stamp : stamps) {
            if (stamp == null) {
                continue;
            }
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(stamp.longValue());
        }
        return b.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
