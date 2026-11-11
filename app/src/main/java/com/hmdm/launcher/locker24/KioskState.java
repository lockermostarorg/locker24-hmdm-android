package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;

public class KioskState {
    private static final String PREFS = "locker24";
    private static final String KEY_ADMIN_UNLOCKED = "admin_unlocked";
    private static final String KEY_LAST_REBOOT_DATE = "last_reboot_date";

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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
