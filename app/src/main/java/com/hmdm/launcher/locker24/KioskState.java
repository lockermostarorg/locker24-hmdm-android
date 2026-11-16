package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;

public class KioskState {
    private static final String PREFS = "locker24";
    private static final String KEY_ADMIN_UNLOCKED = "admin_unlocked";
    private static final String KEY_LAST_REBOOT_DATE = "last_reboot_date";
    private static final String KEY_KIOSK_APP_REPLACED_AT = "kiosk_app_replaced_at";

    private static volatile boolean launcherResumed;

    public static boolean isLauncherResumed() {
        return launcherResumed;
    }

    static void setLauncherResumed(boolean resumed) {
        launcherResumed = resumed;
    }

    public static boolean isAdminUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_ADMIN_UNLOCKED, false);
    }

    public static void setAdminUnlocked(Context context, boolean unlocked) {
        prefs(context).edit().putBoolean(KEY_ADMIN_UNLOCKED, unlocked).commit();
    }

    public static String getLastRebootDate(Context context) {
        return prefs(context).getString(KEY_LAST_REBOOT_DATE, null);
    }

    public static boolean setLastRebootDate(Context context, String isoDate) {
        return prefs(context).edit().putString(KEY_LAST_REBOOT_DATE, isoDate).commit();
    }

    /** elapsedRealtime of the last install or replacement of the kiosk app, 0 when none is pending. */
    public static long getKioskAppReplacedAt(Context context) {
        return prefs(context).getLong(KEY_KIOSK_APP_REPLACED_AT, 0);
    }

    public static synchronized void markKioskAppReplaced(Context context, long elapsedRealtime) {
        prefs(context).edit().putLong(KEY_KIOSK_APP_REPLACED_AT, elapsedRealtime).commit();
    }

    /** Clears the mark only if it was not refreshed since {@code expected} was read. */
    public static synchronized boolean clearKioskAppReplaced(Context context, long expected) {
        if (getKioskAppReplacedAt(context) != expected) {
            return false;
        }
        return prefs(context).edit().remove(KEY_KIOSK_APP_REPLACED_AT).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
