package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class RebootScheduleTest {
    private static final long HOUR = 3_600_000L;

    @Test public void parse() {
        assertArrayEquals(new int[]{4, 0}, RebootSchedule.parse("04:00"));
        assertArrayEquals(new int[]{23, 59}, RebootSchedule.parse(" 23:59 "));
        assertNull(RebootSchedule.parse(""));
        assertNull(RebootSchedule.parse(null));
        assertNull(RebootSchedule.parse("25:00"));
        assertNull(RebootSchedule.parse("12:60"));
        assertNull(RebootSchedule.parse("4"));
        assertArrayEquals(new int[]{4, 0}, RebootSchedule.parse("4:00"));
        assertArrayEquals(new int[]{4, 0}, RebootSchedule.parse(" 04:00 "));
        assertNull(RebootSchedule.parse("+4:00"));
        assertNull(RebootSchedule.parse("-1:00"));
        assertNull(RebootSchedule.parse("4:0"));
        assertNull(RebootSchedule.parse("\u0664:00"));
        assertNull(RebootSchedule.parse("04:\u0660\u0660"));
        assertNull(RebootSchedule.parse("ab:cd"));
    }

    @Test public void dueInsideFiveMinutesAfterTarget() {
        int[] at = {4, 0};
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 2), at, LocalDate.of(2026, 11, 11), 5 * HOUR));
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 6), at, LocalDate.of(2026, 11, 11), 5 * HOUR));
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 3, 59), at, LocalDate.of(2026, 11, 11), 5 * HOUR));
    }

    @Test public void minuteBoundaries() {
        int[] at = {4, 0};
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 0), at, null, 5 * HOUR));
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 4, 59), at, null, 5 * HOUR));
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 5), at, null, 5 * HOUR));
    }

    @Test public void nullTimeIsNeverDue() {
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), null, null, 5 * HOUR));
    }

    @Test public void neverTwiceADay() {
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), new int[]{4, 0}, LocalDate.of(2026, 11, 12), 5 * HOUR));
    }

    @Test public void neverInFirstHourAfterBoot() {
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), new int[]{4, 0}, null, 59 * 60_000L));
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), new int[]{4, 0}, null, HOUR));
    }

    @Test public void neverWithUnsetClock() {
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(1970, 1, 1, 4, 1), new int[]{4, 0}, null, 5 * HOUR));
    }

    @Test public void windowCrossesMidnight() {
        int[] at = {23, 58};
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 11, 23, 59), at, LocalDate.of(2026, 11, 10), 5 * HOUR));
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 0, 1), at, LocalDate.of(2026, 11, 10), 5 * HOUR));
        assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 0, 2), at, LocalDate.of(2026, 11, 10), 5 * HOUR));
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 0, 3), at, LocalDate.of(2026, 11, 10), 5 * HOUR));
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 11, 23, 57), at, LocalDate.of(2026, 11, 10), 5 * HOUR));
    }

    @Test public void doubleRebootAcrossMidnightBlockedByUptimeGuard() {
        int[] at = {23, 58};
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 0, 1), at, LocalDate.of(2026, 11, 11), 2 * 60_000L));
    }

    @Test public void sameDateAsLastRebootIsNotDueEvenInsideWrappedWindow() {
        assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 11, 23, 59), new int[]{23, 58}, LocalDate.of(2026, 11, 11), 5 * HOUR));
    }
}
