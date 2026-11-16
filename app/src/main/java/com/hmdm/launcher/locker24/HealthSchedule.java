package com.hmdm.launcher.locker24;

public final class HealthSchedule {
    public static final long TRIGGER_DEBOUNCE_MS = 30_000L;
    public static final long KIOSK_DEBOUNCE_MS = 60_000L;

    private static final int DEFAULT_MINUTES = 5;
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 60;
    private static final long TOLERANCE_MS = 15_000L;

    private HealthSchedule() {
    }

    public static int intervalMinutes(String setting) {
        int minutes = DEFAULT_MINUTES;
        if (setting != null) {
            try {
                minutes = Integer.parseInt(setting.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }

    public static boolean isDue(long now, long lastCollectAt, int intervalMin) {
        return isDue(now, lastCollectAt, intervalMin, TOLERANCE_MS);
    }

    public static boolean isDue(long now, long lastCollectAt, int intervalMin, long toleranceMs) {
        return lastCollectAt == 0
                || now < lastCollectAt
                || now - lastCollectAt >= intervalMin * 60_000L - toleranceMs;
    }
}
