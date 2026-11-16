package com.hmdm.launcher.locker24;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.util.RemoteLogger;

/** The process-wide ConfigFlowGate for ConfigUpdater flows. */
public final class ConfigFlows {
    private static final Object BACKGROUND = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class Request {
        final ConfigUpdater updater;
        final Context context;
        final ConfigUpdater.UINotifier notifier;
        final boolean userInteraction;
        final boolean skipLoad;

        Request(ConfigUpdater updater, Context context, ConfigUpdater.UINotifier notifier, boolean userInteraction,
                boolean skipLoad) {
            this.updater = updater;
            this.context = context;
            this.notifier = notifier;
            this.userInteraction = userInteraction;
            this.skipLoad = skipLoad;
        }
    }

    private static final ConfigFlowGate<Request> GATE = new ConfigFlowGate<>(new ConfigFlowGate.Requests<Request>() {
        @Override
        public Object flow(Request request) {
            return request.updater;
        }

        @Override
        public Object key(Request request) {
            return request.notifier != null ? request.notifier : BACKGROUND;
        }

        @Override
        public Request merge(Request pending, Request next) {
            return new Request(next.updater, next.context, next.notifier,
                    pending.userInteraction || next.userInteraction, pending.skipLoad && next.skipLoad);
        }

        @Override
        public boolean listenerGone(Request request) {
            return isGone(request.notifier);
        }
    });

    private ConfigFlows() {
    }

    public static boolean isGone(Object listener) {
        return listener instanceof Activity && ((Activity) listener).isDestroyed();
    }

    /** Returns true when the caller runs its flow now, false when the request waits for the running flow. */
    public static boolean begin(ConfigUpdater updater, Context context, ConfigUpdater.UINotifier notifier,
                                boolean userInteraction) {
        return begin(updater, context, notifier, userInteraction, false);
    }

    /** As begin(), for a flow that continues with the stored configuration instead of loading it. */
    public static boolean beginSkippingLoad(ConfigUpdater updater, Context context,
                                            ConfigUpdater.UINotifier notifier, boolean userInteraction) {
        return begin(updater, context, notifier, userInteraction, true);
    }

    private static boolean begin(ConfigUpdater updater, Context context, ConfigUpdater.UINotifier notifier,
                                 boolean userInteraction, boolean skipLoad) {
        final Request request = new Request(updater, context, notifier, userInteraction, skipLoad);
        ConfigFlowGate.Decision decision = GATE.request(request, SystemClock.elapsedRealtime());
        if (decision != ConfigFlowGate.Decision.COALESCE) {
            MAIN.postDelayed(() -> expire(request), ConfigFlowGate.STALE_MS);
        }
        switch (decision) {
            case COALESCE:
                log(context, Const.LOG_DEBUG, "Config update requested while another one runs, it runs again afterwards");
                return false;
            case TAKEOVER_STALE:
                log(context, Const.LOG_WARN, "Config update starts over a flow running for more than "
                        + (ConfigFlowGate.STALE_MS / 60_000) + " min");
                return true;
            case TAKEOVER_LISTENER_GONE:
                log(context, Const.LOG_INFO, "Config update takes over a flow of a destroyed launcher instance");
                return true;
            default:
                return true;
        }
    }

    /** Ends the updater's flow and starts a request made while it ran. */
    public static void end(ConfigUpdater updater) {
        rerun(GATE.finish(updater));
    }

    private static void expire(Request request) {
        if (!GATE.expire(request)) {
            return;
        }
        log(request.context, Const.LOG_WARN, "Config update flow did not finish within "
                + (ConfigFlowGate.STALE_MS / 60_000) + " min, releasing it");
        rerun(GATE.takeNext());
    }

    private static void rerun(final Request next) {
        if (next == null) {
            return;
        }
        MAIN.post(() -> {
            try {
                if (next.context == null) {
                    Log.w(Const.LOG_TAG, "Queued config update dropped: no context");
                    return;
                }
                log(next.context, Const.LOG_DEBUG, "Config update runs again for a request made during the previous one");
                if (isGone(next.notifier)) {
                    Context app = next.context.getApplicationContext();
                    new ConfigUpdater(app).updateConfig(app, null, next.userInteraction);
                } else if (next.skipLoad) {
                    next.updater.skipConfigLoad();
                } else {
                    next.updater.updateConfig(next.context, next.notifier, next.userInteraction);
                }
            } catch (RuntimeException e) {
                Log.w(Const.LOG_TAG, "Queued config update failed: " + e);
            }
        });
    }

    /** True when another flow has taken over: the updater's flow stops before the given step. */
    public static boolean superseded(ConfigUpdater updater, Context context, String step) {
        if (!GATE.superseded(updater)) {
            return false;
        }
        log(context, Const.LOG_INFO, "Config update flow superseded, stopping before " + step);
        return true;
    }

    private static void log(Context context, int level, String message) {
        try {
            RemoteLogger.log(context, level, message);
        } catch (RuntimeException ignored) {
        }
    }
}
