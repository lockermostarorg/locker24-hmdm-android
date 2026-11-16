package com.hmdm.launcher.locker24;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.RemoteLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Collection runs on one process-wide thread, "locker24-health", so two collections never
 * overlap and the single queue instance guards the single queue file.
 */
public final class HealthReporter {
    public static final String SETTING_INTERVAL = "health_interval_minutes";
    public static final String TRIGGER_SCHEDULE = "schedule";

    private static final long DEFAULT_TICK_MS = WatchdogPolicy.DEFAULT_SECONDS * 1000L;
    private static final Object REQUEST_TOKEN = new Object();
    static final long ERROR_LOG_INTERVAL_MS = 3_600_000L;

    private static final Object LOCK = new Object();
    private static final Object HANDLER_LOCK = new Object();
    private static final Object QUEUE_LOCK = new Object();
    private static final Map<String, Long> ERRORS_LOGGED = new HashMap<>();

    private static Handler handler;
    private static HealthQueue queue;
    private static String pendingTrigger;
    private static Object pendingOwner;

    private HealthReporter() {
    }

    public static boolean isEnabled() {
        return "locker24".equals(BuildConfig.FLAVOR);
    }

    /** Collects a scheduled report when one is due. */
    public static void onTick(Context context) {
        onTick(context, DEFAULT_TICK_MS);
    }

    /** Collects a scheduled report when one is due; {@code tickMs} is how often the caller ticks. */
    public static void onTick(Context context, long tickMs) {
        if (!isEnabled()) {
            return;
        }
        final Context ctx = context.getApplicationContext();
        handler().post(() -> tickNow(ctx, tickMs));
    }

    /** Runs the due check on the health thread and waits for it, at most {@code timeoutMs}. */
    static void onTickAndWait(Context context, long tickMs, long timeoutMs) throws InterruptedException {
        if (!isEnabled()) {
            return;
        }
        final Context ctx = context.getApplicationContext();
        final CountDownLatch done = new CountDownLatch(1);
        handler().post(() -> {
            try {
                tickNow(ctx, tickMs);
            } finally {
                done.countDown();
            }
        });
        done.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Collects a report after {@code delayMs}. A new request replaces the pending one; while a
     * boot request is pending, the replacement keeps the name boot.
     */
    public static void requestSoon(Context context, String trigger, long delayMs) {
        requestSoon(context, trigger, delayMs, null);
    }

    /** Same as {@link #requestSoon(Context, String, long)}, remembering {@code owner} for {@link #cancelRequest}. */
    static void requestSoon(Context context, String trigger, long delayMs, Object owner) {
        if (!isEnabled()) {
            return;
        }
        final Context ctx = context.getApplicationContext();
        Handler h = handler();
        synchronized (LOCK) {
            pendingTrigger = HealthSchedule.mergeTrigger(pendingTrigger, trigger);
            pendingOwner = owner;
            h.removeCallbacksAndMessages(REQUEST_TOKEN);
            h.postAtTime(() -> {
                String name;
                synchronized (LOCK) {
                    name = pendingTrigger;
                    pendingTrigger = null;
                    pendingOwner = null;
                }
                collect(ctx, name != null ? name : trigger);
            }, REQUEST_TOKEN, SystemClock.uptimeMillis() + Math.max(0, delayMs));
        }
    }

    /** Removes the pending request when {@code owner} made the latest one. */
    static void cancelRequest(Object owner) {
        if (owner == null) {
            return;
        }
        synchronized (LOCK) {
            if (pendingOwner != owner) {
                return;
            }
            Handler h;
            synchronized (HANDLER_LOCK) {
                h = handler;
            }
            if (h != null) {
                h.removeCallbacksAndMessages(REQUEST_TOKEN);
            }
            pendingTrigger = null;
            pendingOwner = null;
        }
    }

    /** Collects a report now, on the health thread. */
    public static void collectNow(Context context, String trigger) {
        if (!isEnabled()) {
            return;
        }
        final Context ctx = context.getApplicationContext();
        handler().post(() -> collect(ctx, trigger));
    }

    static HealthQueue queue(Context context) {
        synchronized (QUEUE_LOCK) {
            if (queue == null) {
                queue = new HealthQueue(FileQueueStore.forContext(context), HealthQueue.MAX);
            }
            return queue;
        }
    }

    private static Handler handler() {
        synchronized (HANDLER_LOCK) {
            if (handler == null) {
                HandlerThread thread = new HandlerThread("locker24-health");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            return handler;
        }
    }

    private static void tickNow(Context ctx, long tickMs) {
        try {
            String setting = SettingsHelper.getInstance(ctx).getAppPreference(ctx.getPackageName(), SETTING_INTERVAL);
            int interval = HealthSchedule.intervalMinutes(setting);
            long now = System.currentTimeMillis();
            if (HealthSchedule.isDue(now, HealthCounters.getLastCollectAt(ctx), interval,
                    HealthSchedule.toleranceMs(tickMs))) {
                collect(ctx, TRIGGER_SCHEDULE);
            }
        } catch (Throwable e) {
            logError(ctx, "Health tick failed: ", e);
        }
    }

    private static void collect(Context ctx, String trigger) {
        try {
            HealthSnapshot snapshot = HealthCollector.collect(ctx, trigger);
            String report = HealthReport.toJsonString(snapshot);
            try {
                queue(ctx).add(report);
            } catch (RuntimeException e) {
                log(ctx, Const.LOG_WARN, "Health report dropped, queue not saved: " + e.getMessage());
            }
            HealthCounters.setLastCollectAt(ctx, System.currentTimeMillis());
            HealthUploadWorker.enqueue(ctx);
        } catch (Throwable e) {
            logError(ctx, "Health collect failed: ", e);
        }
    }

    private static void logError(Context ctx, String prefix, Throwable e) {
        boolean log;
        synchronized (ERRORS_LOGGED) {
            log = shouldLog(ERRORS_LOGGED, prefix + e.getClass().getName(), System.currentTimeMillis());
        }
        if (log) {
            log(ctx, Const.LOG_WARN, prefix + e);
        }
    }

    static boolean shouldLog(Map<String, Long> last, String key, long now) {
        Long prev = last.get(key);
        if (prev != null && now >= prev && now - prev < ERROR_LOG_INTERVAL_MS) {
            return false;
        }
        last.put(key, now);
        return true;
    }

    private static void log(Context ctx, int level, String message) {
        try {
            RemoteLogger.log(ctx, level, message);
        } catch (Throwable ignored) {
        }
    }
}
