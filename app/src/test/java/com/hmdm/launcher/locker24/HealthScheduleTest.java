package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HealthScheduleTest {
    private static final long LAST = 1_000_000L;

    @Test
    public void intervalDefaultsAndClamps() {
        assertEquals(5, HealthSchedule.intervalMinutes(null));
        assertEquals(5, HealthSchedule.intervalMinutes(""));
        assertEquals(5, HealthSchedule.intervalMinutes("abc"));
        assertEquals(1, HealthSchedule.intervalMinutes("0"));
        assertEquals(60, HealthSchedule.intervalMinutes("61"));
        assertEquals(15, HealthSchedule.intervalMinutes("15"));
    }

    @Test
    public void dueAfterIntervalMinus15s() {
        assertFalse(HealthSchedule.isDue(LAST + 284_999, LAST, 5));
        assertTrue(HealthSchedule.isDue(LAST + 285_000, LAST, 5));
        assertTrue(HealthSchedule.isDue(LAST + 1, 0, 5));
        assertTrue(HealthSchedule.isDue(LAST - 1, LAST, 5));
    }

    @Test
    public void debounceConstants() {
        assertEquals(30_000L, HealthSchedule.TRIGGER_DEBOUNCE_MS);
        assertEquals(60_000L, HealthSchedule.KIOSK_DEBOUNCE_MS);
    }

    @Test
    public void customToleranceBoundaries() {
        assertFalse(HealthSchedule.isDue(LAST + 59_999, LAST, 5, 240_000));
        assertTrue(HealthSchedule.isDue(LAST + 60_000, LAST, 5, 240_000));
        assertEquals(HealthSchedule.isDue(LAST + 285_000, LAST, 5),
                HealthSchedule.isDue(LAST + 285_000, LAST, 5, 15_000));
    }

    @Test
    public void intervalTrimsWhitespace() {
        assertEquals(7, HealthSchedule.intervalMinutes(" 7 "));
    }
}
