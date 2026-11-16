package com.hmdm.launcher.locker24;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Keeps reports coming when the watchdog service is not running. */
public class HealthSafetyNetWorker extends Worker {
    public static final String WORK_NAME = "locker24_health_safety_net";

    private static final long PERIOD_MINUTES = 15;
    private static final long WAIT_MS = 30_000L;

    public HealthSafetyNetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        if (!HealthReporter.isEnabled()) {
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(HealthSafetyNetWorker.class,
                PERIOD_MINUTES, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            HealthReporter.onTickAndWait(getApplicationContext(), TimeUnit.MINUTES.toMillis(PERIOD_MINUTES), WAIT_MS);
        } catch (Throwable ignored) {
        }
        return Result.success();
    }
}
