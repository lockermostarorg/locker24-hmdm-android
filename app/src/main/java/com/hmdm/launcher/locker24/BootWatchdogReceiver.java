package com.hmdm.launcher.locker24;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootWatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        KioskWatchdogService.start(context, null);
    }
}
