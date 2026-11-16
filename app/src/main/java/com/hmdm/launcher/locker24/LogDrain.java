package com.hmdm.launcher.locker24;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uploads stored log rows batch by batch and deletes each batch once the server accepted it.
 * Only one drain runs at a time: a second attempt returns BUSY at once, because the running
 * drain keeps selecting until the store is empty. A failed upload or delete ends the drain, so
 * rows that cannot be deleted are not uploaded again in a loop.
 */
public final class LogDrain<T> {

    public interface Store<T> {
        List<T> select(int limit);

        boolean upload(List<T> items);

        boolean delete(List<T> items);
    }

    public enum Outcome { EMPTY, BUSY, UPLOAD_FAILED, DELETE_FAILED }

    private final AtomicBoolean running = new AtomicBoolean();

    public Outcome drain(Store<T> store, int batchSize) {
        if (!running.compareAndSet(false, true)) {
            return Outcome.BUSY;
        }
        try {
            while (true) {
                List<T> items = store.select(batchSize);
                if (items.isEmpty()) {
                    return Outcome.EMPTY;
                }
                if (!store.upload(items)) {
                    return Outcome.UPLOAD_FAILED;
                }
                if (!store.delete(items)) {
                    return Outcome.DELETE_FAILED;
                }
            }
        } finally {
            running.set(false);
        }
    }
}
