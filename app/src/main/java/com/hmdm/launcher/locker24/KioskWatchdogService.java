package com.hmdm.launcher.locker24;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;

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
import java.util.List;

public class KioskWatchdogService extends Service {
    public static final String ACTION_RECHECK = "com.hmdm.launcher.locker24.ACTION_RECHECK";

    private static final String CHANNEL_ID = "locker24_watchdog";
    private static final int NOTIFICATION_ID = 24024;
    private static final String SETTING_REBOOT_TIME = "reboot_time";
    private static final String SETTING_SECONDS = "kiosk_watchdog_seconds";
    private static final int LAUNCHER_IN_FRONT_LIMIT = 4;

    private HandlerThread thread;
    private Handler handler;
    private WatchdogPolicy policy;
    private long intervalMs = WatchdogPolicy.DEFAULT_SECONDS * 1000L;
    private boolean receiverRegistered;
    private boolean tickPending;
    private long lastRelaunchAt;
    private boolean lastKioskAppMissing;
    private String lastCappedValue;
    private PowerManager.WakeLock wakeLock;

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
                if (ProUtils.kioskModeRequired(context) && !KioskState.isAdminUnlocked(context)
                        && !ProUtils.isKioskModeRunning(context)) {
                    safeLog(Const.LOG_INFO, "Kiosk app " + pkg + " changed, relaunching launcher");
                    relaunch();
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
        thread = new HandlerThread("locker24-watchdog");
        thread.start();
        handler = new Handler(thread.getLooper());
        registerPackageReceiver();
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
        boolean launcherInFront = isLauncherInFront();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean interactive = pm == null || pm.isInteractive();
        WatchdogPolicy.Action action = policy.evaluate(required, adminUnlocked, lockTaskLocked, launcherInFront,
                interactive);
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
                return;
            }
        } else if (ctx.getPackageName().equals(mainApp())) {
            return;
        }
        safeLog(Const.LOG_WARN, "Kiosk watchdog relaunching launcher (lockTask=" + lockTaskLocked
                + ", inFront=" + launcherInFront + ")");
        relaunch();
    }

    private void relaunch() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastRelaunchAt != 0 && now - lastRelaunchAt < 5000) {
            return;
        }
        lastRelaunchAt = now;
        try {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getApplicationContext().startActivity(intent);
        } catch (Throwable e) {
            safeLog(Const.LOG_WARN, "Kiosk watchdog cannot start launcher: " + e);
        }
    }

    private boolean isLauncherInFront() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) {
            return false;
        }
        int myPid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pid == myPid) {
                return p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
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
