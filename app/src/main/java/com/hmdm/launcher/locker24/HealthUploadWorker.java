package com.hmdm.launcher.locker24;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.RemoteLogger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class HealthUploadWorker extends Worker {
    public static final String WORK_NAME = "locker24_health_upload";

    private static final long BACKOFF_SECONDS = 60;
    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public HealthUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        if (!HealthReporter.isEnabled()) {
            return;
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(HealthUploadWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!HealthReporter.isEnabled()) {
            return Result.success();
        }
        Context ctx = getApplicationContext();
        try {
            SettingsHelper settings = SettingsHelper.getInstance(ctx);
            String deviceId = settings.getDeviceId();
            String baseUrl = settings.getBaseUrl();
            if (deviceId == null || deviceId.trim().isEmpty() || baseUrl == null || baseUrl.trim().isEmpty()) {
                return Result.success();
            }
            String signature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + deviceId);
            HealthApi api = HealthApi.Factory.get(baseUrl);
            HealthQueue queue = HealthReporter.queue(ctx);
            boolean dropLogged = false;
            boolean sentThisRun = false;
            boolean lastWasRetry = HealthCounters.getLastUploadRetry(ctx);
            for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
                List<String> batch = queue.peek(
                        UploadOutcome.batchSize(getRunAttemptCount(), lastWasRetry, sentThisRun));
                if (batch.isEmpty()) {
                    return Result.success();
                }
                String body;
                try {
                    body = HealthReport.envelope(batch, System.currentTimeMillis());
                } catch (IllegalArgumentException e) {
                    queue.removeSent(batch);
                    log(ctx, Const.LOG_WARN, "Health upload: dropped " + batch.size() + " unreadable queued reports");
                    continue;
                }
                UploadOutcome.Result outcome;
                String message = null;
                try {
                    Response<ResponseBody> response = api.report(settings.getServerProject(), deviceId, signature,
                            RequestBody.create(body, JSON)).execute();
                    String text = null;
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        try {
                            text = responseBody.string();
                        } finally {
                            responseBody.close();
                        }
                    } else if (response.errorBody() != null) {
                        response.errorBody().close();
                    }
                    UploadOutcome.Body parsed = UploadOutcome.Body.parse(text);
                    message = parsed.message;
                    outcome = UploadOutcome.classify(response.code(), parsed.status, parsed.message);
                } catch (Exception e) {
                    outcome = UploadOutcome.Result.RETRY;
                }
                switch (outcome) {
                    case SENT:
                        if (!sentThisRun) {
                            sentThisRun = true;
                            HealthCounters.setLastUploadRetry(ctx, false);
                        }
                        queue.removeSent(batch);
                        break;
                    case DROP:
                        queue.removeSent(batch);
                        if (!dropLogged) {
                            dropLogged = true;
                            log(ctx, Const.LOG_WARN, "Health upload rejected (" + message + "), dropped "
                                    + batch.size() + " reports");
                        }
                        break;
                    default:
                        HealthCounters.setLastUploadRetry(ctx, true);
                        return retryOrStop();
                }
            }
            return queue.size() > 0 ? retryOrStop() : Result.success();
        } catch (Throwable e) {
            log(ctx, Const.LOG_WARN, "Health upload failed: " + e.getClass().getSimpleName());
            return retryOrStop();
        }
    }

    private Result retryOrStop() {
        return UploadOutcome.stopRetrying(getRunAttemptCount()) ? Result.success() : Result.retry();
    }

    private static void log(Context ctx, int level, String message) {
        try {
            RemoteLogger.log(ctx, level, message);
        } catch (Throwable ignored) {
        }
    }
}
