package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;

public class KioskState {
    private static final String PREFS = "locker24";
    private static final String KEY_ADMIN_UNLOCKED = "admin_unlocked";
    private static final String KEY_LAST_REBOOT_DATE = "last_reboot_date";
    private static final String KEY_HANDLED_KIOSK_UPDATE_TIME = "handled_kiosk_update_time";

    private static volatile boolean launcherResumed;
    private static volatile long lastFreshStartAt;

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

    /**
     * A reboot starts the kiosk app fresh anyway, so an install from before this boot needs no
     * forced fresh start. BOOT_COMPLETED can arrive late, so an install after the boot stays open.
     */
    public static void markCurrentKioskUpdateHandled(Context context) {
        long lastUpdateTime = kioskAppLastUpdateTime(context, mainApp(context));
        if (KioskStartPolicy.installPredatesBoot(lastUpdateTime, System.currentTimeMillis(),
                SystemClock.elapsedRealtime())) {
            setHandledKioskUpdateTime(context, lastUpdateTime);
        }
    }

    /** Marks the kiosk app's update handled when lock task has held well past it. Returns true if it did. */
    public static boolean markUpdateHandledIfEngaged(Context context, boolean lockTaskLocked) {
        long lastUpdateTime = kioskAppLastUpdateTime(context, mainApp(context));
        if (!KioskStartPolicy.engagedAnswersUpdate(lockTaskLocked, lastUpdateTime,
                getHandledKioskUpdateTime(context), System.currentTimeMillis())) {
            return false;
        }
        setHandledKioskUpdateTime(context, lastUpdateTime);
        return true;
    }

    public static long getLastFreshStartAt() {
        return lastFreshStartAt;
    }

    public static void setLastFreshStartAt(long elapsedRealtime) {
        lastFreshStartAt = elapsedRealtime;
    }

    private static String mainApp(Context context) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        return config != null ? config.getMainApp() : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
