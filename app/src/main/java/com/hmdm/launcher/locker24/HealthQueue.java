package com.hmdm.launcher.locker24;

import java.util.ArrayList;
import java.util.List;

public final class HealthQueue {
    public static final int MAX = 288;
    public static final int BATCH = 50;

    private final QueueStore store;
    private final int max;
    private final List<String> lines = new ArrayList<>();

    public HealthQueue(QueueStore store, int max) {
        this.store = store;
        this.max = max;
        List<String> loaded = store.load();
        if (loaded != null) {
            for (String line : loaded) {
                if (line != null && !line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        trim();
    }

    public synchronized void add(String report) {
        if (report == null || report.trim().isEmpty()) {
            return;
        }
        lines.add(report);
        trim();
        store.save(lines);
    }

    public synchronized List<String> peek(int n) {
        return new ArrayList<>(lines.subList(0, Math.max(0, Math.min(n, lines.size()))));
    }

    public synchronized void remove(int n) {
        int count = Math.max(0, Math.min(n, lines.size()));
        if (count == 0) {
            return;
        }
        lines.subList(0, count).clear();
        store.save(lines);
    }

    public synchronized int size() {
        return lines.size();
    }

    private void trim() {
        int excess = lines.size() - max;
        if (excess > 0) {
            lines.subList(0, excess).clear();
        }
    }
}
