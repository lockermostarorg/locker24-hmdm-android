package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HealthReporterPolicyTest {

    @Test
    public void toleranceIsHalfTheTickBetween15And60Seconds() {
        assertEquals(15_000L, HealthSchedule.toleranceMs(0));
        assertEquals(15_000L, HealthSchedule.toleranceMs(10_000));
        assertEquals(15_000L, HealthSchedule.toleranceMs(30_000));
        assertEquals(30_000L, HealthSchedule.toleranceMs(60_000));
        assertEquals(60_000L, HealthSchedule.toleranceMs(120_000));
        assertEquals(60_000L, HealthSchedule.toleranceMs(240_000));
    }

    @Test
    public void longTickStillCollectsWithinOneTickOfTheInterval() {
        long tick = 240_000L;
        long tolerance = HealthSchedule.toleranceMs(tick);
        long last = 1_000_000L;
        assertFalse(HealthSchedule.isDue(last + 239_999, last, 5, tolerance));
        assertTrue(HealthSchedule.isDue(last + 240_000, last, 5, tolerance));
    }

    @Test
    public void pendingBootKeepsItsName() {
        assertEquals("boot", HealthSchedule.mergeTrigger("boot", "network"));
        assertEquals("boot", HealthSchedule.mergeTrigger("boot", "charging"));
        assertEquals("network", HealthSchedule.mergeTrigger(null, "network"));
        assertEquals("kiosk", HealthSchedule.mergeTrigger("network", "kiosk"));
        assertEquals("boot", HealthSchedule.mergeTrigger("charging", "boot"));
    }

    @Test
    public void bodyParsesStatusAndMessage() {
        UploadOutcome.Body b = UploadOutcome.Body.parse("{\"status\":\"ERROR\",\"message\":\"error.signature\",\"data\":null}");
        assertEquals("ERROR", b.status);
        assertEquals("error.signature", b.message);
        b = UploadOutcome.Body.parse("{\"status\":\"OK\"}");
        assertEquals("OK", b.status);
        assertNull(b.message);
    }

    @Test
    public void unparseableBodyGivesNullsAndRetry() {
        for (String text : Arrays.asList(null, "", "<html>bad gateway</html>", "[1,2]", "{\"status\":5}")) {
            UploadOutcome.Body b = UploadOutcome.Body.parse(text);
            assertNull(text, b.status);
            assertEquals(UploadOutcome.Result.RETRY, UploadOutcome.classify(200, b.status, b.message));
        }
    }

    @Test
    public void retriesStopAfterTheFifthRun() {
        assertFalse(UploadOutcome.stopRetrying(0));
        assertFalse(UploadOutcome.stopRetrying(3));
        assertTrue(UploadOutcome.stopRetrying(4));
        assertTrue(UploadOutcome.stopRetrying(9));
    }

    @Test
    public void stampsRoundTrip() {
        List<Long> stamps = new ArrayList<>(Arrays.asList(1L, 20L, 300L));
        assertEquals("1,20,300", HealthCounters.formatStamps(stamps));
        assertEquals(stamps, HealthCounters.parseStamps("1,20,300"));
    }

    @Test
    public void stampsSkipGarbage() {
        assertEquals(new ArrayList<Long>(), HealthCounters.parseStamps(null));
        assertEquals(new ArrayList<Long>(), HealthCounters.parseStamps(""));
        assertEquals(Arrays.asList(5L, 7L), HealthCounters.parseStamps(" 5,x,,7 ,"));
        assertEquals("", HealthCounters.formatStamps(new ArrayList<Long>()));
    }
}
