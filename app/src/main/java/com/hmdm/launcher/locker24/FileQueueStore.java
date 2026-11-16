package com.hmdm.launcher.locker24;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** One report per line. A save writes a temporary file and renames it over the queue file. */
public final class FileQueueStore implements QueueStore {
    public static final String FILE_NAME = "locker24_health_queue.jsonl";

    private final File file;
    private final File temp;

    public FileQueueStore(File file) {
        this.file = file;
        this.temp = new File(file.getPath() + ".tmp");
    }

    public static FileQueueStore forContext(Context context) {
        return new FileQueueStore(new File(context.getApplicationContext().getFilesDir(), FILE_NAME));
    }

    @Override
    public List<String> load() {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return lines;
    }

    @Override
    public void save(List<String> lines) {
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                for (String line : lines) {
                    writer.write(line);
                    writer.write('\n');
                }
                writer.flush();
                out.getFD().sync();
            }
            if (!temp.renameTo(file)) {
                throw new IOException("Cannot rename " + temp.getName() + " to " + file.getName());
            }
        } catch (IOException e) {
            if (temp.isFile()) {
                temp.delete();
            }
            throw new IllegalStateException("Cannot save the health queue: " + e.getMessage(), e);
        }
    }
}
