package com.hmdm.launcher.locker24;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;

import java.util.HashMap;
import java.util.Map;

public final class HealthValues {
    private static final String PREFS = "locker24_health";
    private static final String KEY_VALUES = "app_values";
    private static final String KEY_PUBLISHED_AT = "app_published_at";

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
            String mainApp = config == null ? null : config.getMainApp();
            String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
            if (!isAllowedCaller(packages, mainApp)) {
                return false;
            }
            Map<String, Object> raw = new HashMap<>();
            for (String key : values.keySet()) {
                raw.put(key, values.get(key));
            }
            Map<String, Object> clean = sanitize(raw);
            if (clean.isEmpty()) {
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
                    .putLong(KEY_PUBLISHED_AT, System.currentTimeMillis())
                    .commit();
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, Object> latest(Context context) {
        try {
            String json = prefs(context).getString(KEY_VALUES, null);
            if (json == null) {
                return null;
            }
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
        } catch (Exception e) {
            return null;
        }
    }

    public static Long publishedAt(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_PUBLISHED_AT) ? p.getLong(KEY_PUBLISHED_AT, 0L) : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void putString(Map<String, Object> out, Map<String, Object> raw, String key, int max) {
        Object v = raw.get(key);
        if (v instanceof String) {
            String s = (String) v;
            out.put(key, s.length() > max ? s.substring(0, max) : s);
        }
    }

    private static void putLong(Map<String, Object> out, Map<String, Object> raw, String key) {
        Object v = raw.get(key);
        if (v instanceof Integer || v instanceof Long) {
            out.put(key, ((Number) v).longValue());
        }
    }
}
