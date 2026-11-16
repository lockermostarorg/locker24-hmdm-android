package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileQueueStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void missingFileLoadsEmpty() {
        FileQueueStore store = new FileQueueStore(new File(tmp.getRoot(), "q.jsonl"));
        assertEquals(Collections.emptyList(), store.load());
    }

    @Test
    public void savedLinesLoadBack() {
        File file = new File(tmp.getRoot(), "q.jsonl");
        new FileQueueStore(file).save(Arrays.asList("{\"a\":1}", "{\"b\":\"č\"}"));
        assertEquals(Arrays.asList("{\"a\":1}", "{\"b\":\"č\"}"), new FileQueueStore(file).load());
    }

    @Test
    public void saveReplacesContentAndLeavesNoTempFile() {
        File file = new File(tmp.getRoot(), "q.jsonl");
        FileQueueStore store = new FileQueueStore(file);
        store.save(Arrays.asList("1", "2", "3"));
        store.save(Collections.singletonList("4"));
        assertEquals(Collections.singletonList("4"), store.load());
        assertEquals(1, tmp.getRoot().listFiles().length);
        store.save(new ArrayList<String>());
        assertEquals(Collections.emptyList(), store.load());
    }

    @Test
    public void failedSaveThrowsAndKeepsThePreviousFile() throws Exception {
        File file = new File(tmp.getRoot(), "q.jsonl");
        FileQueueStore store = new FileQueueStore(file);
        store.save(Collections.singletonList("old"));
        File temp = new File(tmp.getRoot(), "q.jsonl.tmp");
        assertTrue(temp.mkdir());
        try {
            store.save(Collections.singletonList("new"));
            fail("expected a RuntimeException");
        } catch (RuntimeException expected) {
        }
        assertEquals(Collections.singletonList("old"), store.load());
    }

    @Test
    public void saveIntoMissingDirectoryThrows() {
        FileQueueStore store = new FileQueueStore(new File(new File(tmp.getRoot(), "gone"), "q.jsonl"));
        try {
            store.save(Collections.singletonList("x"));
            fail("expected a RuntimeException");
        } catch (RuntimeException expected) {
        }
    }

    @Test
    public void blankLinesAndTrailingNewlinesAreIgnored() throws Exception {
        File file = new File(tmp.getRoot(), "q.jsonl");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("a\n\n  \nb\n".getBytes(StandardCharsets.UTF_8));
        }
        List<String> lines = new FileQueueStore(file).load();
        assertEquals(Arrays.asList("a", "b"), lines);
        assertFalse(lines.contains(""));
    }

    @Test
    public void worksAsTheQueueStore() {
        File file = new File(tmp.getRoot(), "q.jsonl");
        HealthQueue queue = new HealthQueue(new FileQueueStore(file), HealthQueue.MAX);
        queue.add("r1");
        queue.add("r2");
        HealthQueue reloaded = new HealthQueue(new FileQueueStore(file), HealthQueue.MAX);
        assertEquals(Arrays.asList("r1", "r2"), reloaded.peek(HealthQueue.BATCH));
        reloaded.removeSent(Collections.singletonList("r1"));
        assertEquals(Collections.singletonList("r2"), new FileQueueStore(file).load());
    }
}
