package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;

public class KioskState {
    private static final String PREFS = "locker24";
    private static final String KEY_ADMIN_UNLOCKED = "admin_unlocked";
    private static final String KEY_LAST_REBOOT_DATE = "last_reboot_date";
    private static final String KEY_HANDLED_KIOSK_UPDATE_TIME = "handled_kiosk_update_time";

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

    public static long getHandledKioskUpdateTime(Context context) {
        return prefs(context).getLong(KEY_HANDLED_KIOSK_UPDATE_TIME, 0);
    }

    public static void setHandledKioskUpdateTime(Context context, long lastUpdateTime) {
        prefs(context).edit().putLong(KEY_HANDLED_KIOSK_UPDATE_TIME, lastUpdateTime).commit();
    }

    /** PackageInfo.lastUpdateTime of the package, 0 when it is not installed. */
    public static long kioskAppLastUpdateTime(Context context, String packageName) {
        if (packageName == null) {
            return 0;
        }
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return 0;
        }
    }

    /** A reboot starts the kiosk app fresh anyway, so the current install needs no forced fresh start. */
    public static void markCurrentKioskUpdateHandled(Context context) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        String mainApp = config != null ? config.getMainApp() : null;
        long lastUpdateTime = kioskAppLastUpdateTime(context, mainApp);
        if (lastUpdateTime > 0) {
            setHandledKioskUpdateTime(context, lastUpdateTime);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
