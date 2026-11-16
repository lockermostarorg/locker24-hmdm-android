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
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.locker24.KioskPolicy;
import com.hmdm.launcher.locker24.KioskStartPolicy;
import com.hmdm.launcher.locker24.KioskState;
import com.hmdm.launcher.locker24.Locker24Hooks;
import com.hmdm.launcher.locker24.OrientationPolicy;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import java.lang.ref.WeakReference;
import java.util.Calendar;

/**
 * These functions are available in Pro-version only
 * In a free version, the class contains stubs
 */
public class ProUtils {

    private static volatile long lastKioskRetryAt;

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

    private static boolean isLocker24() {
        return "locker24".equals(BuildConfig.FLAVOR);
    }

    /**
     * Requests the configured orientation on the launcher activity (locker24 flavor only).
     * Returns false when there is no configured orientation, so the caller keeps its default behaviour.
     */
    public static boolean applyConfiguredOrientation(Activity activity, String reason) {
        if (!isLocker24() || BuildConfig.DISABLE_ORIENTATION_LOCK) {
            return false;
        }
        try {
            ServerConfig config = SettingsHelper.getInstance(activity).getConfig();
            int target = OrientationPolicy.requestedOrientation(config != null ? config.getOrientation() : null);
            if (target == OrientationPolicy.NOT_CONFIGURED) {
                return false;
            }
            int current = activity.getRequestedOrientation();
            if (OrientationPolicy.needsRequest(current, target)) {
                activity.setRequestedOrientation(target);
                RemoteLogger.log(activity, Const.LOG_DEBUG, "Orientation applied (" + reason + "): "
                        + OrientationPolicy.name(target) + ", was " + OrientationPolicy.name(current)
                        + ", " + Utils.orientationDiag(activity));
            }
            return true;
        } catch (RuntimeException e) {
            logError(activity, "applyConfiguredOrientation failed", e);
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
            applyConfiguredOrientation(activity, "kiosk start");
            boolean kioskRunning = isKioskModeRunning(activity);
            long replacedAt = isLocker24() ? KioskState.getKioskAppReplacedAt(activity) : 0;
            long now = SystemClock.elapsedRealtime();
            boolean replaced = isLocker24() && KioskStartPolicy.replacedRecently(replacedAt, now);
            boolean clearTask = KioskStartPolicy.clearTask(kioskRunning, replaced);
            if (clearTask) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            RemoteLogger.log(activity, Const.LOG_DEBUG, "Kiosk start " + kioskApp + ": lockTaskRunning=" + kioskRunning
                    + ", clearTask=" + clearTask + ", " + Utils.orientationDiag(activity));
            if (replaced && kioskRunning) {
                RemoteLogger.log(activity, Const.LOG_INFO, "Kiosk start " + kioskApp + " forced fresh: app replaced "
                        + ((now - replacedAt) / 1000) + " s ago");
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
            scheduleEngageCheck(appContext, new WeakReference<>(activity), kioskApp, false,
                    clearTask ? replacedAt : 0);
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    RemoteLogger.log(appContext, Const.LOG_DEBUG, "Kiosk start +5s: lockTaskRunning="
                            + isKioskModeRunning(appContext) + ", " + Utils.orientationDiag(appContext)), 5000);
            return true;
        } catch (RuntimeException e) {
            logError(activity, "startCosuKioskMode failed", e);
            return false;
        }
    }

    private static void scheduleEngageCheck(final Context appContext, final WeakReference<Activity> activityRef,
                                            final String kioskApp, final boolean retried, final long replacedMark) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                boolean locked = isKioskModeRunning(appContext);
                if (!locked) {
                    String message = "Lock task not engaged after kiosk start" + (retried ? " retry" : "");
                    Log.w(Const.LOG_TAG, message);
                    RemoteLogger.log(appContext, Const.LOG_WARN, message);
                }
                if (!isLocker24()) {
                    return;
                }
                if (KioskStartPolicy.consumeReplacedMark(replacedMark, locked)
                        && KioskState.clearKioskAppReplaced(appContext, replacedMark)) {
                    RemoteLogger.log(appContext, Const.LOG_DEBUG, "Kiosk app replacement handled, lock task engaged");
                }
                long now = SystemClock.elapsedRealtime();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        && !kioskApp.equals(appContext.getPackageName())
                        && KioskStartPolicy.shouldRetry(retried, locked, kioskModeRequired(appContext),
                                KioskState.isAdminUnlocked(appContext), lastKioskRetryAt, now)) {
                    lastKioskRetryAt = now;
                    retryFreshKioskStart(appContext, activityRef, kioskApp);
                }
            } catch (RuntimeException e) {
                logError(appContext, "Kiosk engage check failed", e);
            }
        }, KioskStartPolicy.ENGAGE_CHECK_MS);
    }

    private static void retryFreshKioskStart(Context appContext, WeakReference<Activity> activityRef, String kioskApp) {
        Activity activity = activityRef.get();
        boolean useActivity = activity != null && !activity.isFinishing() && !activity.isDestroyed();
        Context starter = useActivity ? activity : appContext;
        Intent intent = starter.getPackageManager().getLaunchIntentForPackage(kioskApp);
        if (intent == null) {
            RemoteLogger.log(appContext, Const.LOG_WARN, "Kiosk start retry skipped: no launch intent for " + kioskApp);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (useActivity) {
            applyConfiguredOrientation(activity, "kiosk retry");
        }
        RemoteLogger.log(appContext, Const.LOG_WARN, "Kiosk start retry: starting " + kioskApp
                + " fresh (from " + (useActivity ? "launcher" : "service context") + ", "
                + Utils.orientationDiag(appContext) + ")");
        starter.startActivity(intent, ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle());
        scheduleEngageCheck(appContext, activityRef, kioskApp, true, KioskState.getKioskAppReplacedAt(appContext));
    }

    /**
     * Called when the kiosk app is about to be installed, or was installed or replaced:
     * the next kiosk start must not bring the old task forward (locker24 flavor only).
     */
    public static void onKioskAppInstallEvent(Context context, String packageName, String event) {
        if (!isLocker24() || packageName == null) {
            return;
        }
        try {
            ServerConfig config = SettingsHelper.getInstance(context).getConfig();
            if (config == null || !packageName.equals(config.getMainApp())) {
                return;
            }
            KioskState.markKioskAppReplaced(context, SystemClock.elapsedRealtime());
            RemoteLogger.log(context, Const.LOG_INFO, "Kiosk app " + packageName + " " + event
                    + ": next kiosk start will be fresh");
        } catch (RuntimeException e) {
            logError(context, "onKioskAppInstallEvent failed", e);
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
