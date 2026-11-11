package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WatchdogIntervalTest {
    @Test public void clampsSeconds() {
        assertEquals(30, WatchdogPolicy.intervalSeconds(null));
        assertEquals(30, WatchdogPolicy.intervalSeconds("abc"));
        assertEquals(10, WatchdogPolicy.intervalSeconds("5"));
        assertEquals(30, WatchdogPolicy.intervalSeconds("30"));
        assertEquals(30, WatchdogPolicy.intervalSeconds(" 30 "));
        assertEquals(240, WatchdogPolicy.intervalSeconds("240"));
        assertEquals(240, WatchdogPolicy.intervalSeconds("3600"));
    }
}
