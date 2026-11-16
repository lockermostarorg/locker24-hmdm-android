package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HealthQueueTest {
    private static final class MemoryStore implements QueueStore {
        List<String> lines = new ArrayList<>();

        @Override
        public List<String> load() {
            return new ArrayList<>(lines);
        }

        @Override
        public void save(List<String> lines) {
            this.lines = new ArrayList<>(lines);
        }
    }

    private static HealthQueue fill(QueueStore store, int count) {
        HealthQueue queue = new HealthQueue(store, HealthQueue.MAX);
        for (int i = 1; i <= count; i++) {
            queue.add("r" + i);
        }
        return queue;
    }

    @Test
    public void dropsOldestBeyond288() {
        HealthQueue queue = fill(new MemoryStore(), 289);
        assertEquals(288, queue.size());
        assertEquals(Collections.singletonList("r2"), queue.peek(1));
    }

    @Test
    public void peeksOldestFirstInBatches() {
        HealthQueue queue = fill(new MemoryStore(), 120);
        List<String> batch = queue.peek(HealthQueue.BATCH);
        assertEquals(50, batch.size());
        assertEquals("r1", batch.get(0));
        assertEquals("r50", batch.get(49));
        queue.remove(50);
        assertEquals(Collections.singletonList("r51"), queue.peek(1));
        assertEquals(70, queue.size());
    }

    @Test
    public void survivesReload() {
        MemoryStore store = new MemoryStore();
        HealthQueue first = fill(store, 120);
        HealthQueue second = new HealthQueue(store, HealthQueue.MAX);
        assertEquals(120, second.size());
        assertEquals(first.peek(120), second.peek(120));
    }

    @Test
    public void blankLinesIgnored() {
        MemoryStore store = new MemoryStore();
        store.lines = new ArrayList<>(Arrays.asList("a", "", "b"));
        HealthQueue queue = new HealthQueue(store, HealthQueue.MAX);
        assertEquals(2, queue.size());
        assertEquals(Arrays.asList("a", "b"), queue.peek(5));
    }

    @Test
    public void removeMoreThanSizeEmptiesQueue() {
        HealthQueue queue = fill(new MemoryStore(), 3);
        queue.remove(10);
        assertEquals(0, queue.size());
    }

    @Test
    public void oversizedStoreIsTrimmedOnLoad() {
        MemoryStore store = new MemoryStore();
        for (int i = 1; i <= 300; i++) {
            store.lines.add("r" + i);
        }
        HealthQueue queue = new HealthQueue(store, HealthQueue.MAX);
        assertEquals(288, queue.size());
        assertEquals(Collections.singletonList("r13"), queue.peek(1));
    }
}
