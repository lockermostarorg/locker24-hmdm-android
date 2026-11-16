package com.hmdm.launcher.locker24;

import java.util.List;

public interface QueueStore {
    List<String> load();

    void save(List<String> lines);
}
