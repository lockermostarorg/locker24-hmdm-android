package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LogDrainTest {

    private static class FakeStore implements LogDrain.Store<Integer> {
        final List<Integer> rows = new ArrayList<>();
        final Map<Integer, Integer> uploads = new HashMap<>();
        final AtomicInteger uploadCalls = new AtomicInteger();
        volatile boolean uploadFails;
        volatile boolean deleteFails;
        volatile CountDownLatch uploadEntered;
        volatile CountDownLatch uploadRelease;

        synchronized void insert(int id) {
            rows.add(id);
        }

        @Override
        public synchronized List<Integer> select(int limit) {
            return new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        }

        @Override
        public boolean upload(List<Integer> items) {
            uploadCalls.incrementAndGet();
            CountDownLatch entered = uploadEntered;
            CountDownLatch release = uploadRelease;
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (uploadFails) {
                return false;
            }
            synchronized (this) {
                for (Integer id : items) {
                    uploads.merge(id, 1, Integer::sum);
                }
            }
            return true;
        }

        @Override
        public synchronized boolean delete(List<Integer> items) {
            if (deleteFails) {
                return false;
            }
            rows.removeAll(items);
            return true;
        }

        synchronized void assertEachUploadedOnce(int count) {
            assertEquals(count, uploads.size());
            for (Map.Entry<Integer, Integer> e : uploads.entrySet()) {
                assertEquals("row " + e.getKey(), Integer.valueOf(1), e.getValue());
            }
        }
    }

    @Test public void drainsAllRowsInBatches() {
        FakeStore store = new FakeStore();
        for (int i = 0; i < 25; i++) {
            store.insert(i);
        }
        assertEquals(LogDrain.Outcome.EMPTY, new LogDrain<Integer>().drain(store, 10));
        store.assertEachUploadedOnce(25);
        assertEquals(3, store.uploadCalls.get());
        assertTrue(store.rows.isEmpty());
    }

    @Test public void secondDrainWhileOneRunsReturnsBusyAndRowsGoUpOnce() throws Exception {
        FakeStore store = new FakeStore();
        for (int i = 0; i < 15; i++) {
            store.insert(i);
        }
        store.uploadEntered = new CountDownLatch(1);
        store.uploadRelease = new CountDownLatch(1);
        LogDrain<Integer> drain = new LogDrain<>();
        LogDrain.Outcome[] first = new LogDrain.Outcome[1];
        Thread worker = new Thread(() -> first[0] = drain.drain(store, 10));
        worker.start();
        assertTrue(store.uploadEntered.await(5, TimeUnit.SECONDS));

        assertEquals(LogDrain.Outcome.BUSY, drain.drain(store, 10));
        store.insert(100);

        store.uploadRelease.countDown();
        worker.join(5000);
        assertEquals(LogDrain.Outcome.EMPTY, first[0]);
        store.assertEachUploadedOnce(16);
    }

    @Test public void concurrentDrainAttemptsUploadEachRowOnce() throws Exception {
        FakeStore store = new FakeStore();
        LogDrain<Integer> drain = new LogDrain<>();
        int rows = 2000;
        Thread producer = new Thread(() -> {
            for (int i = 0; i < rows; i++) {
                store.insert(i);
            }
        });
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            workers.add(new Thread(() -> {
                for (int n = 0; n < 500; n++) {
                    drain.drain(store, 10);
                }
            }));
        }
        producer.start();
        for (Thread w : workers) {
            w.start();
        }
        producer.join(10_000);
        for (Thread w : workers) {
            w.join(10_000);
        }
        drain.drain(store, 10);
        store.assertEachUploadedOnce(rows);
    }

    @Test public void failingDeleteStopsTheDrain() {
        FakeStore store = new FakeStore();
        for (int i = 0; i < 5; i++) {
            store.insert(i);
        }
        store.deleteFails = true;
        assertEquals(LogDrain.Outcome.DELETE_FAILED, new LogDrain<Integer>().drain(store, 10));
        assertEquals(1, store.uploadCalls.get());
        assertEquals(5, store.rows.size());
    }

    @Test public void failingUploadStopsTheDrainAndKeepsTheRows() {
        FakeStore store = new FakeStore();
        store.insert(1);
        store.uploadFails = true;
        assertEquals(LogDrain.Outcome.UPLOAD_FAILED, new LogDrain<Integer>().drain(store, 10));
        assertEquals(1, store.uploadCalls.get());
        assertEquals(1, store.rows.size());
    }

    @Test public void exceptionReleasesTheGuard() {
        LogDrain<Integer> drain = new LogDrain<>();
        LogDrain.Store<Integer> throwing = new FakeStore() {
            @Override
            public synchronized List<Integer> select(int limit) {
                throw new IllegalStateException("db");
            }
        };
        try {
            drain.drain(throwing, 10);
        } catch (IllegalStateException expected) {
        }
        assertEquals(LogDrain.Outcome.EMPTY, drain.drain(new FakeStore(), 10));
    }
}
