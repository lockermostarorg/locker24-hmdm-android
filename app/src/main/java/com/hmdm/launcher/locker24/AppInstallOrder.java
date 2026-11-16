package com.hmdm.launcher.locker24;

import java.util.List;

/** Chooses which pending app a configuration update installs next. */
public final class AppInstallOrder {
    public interface Item {
        String pkg();

        String version();

        boolean remove();
    }

    private AppInstallOrder() {
    }

    /**
     * Index of the next app that may run now, or -1. The agent's own package goes last among the runnable
     * apps because installing it restarts the process.
     */
    public static int nextIndex(List<? extends Item> pending, ForcedApps forced, boolean windowOpen,
                                boolean networkOk, String selfPkg) {
        int selfIndex = -1;
        for (int i = 0; i < pending.size(); i++) {
            Item item = pending.get(i);
            if (!ForcedApps.mayRun(forced, item.pkg(), item.version(), item.remove(), windowOpen, networkOk)) {
                continue;
            }
            if (selfPkg != null && selfPkg.equals(item.pkg()) && !item.remove()) {
                if (selfIndex < 0) {
                    selfIndex = i;
                }
                continue;
            }
            return i;
        }
        return selfIndex;
    }
}
