package com.hmdm.launcher.locker24;

public final class HealthSchedule {
    public static final long TRIGGER_DEBOUNCE_MS = 30_000L;
    public static final long KIOSK_DEBOUNCE_MS = 60_000L;

    private static final int DEFAULT_MINUTES = 5;
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 60;
    private static final long TOLERANCE_MS = 15_000L;
    private static final long MAX_TOLERANCE_MS = 60_000L;
    private static final String TRIGGER_BOOT = "boot";

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

    /** Half the watchdog tick, at least 15 s and at most 60 s, so a long tick does not skip an interval. */
    public static long toleranceMs(long tickMs) {
        return Math.min(MAX_TOLERANCE_MS, Math.max(TOLERANCE_MS, tickMs / 2));
    }

    /** A pending boot request keeps its name when a later request reschedules it. */
    public static String mergeTrigger(String pending, String incoming) {
        return TRIGGER_BOOT.equals(pending) ? TRIGGER_BOOT : incoming;
    }
}
