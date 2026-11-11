package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;

public class KioskState {
    private static final String PREFS = "locker24";
    private static final String KEY_ADMIN_UNLOCKED = "admin_unlocked";

    public static boolean isAdminUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_ADMIN_UNLOCKED, false);
    }

    public static void setAdminUnlocked(Context context, boolean unlocked) {
        prefs(context).edit().putBoolean(KEY_ADMIN_UNLOCKED, unlocked).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
