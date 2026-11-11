package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.admin.DevicePolicyManager;

import org.junit.Test;

public class KioskPolicyTest {
    @Test public void requiredOnlyWithModeAndApp() {
        assertTrue(KioskPolicy.kioskRequired(true, "com.hpmoqa.tablet_client"));
        assertFalse(KioskPolicy.kioskRequired(true, " "));
        assertFalse(KioskPolicy.kioskRequired(true, null));
        assertFalse(KioskPolicy.kioskRequired(true, ""));
        assertFalse(KioskPolicy.kioskRequired(null, "com.hpmoqa.tablet_client"));
        assertFalse(KioskPolicy.kioskRequired(false, "com.hpmoqa.tablet_client"));
    }

    @Test public void allOffBlocksEverythingButGlobalActionsUnlessLocked() {
        assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                KioskPolicy.lockTaskFeatures(false, false, false, false, false, false));
        assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                KioskPolicy.lockTaskFeatures(false, false, false, false, false, true));
    }

    @Test public void nullsCountAsFalse() {
        assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                KioskPolicy.lockTaskFeatures(null, null, null, null, null, null));
    }

    @Test public void recentsAndNotificationsNeedHome() {
        int f = KioskPolicy.lockTaskFeatures(false, true, true, false, false, true);
        assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_NONE, f);
        int g = KioskPolicy.lockTaskFeatures(true, true, true, true, true, true);
        assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_HOME | DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
                | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD, g);
    }

    @Test public void lockTaskPackagesKeepsMainAppEvenIfMissing() {
        assertArrayEquals(new String[]{"app.locker24.mdm", "com.hpmoqa.tablet_client", "com.hmdm.control"},
                KioskPolicy.lockTaskPackages("app.locker24.mdm", "com.hpmoqa.tablet_client", " com.hmdm.control, ,app.locker24.mdm"));
    }

    @Test public void lockTaskPackagesSkipsNullAndBlank() {
        assertArrayEquals(new String[]{"app.locker24.mdm"},
                KioskPolicy.lockTaskPackages("app.locker24.mdm", null, null));
        assertArrayEquals(new String[]{"app.locker24.mdm"},
                KioskPolicy.lockTaskPackages("app.locker24.mdm", "  ", ""));
        assertArrayEquals(new String[]{"app.locker24.mdm", "a.b"},
                KioskPolicy.lockTaskPackages("app.locker24.mdm", "", "a.b"));
    }
}
