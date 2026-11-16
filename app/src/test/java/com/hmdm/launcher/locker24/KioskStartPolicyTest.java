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

        void boot(long now, long elapsedSinceBoot) {
            if (KioskStartPolicy.installPredatesBoot(lastUpdateTime, now, elapsedSinceBoot)) {
                handled = lastUpdateTime;
            }
        }

        void lockCheck(boolean locked, long now) {
            if (KioskStartPolicy.engagedAnswersUpdate(locked, lastUpdateTime, handled, now)) {
                handled = lastUpdateTime;
            }
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

        KioskStartPolicy.Decision third = d.start(true, T0 + 20_000);
        assertFalse(third.clearTask);
        assertFalse(third.forcedByUpdate);
    }

    @Test public void startRightAfterTheReplacementIsForcedEvenIfAnEarlierStartReadTheOldValue() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        assertFalse(d.start(true, T0 - 500).clearTask);
        d.lastUpdateTime = T0;
        KioskStartPolicy.Decision after = d.start(true, T0 + 500);
        assertTrue(after.clearTask);
        assertTrue(after.forcedByUpdate);
    }

    @Test public void lockHoldingAfterTheUpdateAnswersItWhenTheStartReadTheOldValue() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        assertTrue(d.start(false, T0 - 500).clearTask);
        d.lastUpdateTime = T0;

        d.lockCheck(true, T0 + 6_000);
        assertEquals(T0, d.handled);

        KioskStartPolicy.Decision inWindow = d.start(true, T0 + 20_000);
        assertFalse(inWindow.clearTask);
        assertFalse(inWindow.forcedByUpdate);
        assertFalse(d.start(true, T0 + 60_000).clearTask);
    }

    @Test public void staleLockRightAfterTheReplacementDoesNotAnswerTheUpdate() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        d.lockCheck(true, T0 + 1_000);
        d.lockCheck(true, T0 + KioskStartPolicy.ENGAGED_AFTER_UPDATE_MS);
        assertEquals(OLD_UPDATE, d.handled);

        KioskStartPolicy.Decision s = d.start(true, T0 + 6_000);
        assertTrue(s.clearTask);
        assertTrue(s.forcedByUpdate);
    }

    @Test public void lockNotEngagedLeavesTheUpdateOpen() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        d.lockCheck(false, T0 + 10_000);
        assertEquals(OLD_UPDATE, d.handled);
        assertTrue(d.start(true, T0 + 12_000).forcedByUpdate);
    }

    @Test public void failedInstallNeverClearsAHealthyTask() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        for (long t = T0; t < T0 + 200_000; t += 20_000) {
            KioskStartPolicy.Decision s = d.start(true, t);
            assertFalse(s.clearTask);
            assertFalse(s.forcedByUpdate);
        }
    }

    @Test public void rebootAnswersAnUpdateInstalledBeforeIt() {
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        long bootAt = T0 + 2_000;
        d.boot(bootAt + 25_000, 25_000);

        KioskStartPolicy.Decision atBoot = d.start(false, bootAt + 26_000);
        assertTrue(atBoot.clearTask);
        assertFalse(atBoot.forcedByUpdate);

        KioskStartPolicy.Decision later = d.start(true, bootAt + 27_000);
        assertFalse(later.clearTask);
        assertFalse(later.forcedByUpdate);
    }

    @Test public void lateBootCompletedDoesNotSwallowAnUpdateInstalledAfterBoot() {
        long bootAt = T0 - 60_000;
        Device d = new Device(OLD_UPDATE, OLD_UPDATE);
        d.lastUpdateTime = T0;
        d.boot(T0 + 1_000, T0 + 1_000 - bootAt);
        assertEquals(OLD_UPDATE, d.handled);

        KioskStartPolicy.Decision s = d.start(true, T0 + 2_000);
        assertTrue(s.clearTask);
        assertTrue(s.forcedByUpdate);
    }

    @Test public void installPredatesBootEdges() {
        assertTrue(KioskStartPolicy.installPredatesBoot(T0 - 1, T0 + 10_000, 10_000));
        assertFalse(KioskStartPolicy.installPredatesBoot(T0, T0 + 10_000, 10_000));
        assertFalse(KioskStartPolicy.installPredatesBoot(0, T0, 10_000));
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

    @Test public void retriesWhenLockNotEngagedAndKioskStillWanted() {
        assertTrue(KioskStartPolicy.shouldRetry(false, true, false));
    }

    @Test public void noRetryWhenEngagedNotRequiredOrAdminUnlocked() {
        assertFalse(KioskStartPolicy.shouldRetry(true, true, false));
        assertFalse(KioskStartPolicy.shouldRetry(false, false, false));
        assertFalse(KioskStartPolicy.shouldRetry(false, true, true));
    }

    @Test public void recentFreshStartWindow() {
        assertFalse(KioskStartPolicy.freshStartRecent(0, 10_000));
        assertTrue(KioskStartPolicy.freshStartRecent(10_000, 10_000));
        assertTrue(KioskStartPolicy.freshStartRecent(10_000, 10_000 + KioskStartPolicy.RECENT_FRESH_START_MS - 1));
        assertFalse(KioskStartPolicy.freshStartRecent(10_000, 10_000 + KioskStartPolicy.RECENT_FRESH_START_MS));
    }
}
