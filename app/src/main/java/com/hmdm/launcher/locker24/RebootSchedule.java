package com.hmdm.launcher.locker24;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class RebootSchedule {
    private static final int WINDOW_MINUTES = 5;
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final long MIN_UPTIME_MS = 3_600_000L;
    private static final int MIN_VALID_YEAR = 2024;

    private RebootSchedule() {
    }

    public static int[] parse(String hhmm) {
        if (hhmm == null) {
            return null;
        }
        String[] parts = hhmm.trim().split(":", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return null;
            }
            return new int[]{h, m};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isDue(LocalDateTime now, int[] at, LocalDate lastRebootDate, long uptimeMs) {
        if (now == null || at == null || at.length != 2) {
            return false;
        }
        if (now.getYear() < MIN_VALID_YEAR || uptimeMs < MIN_UPTIME_MS) {
            return false;
        }
        int nowMinute = now.getHour() * 60 + now.getMinute();
        int atMinute = at[0] * 60 + at[1];
        int sinceTarget = ((nowMinute - atMinute) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        if (sinceTarget >= WINDOW_MINUTES) {
            return false;
        }
        LocalDate windowDate = atMinute > nowMinute ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        return !windowDate.equals(lastRebootDate);
    }
}
