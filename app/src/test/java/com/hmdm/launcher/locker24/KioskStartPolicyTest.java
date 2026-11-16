package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KioskStartPolicyTest {
    private static final long W = KioskStartPolicy.REPLACED_WINDOW_MS;

    @Test public void noMarkIsNotRecent() {
        assertFalse(KioskStartPolicy.replacedRecently(0, 1_000_000));
    }

    @Test public void markWithinWindowIsRecent() {
        assertTrue(KioskStartPolicy.replacedRecently(1_000_000, 1_000_000));
        assertTrue(KioskStartPolicy.replacedRecently(1_000_000, 1_000_000 + W));
    }

    @Test public void markOlderThanWindowIsNotRecent() {
        assertFalse(KioskStartPolicy.replacedRecently(1_000_000, 1_000_000 + W + 1));
    }

    @Test public void markFromBeforeRebootIsNotRecent() {
        assertFalse(KioskStartPolicy.replacedRecently(5_000_000, 40_000));
    }

    @Test public void clearsTaskWhenLockTaskNotRunning() {
        assertTrue(KioskStartPolicy.clearTask(false, false));
    }

    @Test public void keepsTaskWhenLockedAndNotReplaced() {
        assertFalse(KioskStartPolicy.clearTask(true, false));
    }

    @Test public void clearsTaskWhenReplacedEvenIfLockReportsLocked() {
        assertTrue(KioskStartPolicy.clearTask(true, true));
        assertTrue(KioskStartPolicy.clearTask(false, true));
    }

    @Test public void retriesOnceWhenLockNotEngaged() {
        assertTrue(KioskStartPolicy.shouldRetry(false, false, true, false, 0, 10_000));
    }

    @Test public void noRetryAfterARetry() {
        assertFalse(KioskStartPolicy.shouldRetry(true, false, true, false, 0, 10_000));
    }

    @Test public void noRetryWhenLockEngaged() {
        assertFalse(KioskStartPolicy.shouldRetry(false, true, true, false, 0, 10_000));
    }

    @Test public void noRetryWhenKioskNotRequired() {
        assertFalse(KioskStartPolicy.shouldRetry(false, false, false, false, 0, 10_000));
    }

    @Test public void noRetryWhenAdminUnlocked() {
        assertFalse(KioskStartPolicy.shouldRetry(false, false, true, true, 0, 10_000));
    }

    @Test public void noSecondRetryWithinDebounce() {
        long last = 100_000;
        assertFalse(KioskStartPolicy.shouldRetry(false, false, true, false, last,
                last + KioskStartPolicy.RETRY_DEBOUNCE_MS - 1));
        assertTrue(KioskStartPolicy.shouldRetry(false, false, true, false, last,
                last + KioskStartPolicy.RETRY_DEBOUNCE_MS));
    }

    @Test public void consumesMarkOnlyWhenFreshStartEngaged() {
        assertTrue(KioskStartPolicy.consumeReplacedMark(1_000, true));
        assertFalse(KioskStartPolicy.consumeReplacedMark(1_000, false));
        assertFalse(KioskStartPolicy.consumeReplacedMark(0, true));
    }
}
