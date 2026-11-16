package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForcedAppsTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long MIN = 60_000L;

    private static String payload(long issuedAt) {
        return "{\"apps\":[{\"pkg\":\"app.locker24.kiosk\",\"version\":\"2.1.0\"},"
                + "{\"pkg\":\"app.locker24.mdm\",\"version\":\"6.40-l24.6\"}],\"issuedAt\":" + issuedAt + "}";
    }

    @Test public void validPayloadParses() {
        ForcedApps apps = ForcedApps.parse(payload(NOW - 5 * MIN), NOW);
        assertEquals(2, apps.size());
        assertTrue(apps.matches("app.locker24.kiosk", "2.1.0"));
        assertTrue(apps.matches("app.locker24.mdm", "6.40-l24.6"));
        assertFalse(apps.matches("app.locker24.kiosk", "2.0.9"));
        assertFalse(apps.matches("other", "2.1.0"));
    }

    @Test public void payloadAtTheAgeLimitIsFreshAndOlderIsStale() {
        assertFalse(ForcedApps.parse(payload(NOW - 60 * MIN), NOW).isEmpty());
        assertTrue(ForcedApps.parse(payload(NOW - 60 * MIN - 1), NOW).isEmpty());
        assertTrue(ForcedApps.parse(payload(NOW - 5 * 3_600_000L), NOW).isEmpty());
    }

    @Test public void invalidDeviceClockTreatsThePayloadAsFresh() {
        long badNow = 1_000_000_000_000L;
        assertFalse(ForcedApps.parse(payload(badNow - 100 * 3_600_000L), badNow).isEmpty());
    }

    @Test public void missingOrMalformedPayloadIsEmpty() {
        assertTrue(ForcedApps.parse(null, NOW).isEmpty());
        assertTrue(ForcedApps.parse("", NOW).isEmpty());
        assertTrue(ForcedApps.parse("not json", NOW).isEmpty());
        assertTrue(ForcedApps.parse("[]", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"apps\":[{\"pkg\":\"a\",\"version\":\"1\"}]}", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"issuedAt\":" + NOW + "}", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"apps\":\"x\",\"issuedAt\":" + NOW + "}", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"apps\":[],\"issuedAt\":" + NOW + "}", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"apps\":[{\"pkg\":\"a\"}],\"issuedAt\":" + NOW + "}", NOW).isEmpty());
        assertTrue(ForcedApps.parse("{\"apps\":[{\"pkg\":\"a\",\"version\":\"1\"}],\"issuedAt\":\"x\"}", NOW).isEmpty());
    }

    @Test public void forcedAndMatchingInstallsNow() {
        ForcedApps forced = ForcedApps.of("kiosk", "2.1.0");
        assertTrue(ForcedApps.mayRun(forced, "kiosk", "2.1.0", false, false, false));
        assertTrue(ForcedApps.mayRun(forced, "kiosk", "2.1.0", false, true, false));
        assertTrue(ForcedApps.mayRun(forced, "kiosk", "2.1.0", false, false, true));
    }

    @Test public void forcedWithVersionMismatchWaits() {
        ForcedApps forced = ForcedApps.of("kiosk", "2.1.0");
        assertFalse(ForcedApps.mayRun(forced, "kiosk", "2.2.0", false, false, false));
    }

    @Test public void notForcedWaitsOutsideTheWindowOrWifiRule() {
        ForcedApps forced = ForcedApps.of("kiosk", "2.1.0");
        assertFalse(ForcedApps.mayRun(forced, "agent", "6.40-l24.6", false, false, true));
        assertFalse(ForcedApps.mayRun(ForcedApps.EMPTY, "kiosk", "2.1.0", false, true, false));
        assertTrue(ForcedApps.mayRun(ForcedApps.EMPTY, "agent", "1", false, true, true));
    }

    @Test public void removalsWaitEvenWhenThePackageIsForced() {
        ForcedApps forced = ForcedApps.of("kiosk", "2.1.0");
        assertFalse(ForcedApps.mayRun(forced, "kiosk", "2.1.0", true, false, false));
        assertTrue(ForcedApps.mayRun(forced, "kiosk", "2.1.0", true, true, true));
    }

    @Test public void unionMergesTheSetsAndEmptyIsNeutral() {
        ForcedApps a = ForcedApps.of("a", "1");
        ForcedApps b = ForcedApps.of("b", "2");
        ForcedApps both = ForcedApps.union(a, b);
        assertEquals(2, both.size());
        assertTrue(both.matches("a", "1"));
        assertTrue(both.matches("b", "2"));
        assertEquals(1, ForcedApps.union(a, ForcedApps.EMPTY).size());
        assertEquals(1, ForcedApps.union(ForcedApps.EMPTY, a).size());
        assertEquals(1, ForcedApps.union(a, ForcedApps.of("a", "1")).size());
        assertTrue(ForcedApps.union(ForcedApps.EMPTY, ForcedApps.EMPTY).isEmpty());
    }
}
