package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class LauncherLifecycleTest {
    @Before public void reset() {
        KioskState.setLauncherResumed(false);
    }

    @Test public void launcherResumeAndPauseAreTracked() {
        LauncherLifecycle.onActivityEvent(LauncherLifecycle.LAUNCHER_ACTIVITY, true);
        assertTrue(KioskState.isLauncherResumed());
        LauncherLifecycle.onActivityEvent(LauncherLifecycle.LAUNCHER_ACTIVITY, false);
        assertFalse(KioskState.isLauncherResumed());
    }

    @Test public void otherActivitiesAreIgnored() {
        LauncherLifecycle.onActivityEvent("com.hmdm.launcher.ui.AdminActivity", true);
        assertFalse(KioskState.isLauncherResumed());
        LauncherLifecycle.onActivityEvent(LauncherLifecycle.LAUNCHER_ACTIVITY, true);
        LauncherLifecycle.onActivityEvent("com.hmdm.launcher.ui.AdminActivity", false);
        assertTrue(KioskState.isLauncherResumed());
    }

    @Test public void launcherClassNameMatchesTheRealActivity() {
        assertTrue(new java.io.File("src/main/java/"
                + LauncherLifecycle.LAUNCHER_ACTIVITY.replace('.', '/') + ".java").isFile());
    }
}
