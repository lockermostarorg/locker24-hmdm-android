package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppInstallOrderTest {
    private static final String SELF = "app.locker24.mdm";

    private static AppInstallOrder.Item item(final String pkg, final String version, final boolean remove) {
        return new AppInstallOrder.Item() {
            @Override
            public String pkg() {
                return pkg;
            }

            @Override
            public String version() {
                return version;
            }

            @Override
            public boolean remove() {
                return remove;
            }
        };
    }

    private static List<String> drain(List<AppInstallOrder.Item> pending, ForcedApps forced, boolean window,
                                      boolean wifi) {
        List<String> order = new ArrayList<>();
        int i;
        while ((i = AppInstallOrder.nextIndex(pending, forced, window, wifi, SELF)) >= 0) {
            order.add(pending.remove(i).pkg());
        }
        return order;
    }

    @Test public void forcedKioskOnlyWhenTheAgentAndARemovalAreNotForced() {
        List<AppInstallOrder.Item> pending = new ArrayList<>(Arrays.asList(
                item("old.app", "0", true), item(SELF, "6.40-l24.7", false), item("kiosk", "1.2.9", false)));
        ForcedApps forced = ForcedApps.of("kiosk", "1.2.9");
        assertEquals(Arrays.asList("kiosk"), drain(pending, forced, false, true));
        assertEquals(2, pending.size());
    }

    @Test public void forcedAgentGoesLastAfterTheOtherForcedApps() {
        List<AppInstallOrder.Item> pending = new ArrayList<>(Arrays.asList(
                item(SELF, "6.40-l24.7", false), item("kiosk", "1.2.9", false)));
        ForcedApps forced = ForcedApps.union(ForcedApps.of(SELF, "6.40-l24.7"), ForcedApps.of("kiosk", "1.2.9"));
        assertEquals(Arrays.asList("kiosk", SELF), drain(pending, forced, false, false));
    }

    @Test public void insideTheWindowTheAgentStillGoesLastAndRemovalsRun() {
        List<AppInstallOrder.Item> pending = new ArrayList<>(Arrays.asList(
                item(SELF, "7", false), item("old.app", "0", true), item("kiosk", "1", false)));
        assertEquals(Arrays.asList("old.app", "kiosk", SELF), drain(pending, ForcedApps.EMPTY, true, true));
    }

    @Test public void nothingRunsOutsideTheWindowWithoutAForcedMatch() {
        List<AppInstallOrder.Item> pending = new ArrayList<>(Arrays.asList(
                item("kiosk", "1.2.9", false), item("old.app", "0", true)));
        assertEquals(-1, AppInstallOrder.nextIndex(pending, ForcedApps.EMPTY, false, true, SELF));
        assertEquals(-1, AppInstallOrder.nextIndex(pending, ForcedApps.of("kiosk", "2.0"), false, true, SELF));
        assertEquals(-1, AppInstallOrder.nextIndex(new ArrayList<AppInstallOrder.Item>(), ForcedApps.EMPTY, true, true, SELF));
    }
}
