package com.hmdm.launcher.locker24;

import java.util.ArrayList;
import java.util.List;

/**
 * Disk-backed FIFO of health reports. Each report is one line and must not contain newlines.
 */
public final class HealthQueue {
    public static final int MAX = 288;
    public static final int BATCH = 50;

    private final QueueStore store;
    private final int max;
    private final List<String> lines = new ArrayList<>();

    public HealthQueue(QueueStore store, int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive");
        }
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
        List<String> before = new ArrayList<>(lines);
        lines.add(report);
        trim();
        persist(before);
    }

    public synchronized List<String> peek(int n) {
        return new ArrayList<>(lines.subList(0, Math.max(0, Math.min(n, lines.size()))));
    }

    public synchronized void remove(int n) {
        int count = Math.max(0, Math.min(n, lines.size()));
        if (count == 0) {
            return;
        }
        List<String> before = new ArrayList<>(lines);
        lines.subList(0, count).clear();
        persist(before);
    }

    /**
     * Removes the sent reports that are still at the head of the queue. Reports of {@code sent}
     * that were trimmed off the head in the meantime are skipped. Returns how many were removed.
     */
    public synchronized int removeSent(List<String> sent) {
        if (sent == null || sent.isEmpty() || lines.isEmpty()) {
            return 0;
        }
        int start = sent.indexOf(lines.get(0));
        if (start < 0) {
            return 0;
        }
        int count = 0;
        while (start + count < sent.size() && count < lines.size()
                && lines.get(count).equals(sent.get(start + count))) {
            count++;
        }
        remove(count);
        return count;
    }

    public synchronized int size() {
        return lines.size();
    }

    private void persist(List<String> before) {
        try {
            store.save(new ArrayList<>(lines));
        } catch (RuntimeException e) {
            lines.clear();
            lines.addAll(before);
            throw e;
        }
    }

    private void trim() {
        int excess = lines.size() - max;
        if (excess > 0) {
            lines.subList(0, excess).clear();
        }
    }
}
