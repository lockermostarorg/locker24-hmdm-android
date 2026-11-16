package com.hmdm.launcher.locker24;

import android.content.pm.ActivityInfo;

import com.hmdm.launcher.Const;

public final class OrientationPolicy {
    public static final int NOT_CONFIGURED = Integer.MIN_VALUE;

    private OrientationPolicy() {
    }

    public static int requestedOrientation(Integer configured) {
        if (configured == null) {
            return NOT_CONFIGURED;
        }
        switch (configured) {
            case Const.SCREEN_ORIENTATION_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case Const.SCREEN_ORIENTATION_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            default:
                return NOT_CONFIGURED;
        }
    }

    public static boolean needsRequest(int current, int target) {
        return target != NOT_CONFIGURED && current != target;
    }

    public static String name(int requested) {
        switch (requested) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return "portrait";
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return "landscape";
            default:
                return String.valueOf(requested);
        }
    }
}
