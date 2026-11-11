package com.hmdm.launcher.locker24;

import android.app.admin.DevicePolicyManager;

import java.util.LinkedHashSet;
import java.util.Set;

public final class KioskPolicy {
    private KioskPolicy() {
    }

    public static boolean kioskRequired(Boolean kioskMode, String mainApp) {
        return Boolean.TRUE.equals(kioskMode) && mainApp != null && !mainApp.trim().isEmpty();
    }

    public static int lockTaskFeatures(Boolean home, Boolean recents, Boolean notifications,
                                       Boolean systemInfo, Boolean keyguard, Boolean lockButtons) {
        int features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
        boolean homeOn = Boolean.TRUE.equals(home);
        if (homeOn) {
            features |= DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
            if (Boolean.TRUE.equals(recents)) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW;
            }
            if (Boolean.TRUE.equals(notifications)) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
            }
        }
        if (Boolean.TRUE.equals(systemInfo)) {
            features |= DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
        }
        if (Boolean.TRUE.equals(keyguard)) {
            features |= DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
        }
        if (!Boolean.TRUE.equals(lockButtons)) {
            features |= DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
        }
        return features;
    }

    public static String[] lockTaskPackages(String launcherPackage, String mainApp, String extraCsv) {
        Set<String> packages = new LinkedHashSet<>();
        add(packages, launcherPackage);
        add(packages, mainApp);
        if (extraCsv != null) {
            for (String part : extraCsv.split(",")) {
                add(packages, part);
            }
        }
        return packages.toArray(new String[0]);
    }

    private static void add(Set<String> packages, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            packages.add(trimmed);
        }
    }
}
