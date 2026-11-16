package com.hmdm.launcher.locker24;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The apps a forceAppUpdate push lifts the update window and the Wi-Fi-only rule for. */
public final class ForcedApps {
    public static final long MAX_AGE_MS = 60 * 60_000L;
    public static final long VALID_CLOCK_FROM_MS = 1577836800000L;

    public static final ForcedApps EMPTY = new ForcedApps(Collections.<String>emptySet());

    private final Set<String> entries;

    private ForcedApps(Set<String> entries) {
        this.entries = entries;
    }

    private static String entry(String pkg, String version) {
        return pkg + "\n" + version;
    }

    public static ForcedApps of(String pkg, String version) {
        return new ForcedApps(Collections.singleton(entry(pkg, version)));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public boolean matches(String pkg, String version) {
        return pkg != null && version != null && entries.contains(entry(pkg, version));
    }

    public static ForcedApps union(ForcedApps a, ForcedApps b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Set<String> all = new LinkedHashSet<>(a.entries);
        all.addAll(b.entries);
        return new ForcedApps(Collections.unmodifiableSet(all));
    }

    /** Empty when the payload is missing, malformed or older than MAX_AGE_MS (a clock before 2020 counts as fresh). */
    public static ForcedApps parse(String payload, long nowMs) {
        if (payload == null) {
            return EMPTY;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(payload);
            if (root == null || !root.isObject()) {
                return EMPTY;
            }
            JsonNode issuedAt = root.get("issuedAt");
            if (issuedAt == null || !issuedAt.isIntegralNumber()) {
                return EMPTY;
            }
            if (nowMs >= VALID_CLOCK_FROM_MS && issuedAt.asLong() < nowMs - MAX_AGE_MS) {
                return EMPTY;
            }
            JsonNode apps = root.get("apps");
            if (apps == null || !apps.isArray()) {
                return EMPTY;
            }
            Set<String> parsed = new LinkedHashSet<>();
            for (JsonNode app : apps) {
                JsonNode pkg = app.get("pkg");
                JsonNode version = app.get("version");
                if (pkg != null && pkg.isTextual() && !pkg.asText().isEmpty()
                        && version != null && version.isTextual() && !version.asText().isEmpty()) {
                    parsed.add(entry(pkg.asText(), version.asText()));
                }
            }
            return parsed.isEmpty() ? EMPTY : new ForcedApps(Collections.unmodifiableSet(parsed));
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /** Whether an install, update or removal may run now. Only a matching forced app skips the window and Wi-Fi rules. */
    public static boolean mayRun(ForcedApps forced, String pkg, String version, boolean remove,
                                 boolean windowOk, boolean networkOk) {
        if (windowOk && networkOk) {
            return true;
        }
        return !remove && forced.matches(pkg, version);
    }
}
