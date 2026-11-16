package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KioskStartPolicyTest {
    private static final long T0 = 1_763_280_000_000L;
    private static final long OLD_UPDATE = T0 - 30L * 24 * 3600 * 1000;

    /** Holds the persisted handledUpdateTime across starts, the way KioskState does. */
    private static final class Device {
        boolean tracking = true;
        long lastUpdateTime;
        long handled;

        Device(long lastUpdateTime, long handled) {
            this.lastUpdateTime = lastUpdateTime;
            this.handled = handled;
        }

        KioskStartPolicy.Decision start(boolean lockTaskRunning, long now) {
            KioskStartPolicy.Decision d = KioskStartPolicy.decide(tracking, lastUpdateTime, handled,
                    lockTaskRunning, now);
            handled = d.newHandled;
            return d;
        }

        void boot() {
            handled = lastUpdateTime;
        }
    }

    @Test public void singleUpdateClearsALockedTaskOnlyOnce() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;

        KioskStartPolicy.Decision first = d.start(true, T0 + 1_000);
        assertTrue(first.clearTask);
        assertTrue(first.forcedByUpdate);

        KioskStartPolicy.Decision second = d.start(true, T0 + 1_200);
        assertFalse(second.clearTask);
        assertFalse(second.forcedByUpdate);

        KioskStartPolicy.Decision third = d.start(true, T0 + 90_000);
        assertFalse(third.clearTask);
        assertFalse(third.forcedByUpdate);
    }

    @Test public void startsBeforeTheReplacementDoNotUseUpTheUpdate() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        assertFalse(d.start(true, T0 - 2_000).clearTask);
        d.lastUpdateTime = T0;
        KioskStartPolicy.Decision after = d.start(true, T0 + 500);
        assertTrue(after.clearTask);
        assertTrue(after.forcedByUpdate);
    }

    @Test public void failedInstallNeverClearsAHealthyTask() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        for (long t = T0; t < T0 + 200_000; t += 20_000) {
            KioskStartPolicy.Decision s = d.start(true, t);
            assertFalse(s.clearTask);
            assertFalse(s.forcedByUpdate);
        }
    }

    @Test public void rebootAnswersTheUpdate() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        d.boot();

        KioskStartPolicy.Decision atBoot = d.start(false, T0 + 30_000);
        assertTrue(atBoot.clearTask);
        assertFalse(atBoot.forcedByUpdate);

        KioskStartPolicy.Decision later = d.start(true, T0 + 60_000);
        assertFalse(later.clearTask);
        assertFalse(later.forcedByUpdate);
    }

    @Test public void updateThenStartWithLockNotRunningClearsAndUsesUpTheUpdate() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;

        KioskStartPolicy.Decision first = d.start(false, T0 + 2_000);
        assertTrue(first.clearTask);
        assertTrue(first.forcedByUpdate);

        KioskStartPolicy.Decision locked = d.start(true, T0 + 10_000);
        assertFalse(locked.clearTask);

        KioskStartPolicy.Decision unlocked = d.start(false, T0 + 20_000);
        assertTrue(unlocked.clearTask);
        assertFalse(unlocked.forcedByUpdate);
    }

    @Test public void updateSeenAfterTheWindowIsMarkedHandledWithoutClearing() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;

        KioskStartPolicy.Decision late = d.start(true, T0 + KioskStartPolicy.UPDATE_WINDOW_MS + 1);
        assertFalse(late.clearTask);
        assertFalse(late.forcedByUpdate);
        assertEquals(T0, d.handled);

        assertFalse(d.start(true, T0 + 5_000).clearTask);
    }

    @Test public void windowEdges() {
        assertTrue(KioskStartPolicy.decide(true, T0, OLD_UPDATE, true, T0 + KioskStartPolicy.UPDATE_WINDOW_MS)
                .clearTask);
        assertFalse(KioskStartPolicy.decide(true, T0, OLD_UPDATE, true, T0 + KioskStartPolicy.UPDATE_WINDOW_MS + 1)
                .clearTask);
    }

    @Test public void smallClockSkewStillCountsAsTheUpdate() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        KioskStartPolicy.Decision s = d.start(true, T0 - 30_000);
        assertTrue(s.clearTask);
        assertTrue(s.forcedByUpdate);
    }

    @Test public void largeClockSkewIsIgnoredAndNotRevivedLater() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        KioskStartPolicy.Decision skewed = d.start(true, T0 - KioskStartPolicy.CLOCK_SKEW_MS);
        assertFalse(skewed.clearTask);
        assertFalse(skewed.forcedByUpdate);

        assertFalse(d.start(true, T0 + 1_000).clearTask);
    }

    @Test public void firstRunWithNothingHandledOnlyFiresForARecentUpdate() {
        Device stale = new Device(OLD_UPDATE, 0);
        assertFalse(stale.start(true, T0).clearTask);
        assertEquals(OLD_UPDATE, stale.handled);

        Device recent = new Device(T0, 0);
        assertTrue(recent.start(true, T0 + 1_000).forcedByUpdate);
        assertFalse(recent.start(true, T0 + 2_000).clearTask);
    }

    @Test public void unknownUpdateTimeKeepsUpstreamRule() {
        Device d = new Device(0, 0);
        assertFalse(d.start(true, T0).clearTask);
        assertTrue(d.start(false, T0).clearTask);
        assertFalse(d.start(false, T0).forcedByUpdate);
        assertEquals(0, d.handled);
    }

    @Test public void trackingOffIsUpstream() {
        Device d = new Device(T0, OLD_UPDATE);
        d.tracking = false;
        KioskStartPolicy.Decision locked = d.start(true, T0 + 1_000);
        assertFalse(locked.clearTask);
        assertFalse(locked.forcedByUpdate);
        KioskStartPolicy.Decision unlocked = d.start(false, T0 + 1_000);
        assertTrue(unlocked.clearTask);
        assertFalse(unlocked.forcedByUpdate);
        assertEquals(OLD_UPDATE, d.handled);
    }

    @Test public void retriesOnlyOnceAfterAnUpdateStartThatDidNotEngage() {
        assertTrue(KioskStartPolicy.shouldRetry(true, false, false, true, false));
        assertFalse(KioskStartPolicy.shouldRetry(true, true, false, true, false));
    }

    @Test public void noRetryForOrdinaryStarts() {
        assertFalse(KioskStartPolicy.shouldRetry(false, false, false, true, false));
    }

    @Test public void noRetryWhenEngagedNotRequiredOrAdminUnlocked() {
        assertFalse(KioskStartPolicy.shouldRetry(true, false, true, true, false));
        assertFalse(KioskStartPolicy.shouldRetry(true, false, false, false, false));
        assertFalse(KioskStartPolicy.shouldRetry(true, false, false, true, true));
    }
}
