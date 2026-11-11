package com.hmdm.launcher.locker24;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.ui.MainActivity;
import com.hmdm.launcher.util.RemoteLogger;

public class SelfUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        try {
            RemoteLogger.log(context, Const.LOG_INFO, "Launcher updated, restarting");
        } catch (Throwable ignored) {
        }
        try {
            Intent main = new Intent(context, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(main);
        } catch (Throwable e) {
            try {
                RemoteLogger.log(context, Const.LOG_WARN, "Cannot restart launcher after update: " + e);
            } catch (Throwable ignored) {
            }
        }
        KioskWatchdogService.start(context, null);
    }
}
