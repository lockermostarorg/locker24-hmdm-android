/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.pro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.locker24.KioskPolicy;
import com.hmdm.launcher.locker24.KioskState;
import com.hmdm.launcher.locker24.Locker24Hooks;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import java.util.Calendar;

/**
 * These functions are available in Pro-version only
 * In a free version, the class contains stubs
 */
public class ProUtils {

    public static boolean isPro() {
        return false;
    }

    public static boolean kioskModeRequired(Context context) {
        try {
            ServerConfig config = SettingsHelper.getInstance(context).getConfig();
            if (config == null) {
                return false;
            }
            return KioskPolicy.kioskRequired(config.getKioskMode(), config.getMainApp());
        } catch (RuntimeException e) {
            logError(context, "kioskModeRequired failed", e);
            return false;
        }
    }

    private static void logError(Context context, String message, Throwable e) {
        Log.e(Const.LOG_TAG, message + ": " + e);
        try {
            RemoteLogger.log(context, Const.LOG_ERROR, message + ": " + e);
        } catch (RuntimeException ignored) {
        }
    }

    private static DevicePolicyManager getDpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private static void releaseDevicePolicies(Context context) {
        DevicePolicyManager dpm = getDpm(context);
        ComponentName admin = LegacyUtils.getAdminComponentName(context);
        try {
            if (!dpm.setStatusBarDisabled(admin, false)) {
                Log.w(Const.LOG_TAG, "setStatusBarDisabled(false) returned false");
            }
        } catch (RuntimeException e) {
            logError(context, "release: setStatusBarDisabled failed", e);
        }
        try {
            if (!dpm.setKeyguardDisabled(admin, false)) {
                Log.w(Const.LOG_TAG, "setKeyguardDisabled(false) returned false");
            }
        } catch (RuntimeException e) {
            logError(context, "release: setKeyguardDisabled failed", e);
        }
        try {
            dpm.setLockTaskPackages(admin, new String[0]);
        } catch (RuntimeException e) {
            logError(context, "release: setLockTaskPackages failed", e);
        }
    }

    private static void applyLockTaskPackages(Context context, String app) {
        DevicePolicyManager dpm = getDpm(context);
        ComponentName admin = LegacyUtils.getAdminComponentName(context);
        String extra = SettingsHelper.getInstance(context)
                .getAppPreference(context.getPackageName(), "kiosk_extra_packages");
        dpm.setLockTaskPackages(admin, KioskPolicy.lockTaskPackages(context.getPackageName(), app, extra));
    }

    private static void applyLockTaskFeatures(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        if (config == null) {
            return;
        }
        int features = KioskPolicy.lockTaskFeatures(config.getKioskHome(), config.getKioskRecents(),
                config.getKioskNotifications(), config.getKioskSystemInfo(),
                config.getKioskKeyguard(), config.getKioskLockButtons());
        getDpm(context).setLockTaskFeatures(LegacyUtils.getAdminComponentName(context), features);
    }

    public static void initCrashlytics(Context context) {
        // Stub
    }

    public static void sendExceptionToCrashlytics(Throwable e) {
        // Stub
    }

    // Start the service checking if the foreground app is allowed to the user (by usage statistics)
    public static boolean checkAccessibilityService(Context context) {
        // Stub
        return true;
    }

    // Pro-version
    public static boolean checkUsageStatistics(Context context) {
        // Stub
        return true;
    }

    // Add a transparent view on top of the status bar which prevents user interaction with the status bar
    public static View preventStatusBarExpansion(Activity activity) {
        // Stub
        return null;
    }

    // Add a transparent view on top of a swipeable area at the right (opens app list on Samsung tablets)
    public static View preventApplicationsList(Activity activity) {
        // Stub
        return null;
    }

    public static View createKioskUnlockButton(Activity activity) {
        // Stub
        return null;
    }

    public static boolean isKioskAppInstalled(Context context) {
        try {
            ServerConfig config = SettingsHelper.getInstance(context).getConfig();
            if (config == null || config.getMainApp() == null) {
                return false;
            }
            return context.getPackageManager().getLaunchIntentForPackage(config.getMainApp()) != null;
        } catch (RuntimeException e) {
            logError(context, "isKioskAppInstalled failed", e);
            return false;
        }
    }

    public static boolean isKioskModeRunning(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED;
        } catch (RuntimeException e) {
            logError(context, "isKioskModeRunning failed", e);
            return false;
        }
    }

    public static Intent getKioskAppIntent(String kioskApp, Activity activity) {
        try {
            if (kioskApp == null) {
                return null;
            }
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(kioskApp);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            return intent;
        } catch (RuntimeException e) {
            logError(activity, "getKioskAppIntent failed", e);
            return null;
        }
    }

    // Start COSU kiosk mode
    public static boolean startCosuKioskMode(String kioskApp, Activity activity, boolean enableSettings) {
        if (!Utils.isDeviceOwner(activity)) {
            return false;
        }
        try {
            Intent intent = getKioskAppIntent(kioskApp, activity);
            if (intent == null) {
                return false;
            }
            applyLockTaskPackages(activity, kioskApp);
            updateKioskOptions(activity);
            ServerConfig config = SettingsHelper.getInstance(activity).getConfig();
            boolean keyguardAllowed = config != null && Boolean.TRUE.equals(config.getKioskKeyguard());
            DevicePolicyManager dpm = getDpm(activity);
            ComponentName admin = LegacyUtils.getAdminComponentName(activity);
            if (!dpm.setKeyguardDisabled(admin, !keyguardAllowed)) {
                Log.w(Const.LOG_TAG, "setKeyguardDisabled returned false");
                RemoteLogger.log(activity, Const.LOG_WARN, "setKeyguardDisabled returned false");
            }
            if (!dpm.setStatusBarDisabled(admin, true)) {
                Log.w(Const.LOG_TAG, "setStatusBarDisabled returned false");
                RemoteLogger.log(activity, Const.LOG_WARN, "setStatusBarDisabled returned false");
            }
            if (!isKioskModeRunning(activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            KioskState.setAdminUnlocked(activity, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.startActivity(intent, ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle());
            } else {
                // API 26-27 cannot start a task in lock task mode via ActivityOptions;
                // start the app and rely on the allowlist only.
                activity.startActivity(intent);
            }
            final Context appContext = activity.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isKioskModeRunning(appContext)) {
                    Log.w(Const.LOG_TAG, "Lock task not engaged after kiosk start");
                    RemoteLogger.log(appContext, Const.LOG_WARN, "Lock task not engaged after kiosk start");
                }
            }, 1000);
            return true;
        } catch (RuntimeException e) {
            logError(activity, "startCosuKioskMode failed", e);
            return false;
        }
    }

    // Set/update kiosk mode options (lock tack features)
    public static void updateKioskOptions(Activity activity) {
        if (!Utils.isDeviceOwner(activity)) {
            return;
        }
        try {
            applyLockTaskFeatures(activity);
        } catch (RuntimeException e) {
            logError(activity, "updateKioskOptions failed", e);
        }
    }

    // Update app list in the kiosk mode
    public static void updateKioskAllowedApps(String kioskApp, Activity activity, boolean enableSettings) {
        if (!Utils.isDeviceOwner(activity)) {
            return;
        }
        try {
            applyLockTaskPackages(activity, kioskApp);
        } catch (RuntimeException e) {
            logError(activity, "updateKioskAllowedApps failed", e);
        }
    }

    public static void unlockKiosk(Activity activity) {
        if (!Utils.isDeviceOwner(activity)) {
            return;
        }
        try {
            KioskState.setAdminUnlocked(activity, true);
        } catch (RuntimeException e) {
            logError(activity, "unlockKiosk failed", e);
        }
        releaseDevicePolicies(activity);
        try {
            activity.stopLockTask();
        } catch (RuntimeException e) {
            logError(activity, "stopLockTask failed", e);
        }
    }

    public static void processConfig(Context context, ServerConfig config) {
        try {
            if (Utils.isDeviceOwner(context) && !kioskModeRequired(context) && !isKioskModeRunning(context)) {
                releaseDevicePolicies(context);
            }
        } catch (RuntimeException e) {
            logError(context, "processConfig release failed", e);
        }
        try {
            Locker24Hooks.onConfig(context, config);
        } catch (RuntimeException e) {
            logError(context, "processConfig failed", e);
        }
    }

    public static void processLocation(Context context, Location location, String provider) {
        // Stub    
    }

    public static String getAppName(Context context) {
        return context.getString(R.string.app_name);
    }

    public static String getCopyright(Context context) {
        return "(c) " + Calendar.getInstance().get(Calendar.YEAR) + " " + context.getString(R.string.vendor);
    }
}
