package com.hmdm.launcher.locker24;

import android.content.Context;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.RemoteLogger;

public class Locker24Hooks {

    public static void onConfig(Context context, ServerConfig config) {
        try {
            if (!BuildConfig.FLAVOR.equals("locker24")) {
                return;
            }
            KioskWatchdogService.start(context, KioskWatchdogService.ACTION_RECHECK);
        } catch (Throwable e) {
            try {
                RemoteLogger.log(context, Const.LOG_WARN, "Locker24 config hook failed: " + e);
            } catch (Throwable ignored) {
            }
        }
    }
}
