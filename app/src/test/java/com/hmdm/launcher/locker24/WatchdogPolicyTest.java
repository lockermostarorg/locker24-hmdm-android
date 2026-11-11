package com.hmdm.launcher.locker24;

import static com.hmdm.launcher.locker24.WatchdogPolicy.Action.NONE;
import static com.hmdm.launcher.locker24.WatchdogPolicy.Action.RELAUNCH;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WatchdogPolicyTest {
    @Test public void relaunchesWhenLockTaskLostOnSecondCheck() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
    }

    @Test public void relaunchesWhenLauncherInFrontOnFourthCheck() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, p.evaluate(true, false, true, true));
    }

    @Test public void healthyCheckResetsCounters() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(NONE, p.evaluate(true, false, true, false));
        assertEquals(NONE, p.evaluate(true, false, false, false));
        WatchdogPolicy q = new WatchdogPolicy(4);
        for (int i = 0; i < 3; i++) assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(NONE, q.evaluate(true, false, true, false));
        for (int i = 0; i < 3; i++) assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, q.evaluate(true, false, true, true));
    }

    @Test public void adminUnlockedSuppressesRelaunch() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, true, false, false));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, true, true, true));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, true, false, true));
    }

    @Test public void adminUnlockedResetsCounters() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(NONE, p.evaluate(true, true, false, false));
        assertEquals(NONE, p.evaluate(true, false, false, false));
    }

    @Test public void notRequiredNeverRelaunches() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(false, false, false, true));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(false, false, true, true));
    }

    @Test public void countersDoNotLeakIntoEachOther() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, false, true));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, true));

        WatchdogPolicy q = new WatchdogPolicy(4);
        assertEquals(NONE, q.evaluate(true, false, false, false));
        assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(NONE, q.evaluate(true, false, false, false));
    }

    @Test public void countersResetAfterRelaunch() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));

        WatchdogPolicy q = new WatchdogPolicy(4);
        for (int i = 0; i < 3; i++) assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, q.evaluate(true, false, true, true));
        for (int i = 0; i < 3; i++) assertEquals(NONE, q.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, q.evaluate(true, false, true, true));
    }

    @Test public void limitZeroBehavesLikeLimitOne() {
        WatchdogPolicy p = new WatchdogPolicy(0);
        assertEquals(RELAUNCH, p.evaluate(true, false, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, false));
    }

    @Test public void screenOffKeepsLastInteractiveInFrontState() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, true, false, false));
    }

    @Test public void screenOffAfterKioskInFrontNeverRelaunches() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, false, true));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, false, true, true, false));
    }

    @Test public void adminUnlockedSuppressesWhileNotInteractive() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, true, true));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, true, true, true, false));
        for (int i = 0; i < 10; i++) assertEquals(NONE, p.evaluate(true, true, false, false, false));
    }

    @Test public void screenOffInFrontRelaunchHappensOnlyOnce() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, true, true, true));
        assertEquals(NONE, p.evaluate(true, false, true, true, false));
        assertEquals(NONE, p.evaluate(true, false, true, true, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, true, true, false));
        for (int i = 0; i < 120; i++) assertEquals(NONE, p.evaluate(true, false, true, true, false));
    }

    @Test public void lockLostWhileScreenOffRelaunchesOncePerPeriod() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        int count = 0;
        for (int i = 0; i < 120; i++) {
            if (p.evaluate(true, false, false, false, false) == RELAUNCH) count++;
        }
        assertEquals(1, count);
        assertEquals(NONE, p.evaluate(true, false, true, false, true));
        assertEquals(NONE, p.evaluate(true, false, false, false, true));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false, true));
    }

    @Test public void oneInteractiveInFrontTickThenScreenOffGivesOneRelaunch() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        int count = 0;
        if (p.evaluate(true, false, true, true, true) == RELAUNCH) count++;
        for (int i = 0; i < 120; i++) {
            if (p.evaluate(true, false, true, true, false) == RELAUNCH) count++;
        }
        assertEquals(1, count);
    }
}
