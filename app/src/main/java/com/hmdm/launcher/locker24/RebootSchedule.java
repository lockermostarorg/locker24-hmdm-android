package com.hmdm.launcher.locker24;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RebootSchedule {
    private static final int WINDOW_MINUTES = 5;
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final long MIN_UPTIME_MS = 3_600_000L;
    private static final int MIN_VALID_YEAR = 2024;

    private static final Pattern TIME = Pattern.compile("\\s*([0-9]{1,2}):([0-9]{2})\\s*");

    private RebootSchedule() {
    }

    public static int[] parse(String hhmm) {
        if (hhmm == null) {
            return null;
        }
        Matcher matcher = TIME.matcher(hhmm);
        if (!matcher.matches()) {
            return null;
        }
        int h = Integer.parseInt(matcher.group(1));
        int m = Integer.parseInt(matcher.group(2));
        if (h > 23 || m > 59) {
            return null;
        }
        return new int[]{h, m};
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
        return !now.toLocalDate().equals(lastRebootDate);
    }
}
