package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HealthQueueTest {
    private static class MemoryStore implements QueueStore {
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

    @Test
    public void exactly288DropsNothing() {
        HealthQueue queue = fill(new MemoryStore(), 288);
        assertEquals(288, queue.size());
        assertEquals(Collections.singletonList("r1"), queue.peek(1));
    }

    @Test
    public void peekOnEmptyAndNonPositive() {
        HealthQueue empty = new HealthQueue(new MemoryStore(), HealthQueue.MAX);
        assertEquals(Collections.emptyList(), empty.peek(5));
        HealthQueue queue = fill(new MemoryStore(), 3);
        assertEquals(Collections.emptyList(), queue.peek(0));
        assertEquals(Collections.emptyList(), queue.peek(-1));
    }

    @Test
    public void reloadAfterRemove() {
        MemoryStore store = new MemoryStore();
        HealthQueue queue = fill(store, 10);
        queue.remove(4);
        HealthQueue reloaded = new HealthQueue(store, HealthQueue.MAX);
        assertEquals(queue.peek(10), reloaded.peek(10));
        assertEquals(6, reloaded.size());
    }

    @Test
    public void reloadAfterOverflowTrim() {
        MemoryStore store = new MemoryStore();
        fill(store, 300);
        HealthQueue reloaded = new HealthQueue(store, HealthQueue.MAX);
        assertEquals(288, reloaded.size());
        assertEquals(Collections.singletonList("r13"), reloaded.peek(1));
    }

    @Test
    public void removeSentSurvivesOverflowRace() {
        HealthQueue queue = fill(new MemoryStore(), 288);
        List<String> peeked = queue.peek(50);
        queue.add("r289");
        assertEquals(49, queue.removeSent(peeked));
        assertEquals(239, queue.size());
        assertEquals(Collections.singletonList("r51"), queue.peek(1));
        List<String> all = queue.peek(300);
        assertEquals("r289", all.get(all.size() - 1));
    }

    @Test
    public void removeSentWithoutRaceRemovesAll() {
        HealthQueue queue = fill(new MemoryStore(), 120);
        assertEquals(50, queue.removeSent(queue.peek(50)));
        assertEquals(Collections.singletonList("r51"), queue.peek(1));
    }

    @Test
    public void removeSentWhollyTrimmedRemovesNothing() {
        HealthQueue queue = fill(new MemoryStore(), 288);
        List<String> peeked = queue.peek(2);
        for (int i = 0; i < 5; i++) {
            queue.add("n" + i);
        }
        assertEquals(0, queue.removeSent(peeked));
        assertEquals(288, queue.size());
    }

    @Test
    public void failedSaveRollsBackMemory() {
        final boolean[] fail = {false};
        MemoryStore store = new MemoryStore() {
            @Override
            public void save(List<String> lines) {
                if (fail[0]) {
                    throw new IllegalStateException("disk");
                }
                super.save(lines);
            }
        };
        HealthQueue queue = fill(store, 3);
        fail[0] = true;
        try {
            queue.add("x");
            org.junit.Assert.fail();
        } catch (IllegalStateException expected) {
        }
        try {
            queue.remove(1);
            org.junit.Assert.fail();
        } catch (IllegalStateException expected) {
        }
        assertEquals(Arrays.asList("r1", "r2", "r3"), queue.peek(10));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveMaxRejected() {
        new HealthQueue(new MemoryStore(), 0);
    }
}
