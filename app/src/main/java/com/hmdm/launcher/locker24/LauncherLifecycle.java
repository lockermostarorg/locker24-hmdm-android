package com.hmdm.launcher.locker24;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether the launcher's MainActivity is resumed, so the kiosk watchdog
 * does not mistake "our process is foreground" (for example a top app bound to
 * one of our services) for "the launcher is in front".
 *
 * Registered once per process by {@link LauncherLifecycleProvider}, which runs
 * before Application.onCreate and therefore before any activity. The watchdog
 * service also calls {@link #ensureRegistered} as a fallback; if that is the first
 * registration, the flag starts as false (not in front) until the next resume.
 */
public final class LauncherLifecycle implements Application.ActivityLifecycleCallbacks {
    static final String LAUNCHER_ACTIVITY = "com.hmdm.launcher.ui.MainActivity";

    private static final AtomicBoolean registered = new AtomicBoolean(false);

    private LauncherLifecycle() {
    }

    public static void ensureRegistered(Application app) {
        if (app == null || !registered.compareAndSet(false, true)) {
            return;
        }
        try {
            app.registerActivityLifecycleCallbacks(new LauncherLifecycle());
        } catch (Throwable e) {
            registered.set(false);
        }
    }

    public static boolean isRegistered() {
        return registered.get();
    }

    static void onActivityEvent(String activityClassName, boolean resumed) {
        if (LAUNCHER_ACTIVITY.equals(activityClassName)) {
            KioskState.setLauncherResumed(resumed);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        onActivityEvent(activity.getClass().getName(), true);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        onActivityEvent(activity.getClass().getName(), false);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        onActivityEvent(activity.getClass().getName(), false);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }
}
