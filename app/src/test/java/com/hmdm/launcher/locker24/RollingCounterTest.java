package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RollingCounterTest {
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void countsLast24h() {
        List<Long> stamps = new ArrayList<>(Arrays.asList(
                NOW - 86_400_000L, NOW - 86_399_999L, NOW));
        assertEquals(2, RollingCounter.count(stamps, NOW));
    }

    @Test
    public void capsAt500() {
        List<Long> stamps = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            RollingCounter.record(stamps, NOW + i);
        }
        assertEquals(500, stamps.size());
        assertEquals(Long.valueOf(NOW + 100), stamps.get(0));
        assertEquals(Long.valueOf(NOW + 599), stamps.get(499));
    }

    @Test
    public void dropsStampsFromTheFuture() {
        List<Long> stamps = new ArrayList<>(Arrays.asList(NOW + 61_000, NOW));
        assertEquals(1, RollingCounter.count(stamps, NOW));
        RollingCounter.record(stamps, NOW);
        assertEquals(2, stamps.size());
        assertEquals(Long.valueOf(NOW), stamps.get(0));
    }

    @Test
    public void recordPrunesOldStamps() {
        List<Long> stamps = new ArrayList<>(Arrays.asList(NOW - 90_000_000L));
        RollingCounter.record(stamps, NOW);
        assertEquals(Arrays.asList(NOW), stamps);
    }

    @Test
    public void futureBoundaryIs60Seconds() {
        List<Long> stamps = new ArrayList<>(Arrays.asList(NOW + 60_000, NOW + 60_001));
        assertEquals(1, RollingCounter.count(stamps, NOW));
    }

    @Test
    public void toleratesNullAndKeepsNewestByValue() {
        List<Long> stamps = new ArrayList<>();
        stamps.add(null);
        for (int i = 0; i < 500; i++) {
            stamps.add(NOW + 30_000);
        }
        RollingCounter.record(stamps, NOW);
        assertEquals(500, stamps.size());
        assertEquals(Long.valueOf(NOW), stamps.get(0));
    }
}
