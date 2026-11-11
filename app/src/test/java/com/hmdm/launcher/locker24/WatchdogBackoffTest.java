package com.hmdm.launcher.locker24;

import static com.hmdm.launcher.locker24.WatchdogPolicy.Action.NONE;
import static com.hmdm.launcher.locker24.WatchdogPolicy.Action.RELAUNCH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WatchdogBackoffTest {
    private static int lockLostRelaunches(WatchdogPolicy p, int ticks, boolean interactive) {
        int count = 0;
        for (int i = 0; i < ticks; i++) {
            if (p.evaluate(true, false, false, false, interactive) == RELAUNCH) count++;
        }
        return count;
    }

    private static WatchdogPolicy backedOff() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(3, lockLostRelaunches(p, 6, true));
        assertTrue(p.isLockLostBackedOff());
        return p;
    }

    @Test public void stopsAfterThreeLockLostRelaunches() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
        assertFalse(p.isLockLostBackedOff());
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
        assertTrue(p.isLockLostBackedOff());
        for (int i = 0; i < 100; i++) assertEquals(NONE, p.evaluate(true, false, false, false));
        assertTrue(p.isLockLostBackedOff());
    }

    @Test public void lockedTickRearms() {
        WatchdogPolicy p = backedOff();
        assertEquals(NONE, p.evaluate(true, false, true, false));
        assertFalse(p.isLockLostBackedOff());
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
    }

    @Test public void lockedTickWhileScreenOffRearms() {
        WatchdogPolicy p = backedOff();
        assertEquals(NONE, p.evaluate(true, false, true, false, false));
        assertFalse(p.isLockLostBackedOff());
    }

    @Test public void adminUnlockRearms() {
        WatchdogPolicy p = backedOff();
        assertEquals(NONE, p.evaluate(true, true, false, false));
        assertFalse(p.isLockLostBackedOff());
        assertEquals(NONE, p.evaluate(true, false, false, false));
        assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
    }

    @Test public void interactiveNotRequiredRearms() {
        WatchdogPolicy p = backedOff();
        assertEquals(NONE, p.evaluate(false, false, false, false, true));
        assertFalse(p.isLockLostBackedOff());
    }

    @Test public void screenOffNotRequiredDoesNotRearm() {
        WatchdogPolicy p = backedOff();
        assertEquals(NONE, p.evaluate(false, false, false, false, false));
        assertTrue(p.isLockLostBackedOff());
        assertEquals(0, lockLostRelaunches(p, 20, true));
    }

    @Test public void screenOffSuppressedRelaunchesDoNotCount() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        assertEquals(1, lockLostRelaunches(p, 120, false));
        assertFalse(p.isLockLostBackedOff());
    }

    @Test public void skippedRelaunchDoesNotCount() {
        WatchdogPolicy p = new WatchdogPolicy(4);
        for (int i = 0; i < 10; i++) {
            if (p.evaluate(true, false, false, false) == RELAUNCH) p.onRelaunchSkipped();
        }
        assertFalse(p.isLockLostBackedOff());
        assertEquals(3, lockLostRelaunches(p, 6, true));
        assertTrue(p.isLockLostBackedOff());
    }

    @Test public void inFrontPathUnaffected() {
        WatchdogPolicy p = backedOff();
        for (int i = 0; i < 3; i++) assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, p.evaluate(true, false, true, true));
        for (int i = 0; i < 3; i++) assertEquals(NONE, p.evaluate(true, false, true, true));
        assertEquals(RELAUNCH, p.evaluate(true, false, true, true));
    }
}
