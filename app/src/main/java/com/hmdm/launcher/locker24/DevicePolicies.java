package com.hmdm.launcher.locker24;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

public final class DevicePolicies {
    private static final String SETTING_STAY_ON = "stay_on_plugged";
    private static final String STAY_ON_ALL_POWER = "3";

    private DevicePolicies() {
    }

    public static void apply(Context context) {
        try {
            Context ctx = context.getApplicationContext();
            if (Utils.isDeviceOwner(ctx) && stayOnEnabled(ctx)) {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName admin = LegacyUtils.getAdminComponentName(ctx);
                    dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, STAY_ON_ALL_POWER);
                } catch (Throwable e) {
                    log(ctx, Const.LOG_WARN, "Cannot set stay-on-while-plugged: " + e);
                }
            }
            logBatteryOptimisations(ctx);
        } catch (Throwable e) {
            log(context, Const.LOG_WARN, "Device policies failed: " + e);
        }
    }

    private static boolean stayOnEnabled(Context ctx) {
        try {
            String value = SettingsHelper.getInstance(ctx).getAppPreference(ctx.getPackageName(), SETTING_STAY_ON);
            return value == null || !value.trim().equals("0");
        } catch (Throwable e) {
            return true;
        }
    }

    private static void logBatteryOptimisations(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return;
            }
            boolean agent = pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
            ServerConfig config = SettingsHelper.getInstance(ctx).getConfig();
            String mainApp = config != null ? config.getMainApp() : null;
            boolean kiosk = mainApp != null && pm.isIgnoringBatteryOptimizations(mainApp);
            log(ctx, Const.LOG_INFO, "battery_opt agent=" + agent + " kiosk=" + kiosk);
        } catch (Throwable e) {
            log(ctx, Const.LOG_WARN, "Battery optimisation check failed: " + e);
        }
    }

    private static void log(Context ctx, int level, String message) {
        try {
            RemoteLogger.log(ctx, level, message);
        } catch (Throwable ignored) {
        }
    }
}
