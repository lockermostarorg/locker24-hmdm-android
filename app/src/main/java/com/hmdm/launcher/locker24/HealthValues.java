package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.RemoteLogger;

import java.util.HashMap;
import java.util.Map;

public final class HealthValues {
    private static final String PREFS = "locker24_health";
    private static final String KEY_VALUES = "app_values";
    private static final String KEY_PUBLISHED_AT = "app_published_at";
    private static final String KEY_PACKAGE = "app_values_pkg";
    private static final long LOG_INTERVAL_MS = 60_000L;
    private static final Map<String, Long> LAST_LOGGED = new HashMap<>();

    private static final String[] KEYS = {
            "locker_short_uid", "last_api_ok_at", "last_api_error_at", "last_api_error", "app_errors_24h"
    };

    private static final int MAX_UID = 64;
    private static final int MAX_ERROR = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HealthValues() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> raw) {
        Map<String, Object> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        putString(out, raw, "locker_short_uid", MAX_UID);
        putString(out, raw, "last_api_error", MAX_ERROR);
        putLong(out, raw, "last_api_ok_at");
        putLong(out, raw, "last_api_error_at");
        Object errors = raw.get("app_errors_24h");
        if (errors instanceof Integer || errors instanceof Long) {
            long n = ((Number) errors).longValue();
            if (n >= 0 && n <= Integer.MAX_VALUE) {
                out.put("app_errors_24h", (int) n);
            }
        }
        return out;
    }

    public static boolean isAllowedCaller(String[] callerPackages, String mainApp) {
        if (callerPackages == null || mainApp == null) {
            return false;
        }
        for (String p : callerPackages) {
            if (mainApp.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean publish(Context context, int callingUid, Bundle values) {
        try {
            if (values == null) {
                return false;
            }
            ServerConfig config = SettingsHelper.getInstance(context).getConfig();
            if (config == null) {
                reject(context, "no config", null);
                return false;
            }
            String mainApp = config.getMainApp();
            if (mainApp == null) {
                reject(context, "no main app configured", null);
                return false;
            }
            String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
            if (!isAllowedCaller(packages, mainApp)) {
                reject(context, "caller not allowed", packages == null ? "none" : TextUtils.join(",", packages));
                return false;
            }
            Map<String, Object> raw = new HashMap<>();
            for (String key : values.keySet()) {
                raw.put(key, values.get(key));
            }
            Map<String, Object> clean = sanitize(raw);
            if (clean.isEmpty()) {
                reject(context, "empty after sanitizing", null);
                return false;
            }
            ObjectNode node = MAPPER.createObjectNode();
            for (Map.Entry<String, Object> e : clean.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Long) {
                    node.put(e.getKey(), (Long) v);
                } else if (v instanceof Integer) {
                    node.put(e.getKey(), (Integer) v);
                } else {
                    node.put(e.getKey(), (String) v);
                }
            }
            return prefs(context).edit()
                    .putString(KEY_VALUES, MAPPER.writeValueAsString(node))
                    .putString(KEY_PACKAGE, mainApp)
                    .putLong(KEY_PUBLISHED_AT, System.currentTimeMillis())
                    .commit();
        } catch (Exception e) {
            reject(context, "exception", e.getClass().getSimpleName());
            return false;
        }
    }

    private static Map<String, Object> parse(String json) throws java.io.IOException {
        JsonNode node = MAPPER.readTree(json);
        Map<String, Object> raw = new HashMap<>();
        for (String key : KEYS) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isTextual()) {
                raw.put(key, v.asText());
            } else if (v.isIntegralNumber()) {
                raw.put(key, v.asLong());
            }
        }
        return sanitize(raw);
    }

    /** Callers needing both the values and publishedAt must use {@link #snapshot} once. */
    public static Map<String, Object> latest(Context context) {
        Snapshot s = snapshot(context);
        return s == null ? null : s.values;
    }

    /** Callers needing both the values and publishedAt must use {@link #snapshot} once. */
    public static Long publishedAt(Context context) {
        Snapshot s = snapshot(context);
        return s == null ? null : s.publishedAt;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void putString(Map<String, Object> out, Map<String, Object> raw, String key, int max) {
        Object v = raw.get(key);
        if (v instanceof String) {
            String s = (String) v;
            out.put(key, truncate(s, max));
        }
    }

    private static void putLong(Map<String, Object> out, Map<String, Object> raw, String key) {
        Object v = raw.get(key);
        if (v instanceof Integer || v instanceof Long) {
            long n = ((Number) v).longValue();
            if (n > 0) {
                out.put(key, n);
            }
        }
    }

    static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = max;
        if (Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    public static boolean isCurrent(String storedPackage, String mainApp) {
        return storedPackage != null && storedPackage.equals(mainApp);
    }

    static boolean shouldLog(Map<String, Long> last, String reason, long now) {
        Long prev = last.get(reason);
        if (prev != null && now - prev < LOG_INTERVAL_MS && now >= prev) {
            return false;
        }
        last.put(reason, now);
        return true;
    }

    private static void reject(Context context, String reason, String detail) {
        try {
            boolean log;
            synchronized (LAST_LOGGED) {
                log = shouldLog(LAST_LOGGED, reason, System.currentTimeMillis());
            }
            if (!log) {
                return;
            }
            String message = "publishHealth rejected: " + reason + (detail == null ? "" : " (" + detail + ")");
            Log.w(Const.LOG_TAG, message);
            RemoteLogger.log(context, Const.LOG_WARN, message);
        } catch (Exception ignored) {
        }
    }

    private static String mainApp(Context context) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        return config == null ? null : config.getMainApp();
    }

    public static final class Snapshot {
        public final Map<String, Object> values;
        public final Long publishedAt;

        Snapshot(Map<String, Object> values, Long publishedAt) {
            this.values = values;
            this.publishedAt = publishedAt;
        }
    }

    public static Snapshot snapshot(Context context) {
        try {
            Map<String, ?> all = prefs(context).getAll();
            Object json = all.get(KEY_VALUES);
            Object pkg = all.get(KEY_PACKAGE);
            if (!(json instanceof String) || !(pkg instanceof String)
                    || !isCurrent((String) pkg, mainApp(context))) {
                return null;
            }
            Object at = all.get(KEY_PUBLISHED_AT);
            return new Snapshot(parse((String) json), at instanceof Long ? (Long) at : null);
        } catch (Exception e) {
            return null;
        }
    }
}
