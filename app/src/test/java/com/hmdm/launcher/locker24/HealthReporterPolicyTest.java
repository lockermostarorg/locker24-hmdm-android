package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        for (long tick : new long[]{120_000L, 240_000L}) {
            List<Long> collected = simulateTicks(tick, 2_000L, 3_600_000L);
            for (int i = 1; i < collected.size(); i++) {
                assertEquals("tick " + tick, 240_000L, collected.get(i) - collected.get(i - 1));
            }
        }
    }

    @Test
    public void defaultTickCollectsEveryFiveMinutes() {
        List<Long> collected = simulateTicks(30_000L, 2_000L, 3_600_000L);
        for (int i = 1; i < collected.size(); i++) {
            assertEquals(300_000L, collected.get(i) - collected.get(i - 1));
        }
    }

    @Test
    public void stampIsTheSnapshotTime() {
        HealthSnapshot s = new HealthSnapshot();
        s.ts = 1_234L;
        assertEquals(1_234L, HealthSchedule.collectStamp(s, 9_999L));
        assertEquals(9_999L, HealthSchedule.collectStamp(new HealthSnapshot(), 9_999L));
        assertEquals(9_999L, HealthSchedule.collectStamp(null, 9_999L));
    }

    /**
     * Ticks every {@code tick} ms; a due tick takes a snapshot at the tick time and finishes
     * {@code collectMs} later, when the reporter stamps it through {@code collectStamp}.
     * Returns the tick times that collected.
     */
    private static List<Long> simulateTicks(long tick, long collectMs, long durationMs) {
        long start = 1_700_000_000_000L;
        long lastCollectAt = 0;
        List<Long> collected = new ArrayList<>();
        for (long now = start; now <= start + durationMs; now += tick) {
            if (HealthSchedule.isDue(now, lastCollectAt, 5, HealthSchedule.toleranceMs(tick))) {
                HealthSnapshot s = new HealthSnapshot();
                s.ts = now;
                long finishedAt = now + collectMs;
                lastCollectAt = HealthSchedule.collectStamp(s, finishedAt);
                collected.add(now);
            }
        }
        assertTrue(collected.size() > 2);
        return collected;
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

    @Test
    public void firstRunAfterSuccessSendsAFullBatch() {
        assertEquals(HealthQueue.BATCH, UploadOutcome.batchSize(0, false, false));
    }

    @Test
    public void retryRunsAndRunsAfterARetrySendOneReport() {
        assertEquals(1, UploadOutcome.batchSize(1, false, false));
        assertEquals(1, UploadOutcome.batchSize(4, false, false));
        assertEquals(1, UploadOutcome.batchSize(0, true, false));
        assertEquals(1, UploadOutcome.batchSize(3, true, false));
    }

    @Test
    public void aSentProbeOpensFullBatches() {
        assertEquals(HealthQueue.BATCH, UploadOutcome.batchSize(0, true, true));
        assertEquals(HealthQueue.BATCH, UploadOutcome.batchSize(4, true, true));
    }

    @Test
    public void errorLogsOncePerClassPerHour() {
        Map<String, Long> last = new HashMap<>();
        long hour = HealthReporter.ERROR_LOG_INTERVAL_MS;
        assertEquals(3_600_000L, hour);
        assertTrue(HealthReporter.shouldLog(last, "IllegalStateException", 1_000L));
        assertFalse(HealthReporter.shouldLog(last, "IllegalStateException", 1_000L + hour - 1));
        assertTrue(HealthReporter.shouldLog(last, "NullPointerException", 2_000L));
        assertTrue(HealthReporter.shouldLog(last, "IllegalStateException", 1_000L + hour));
        assertTrue(HealthReporter.shouldLog(last, "NullPointerException", 1_000L));
    }
}
