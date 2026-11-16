package com.hmdm.launcher.locker24;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.ui.MainActivity;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.util.RemoteLogger;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class KioskWatchdogService extends Service {
    public static final String ACTION_RECHECK = "com.hmdm.launcher.locker24.ACTION_RECHECK";

    private static final String CHANNEL_ID = "locker24_watchdog";
    private static final int NOTIFICATION_ID = 24024;
    private static final String SETTING_REBOOT_TIME = "reboot_time";
    private static final String SETTING_SECONDS = "kiosk_watchdog_seconds";
    private static final int LAUNCHER_IN_FRONT_LIMIT = 4;
    private static final long REPLACE_RECHECK_MS = 6000;
    private static final long HEALTH_BOOT_DELAY_MS = 60_000L;

    private HandlerThread thread;
    private Handler handler;
    private WatchdogPolicy policy;
    private long intervalMs = WatchdogPolicy.DEFAULT_SECONDS * 1000L;
    private boolean receiverRegistered;
    private boolean tickPending;
    private long lastRelaunchAt;
    private boolean lastKioskAppMissing;
    private String lastCappedValue;
    private boolean lockNeverEngagedLogged;
    private PowerManager.WakeLock wakeLock;
    private Boolean lastLockTaskLocked;
    private boolean powerReceiverRegistered;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestHealthReport("charging", HealthSchedule.TRIGGER_DEBOUNCE_MS);
        }
    };

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            tickPending = false;
            try {
                onTick();
            } catch (Throwable e) {
                safeLog(Const.LOG_WARN, "Kiosk watchdog tick failed: " + e);
            }
            Handler h = handler;
            if (h != null) {
                h.postDelayed(this, intervalMs);
                tickPending = true;
            }
        }
    };

    private final Runnable replaceRecheck = new Runnable() {
        @Override
        public void run() {
            try {
                Context ctx = getApplicationContext();
                if (!ProUtils.kioskModeRequired(ctx) || KioskState.isAdminUnlocked(ctx)) {
                    return;
                }
                if (ProUtils.isKioskModeRunning(ctx)) {
                    boolean handled = KioskState.markUpdateHandledIfEngaged(ctx, true);
                    safeLog(Const.LOG_DEBUG, "Kiosk app replace recheck: lock task engaged"
                            + (handled ? ", update handled" : ""));
                    return;
                }
                // Past this point only the watchdog tick (30-60 s) relaunches if that fresh start fails
                if (KioskStartPolicy.freshStartRecent(KioskState.getLastFreshStartAt(),
                        android.os.SystemClock.elapsedRealtime())) {
                    safeLog(Const.LOG_INFO, "Kiosk app replace recheck: lock task gone, but a fresh start is "
                            + "under way, not relaunching");
                    return;
                }
                safeLog(Const.LOG_WARN, "Kiosk app replace recheck: lock task gone, relaunching launcher");
                relaunch();
            } catch (Throwable e) {
                safeLog(Const.LOG_WARN, "Kiosk app replace recheck failed: " + e);
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                        && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return;
                }
                Uri data = intent.getData();
                String pkg = data != null ? data.getSchemeSpecificPart() : null;
                if (pkg == null || !pkg.equals(mainApp())) {
                    return;
                }
                if (!ProUtils.kioskModeRequired(context) || KioskState.isAdminUnlocked(context)) {
                    return;
                }
                KioskStartPolicy.ReplaceAction action = KioskStartPolicy.onKioskAppReplaced(
                        ProUtils.isKioskModeRunning(context), KioskState.getLastFreshStartAt(),
                        android.os.SystemClock.elapsedRealtime());
                if (action == KioskStartPolicy.ReplaceAction.RELAUNCH) {
                    safeLog(Const.LOG_INFO, "Kiosk app " + pkg + " changed, relaunching launcher");
                    relaunch();
                    return;
                }
                if (action == KioskStartPolicy.ReplaceAction.RECHECK_LOCKED) {
                    // Lock task can still read LOCKED for a moment after the locked package is replaced
                    safeLog(Const.LOG_INFO, "Kiosk app " + pkg + " changed while lock task reads locked, rechecking in "
                            + (REPLACE_RECHECK_MS / 1000) + " s");
                } else {
                    safeLog(Const.LOG_INFO, "Kiosk app " + pkg + " changed right after a fresh start, not relaunching, "
                            + "rechecking in " + (REPLACE_RECHECK_MS / 1000) + " s");
                }
                Handler h = handler;
                if (h != null) {
                    h.removeCallbacks(replaceRecheck);
                    h.postDelayed(replaceRecheck, REPLACE_RECHECK_MS);
                }
            } catch (Throwable e) {
                safeLog(Const.LOG_WARN, "Kiosk package receiver failed: " + e);
            }
        }
    };

    public static void start(Context context, String action) {
        try {
            if (!Utils.isDeviceOwner(context)) {
                return;
            }
            Intent intent = new Intent(context, KioskWatchdogService.class);
            if (action != null) {
                intent.setAction(action);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable e) {
            try {
                RemoteLogger.log(context, Const.LOG_WARN, "Cannot start kiosk watchdog: " + e);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        policy = new WatchdogPolicy(LAUNCHER_IN_FRONT_LIMIT);
        LauncherLifecycle.ensureRegistered(getApplication());
        thread = new HandlerThread("locker24-watchdog");
        thread.start();
        handler = new Handler(thread.getLooper());
        registerPackageReceiver();
        registerHealthTriggers();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locker24:watchdog");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Cannot take watchdog wake lock: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            enterForeground();
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Kiosk watchdog startForeground failed: " + e);
        }
        if (!Utils.isDeviceOwner(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        final Handler h = handler;
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    long newInterval = readIntervalMs();
                    boolean changed = newInterval != intervalMs;
                    intervalMs = newInterval;
                    if (changed || !tickPending) {
                        h.removeCallbacks(tickRunnable);
                        h.postDelayed(tickRunnable, intervalMs);
                        tickPending = true;
                    }
                }
            });
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
        wakeLock = null;
        unregisterHealthTriggers();
        if (receiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Runs on the watchdog thread once per interval. */
    protected void onTick() {
        try {
            watchdogTick();
        } finally {
            try {
                checkScheduledReboot();
            } catch (Throwable e) {
                safeLog(Const.LOG_WARN, "Scheduled reboot check failed: " + e);
            } finally {
                try {
                    HealthReporter.onTick(getApplicationContext(), intervalMs);
                } catch (Throwable e) {
                    safeLog(Const.LOG_WARN, "Health tick failed: " + e);
                }
            }
        }
    }

    private void checkScheduledReboot() {
        Context ctx = getApplicationContext();
        String configured = SettingsHelper.getInstance(ctx).getAppPreference(ctx.getPackageName(), SETTING_REBOOT_TIME);
        int[] at = RebootSchedule.parse(configured);
        if (at == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate last = RebootSchedule.lastRebootDateOrNull(KioskState.getLastRebootDate(ctx));
        if (!RebootSchedule.isDue(now, at, last, android.os.SystemClock.elapsedRealtime())) {
            return;
        }
        if (!Utils.isDeviceOwner(ctx)) {
            return;
        }
        if (!KioskState.setLastRebootDate(ctx, now.toLocalDate().toString())) {
            safeLog(Const.LOG_WARN, "Scheduled reboot skipped: cannot persist last reboot date");
            return;
        }
        safeLog(Const.LOG_INFO, "Scheduled reboot");
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            dpm.reboot(LegacyUtils.getAdminComponentName(ctx));
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Scheduled reboot failed: " + e);
        }
    }

    private void watchdogTick() {
        Context ctx = getApplicationContext();
        boolean required = ProUtils.kioskModeRequired(ctx);
        boolean adminUnlocked = KioskState.isAdminUnlocked(ctx);
        boolean lockTaskLocked = ProUtils.isKioskModeRunning(ctx);
        if (lastLockTaskLocked != null && lastLockTaskLocked != lockTaskLocked) {
            requestHealthReport("kiosk", HealthSchedule.KIOSK_DEBOUNCE_MS);
        }
        lastLockTaskLocked = lockTaskLocked;
        boolean launcherInFront = isLauncherInFront();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean interactive = pm == null || pm.isInteractive();
        WatchdogPolicy.Action action = policy.evaluate(required, adminUnlocked, lockTaskLocked, launcherInFront,
                interactive);
        if (policy.isLockLostBackedOff()) {
            if (!lockNeverEngagedLogged) {
                lockNeverEngagedLogged = true;
                safeLog(Const.LOG_ERROR, "Kiosk lock never engaged after "
                        + WatchdogPolicy.LOCK_LOST_RELAUNCH_LIMIT + " attempts");
            }
        } else {
            lockNeverEngagedLogged = false;
        }
        if (action != WatchdogPolicy.Action.RELAUNCH) {
            return;
        }
        if (!lockTaskLocked) {
            boolean missing = !ProUtils.isKioskAppInstalled(ctx);
            if (missing != lastKioskAppMissing) {
                lastKioskAppMissing = missing;
                safeLog(Const.LOG_INFO, missing ? "Kiosk watchdog paused: kiosk app not installed"
                        : "Kiosk watchdog resumed: kiosk app installed");
            }
            if (missing) {
                policy.onRelaunchSkipped();
                return;
            }
        } else if (ctx.getPackageName().equals(mainApp())) {
            return;
        }
        safeLog(Const.LOG_WARN, "Kiosk watchdog relaunching launcher (lockTask=" + lockTaskLocked
                + ", inFront=" + launcherInFront + ")");
        if (relaunch()) {
            try {
                HealthCounters.recordWatchdogRestart(ctx);
            } catch (Throwable e) {
                safeLog(Const.LOG_WARN, "Cannot record watchdog restart: " + e);
            }
        }
    }

    /** Returns false when a relaunch less than 5 s ago makes this one a no-op. */
    private boolean relaunch() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastRelaunchAt != 0 && now - lastRelaunchAt < 5000) {
            return false;
        }
        lastRelaunchAt = now;
        try {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getApplicationContext().startActivity(intent);
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Kiosk watchdog cannot start launcher: " + e);
        }
        return true;
    }

    /**
     * True only while the launcher's MainActivity is resumed, as reported by activity
     * lifecycle callbacks. The process importance is not used: a top app bound to one
     * of our services also makes our process IMPORTANCE_FOREGROUND, which would make
     * the watchdog relaunch the launcher over it every few minutes. When the callbacks
     * are not registered the answer is false, so the in-front path cannot misfire.
     */
    private boolean isLauncherInFront() {
        return LauncherLifecycle.isRegistered() && KioskState.isLauncherResumed();
    }

    private String mainApp() {
        ServerConfig config = SettingsHelper.getInstance(getApplicationContext()).getConfig();
        return config != null ? config.getMainApp() : null;
    }

    private long readIntervalMs() {
        String value = null;
        try {
            value = SettingsHelper.getInstance(getApplicationContext())
                    .getAppPreference(getPackageName(), SETTING_SECONDS);
        } catch (Throwable ignored) {
        }
        int seconds = WatchdogPolicy.intervalSeconds(value);
        try {
            if (value != null && Integer.parseInt(value.trim()) > WatchdogPolicy.MAX_SECONDS
                    && !value.trim().equals(lastCappedValue)) {
                lastCappedValue = value.trim();
                safeLog(Const.LOG_WARN, SETTING_SECONDS + "=" + lastCappedValue + " capped to "
                        + WatchdogPolicy.MAX_SECONDS + " so the reboot window is not missed");
            }
        } catch (NumberFormatException ignored) {
        }
        return seconds * 1000L;
    }

    private void registerPackageReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, filter, null, handler, Context.RECEIVER_EXPORTED);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(packageReceiver, filter, null, handler, 0);
            } else {
                registerReceiver(packageReceiver, filter, null, handler);
            }
            receiverRegistered = true;
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Cannot register package receiver: " + e);
        }
    }

    private void registerHealthTriggers() {
        if (!HealthReporter.isEnabled()) {
            return;
        }
        requestHealthReport("boot", HEALTH_BOOT_DELAY_MS);
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(powerReceiver, filter, null, handler, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(powerReceiver, filter, null, handler);
            }
            powerReceiverRegistered = true;
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Cannot register power receiver: " + e);
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        requestHealthReport("network", HealthSchedule.TRIGGER_DEBOUNCE_MS);
                    }

                    @Override
                    public void onLost(Network network) {
                        requestHealthReport("network", HealthSchedule.TRIGGER_DEBOUNCE_MS);
                    }
                };
                cm.registerDefaultNetworkCallback(callback, handler);
                networkCallback = callback;
            }
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Cannot register network callback: " + e);
        }
    }

    private void unregisterHealthTriggers() {
        if (powerReceiverRegistered) {
            try {
                unregisterReceiver(powerReceiver);
            } catch (Throwable ignored) {
            }
            powerReceiverRegistered = false;
        }
        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Throwable ignored) {
            }
            networkCallback = null;
        }
    }

    private void requestHealthReport(String trigger, long delayMs) {
        try {
            HealthReporter.requestSoon(getApplicationContext(), trigger, delayMs);
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Health request failed: " + e);
        }
    }

    private void enterForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Kiosk watchdog",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("Locker24 MDM")
                .setOngoing(true)
                .build();
        Utils.startStableForegroundService(this, NOTIFICATION_ID, notification);
    }

    private void safeLog(int level, String message) {
        try {
            RemoteLogger.log(getApplicationContext(), level, message);
        } catch (Throwable ignored) {
        }
    }
}
