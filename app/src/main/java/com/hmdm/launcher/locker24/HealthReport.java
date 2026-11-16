package com.hmdm.launcher.locker24;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class HealthReport {
    public static final int VERSION = 1;

    private static final int PLUGGED_AC = 1;
    private static final int PLUGGED_USB = 2;
    private static final int PLUGGED_WIRELESS = 4;
    private static final int PLUGGED_DOCK = 8;

    private static final int HEALTH_GOOD = 2;
    private static final int HEALTH_OVERHEAT = 3;
    private static final int HEALTH_DEAD = 4;
    private static final int HEALTH_OVER_VOLTAGE = 5;
    private static final int HEALTH_UNSPECIFIED_FAILURE = 6;
    private static final int HEALTH_COLD = 7;

    private static final String[] APP_KEYS = {
            "locker_short_uid", "last_api_ok_at", "last_api_error_at", "last_api_error", "app_errors_24h"
    };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HealthReport() {
    }

    public static ObjectNode toJson(HealthSnapshot s) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("v", VERSION);
        put(root, "ts", s.ts);
        put(root, "trigger", s.trigger);

        ObjectNode power = root.putObject("power");
        put(power, "battery_pct", s.batteryPct);
        if (s.plugged == null) {
            power.putNull("charging");
            power.putNull("charge_source");
        } else {
            power.put("charging", s.plugged != 0);
            power.put("charge_source", chargeSource(s.plugged));
        }
        Double celsius = celsius(s.batteryTemperatureTenths);
        if (celsius == null) {
            power.putNull("temperature_c");
        } else {
            power.put("temperature_c", celsius);
        }
        if (s.batteryHealth == null) {
            power.putNull("battery_health");
        } else {
            power.put("battery_health", batteryHealth(s.batteryHealth));
        }

        ObjectNode system = root.putObject("system");
        put(system, "manufacturer", s.manufacturer);
        put(system, "model", s.model);
        put(system, "android_build", s.androidBuild);
        put(system, "android_version", s.androidVersion);
        put(system, "sdk_int", s.sdkInt);
        put(system, "security_patch", s.securityPatch);
        put(system, "free_storage_mb", s.freeStorageMb);
        put(system, "free_ram_mb", s.freeRamMb);
        put(system, "uptime_s", s.uptimeS);

        ObjectNode network = root.putObject("network");
        put(network, "wifi_ssid", cleanSsid(s.wifiSsid));
        put(network, "rssi_dbm", s.rssiDbm);
        put(network, "ip", s.ip);
        put(network, "mqtt_connected", s.mqttConnected);
        put(network, "last_config_sync_at", s.lastConfigSyncAt);

        ObjectNode kiosk = root.putObject("kiosk");
        put(kiosk, "required", s.kioskRequired);
        put(kiosk, "running", s.kioskRunning);
        put(kiosk, "admin_unlocked", s.adminUnlocked);
        put(kiosk, "foreground_package", s.foregroundPackage);
        put(kiosk, "app_package", s.appPackage);
        put(kiosk, "app_version_name", s.appVersionName);
        put(kiosk, "app_version_code", s.appVersionCode);
        put(kiosk, "agent_version_name", s.agentVersionName);
        put(kiosk, "agent_version_code", s.agentVersionCode);
        put(kiosk, "watchdog_restarts_24h", s.watchdogRestarts24h);

        if (s.appValues == null) {
            root.putNull("app");
        } else {
            ObjectNode app = root.putObject("app");
            put(app, "published_at", s.appPublishedAt);
            for (String key : APP_KEYS) {
                putValue(app, key, s.appValues, key);
            }
        }
        return root;
    }

    public static String toJsonString(HealthSnapshot s) {
        return toJson(s).toString();
    }

    public static String envelope(List<String> reports, long sentAt) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("sent_at", sentAt);
        ArrayNode array = root.putArray("reports");
        for (String report : reports) {
            try {
                array.add(MAPPER.readTree(report));
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid report JSON", e);
            }
        }
        return root.toString();
    }

    public static String chargeSource(int plugged) {
        if ((plugged & PLUGGED_AC) != 0) {
            return "ac";
        }
        if ((plugged & PLUGGED_USB) != 0) {
            return "usb";
        }
        if ((plugged & PLUGGED_WIRELESS) != 0) {
            return "wireless";
        }
        if ((plugged & PLUGGED_DOCK) != 0) {
            return "dock";
        }
        return "none";
    }

    public static String batteryHealth(int health) {
        switch (health) {
            case HEALTH_GOOD:
                return "good";
            case HEALTH_OVERHEAT:
                return "overheat";
            case HEALTH_DEAD:
                return "dead";
            case HEALTH_OVER_VOLTAGE:
                return "over_voltage";
            case HEALTH_UNSPECIFIED_FAILURE:
                return "failure";
            case HEALTH_COLD:
                return "cold";
            default:
                return "unknown";
        }
    }

    public static String cleanSsid(String ssid) {
        if (ssid == null) {
            return null;
        }
        String s = ssid.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty() || "<unknown ssid>".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    public static Double celsius(Integer tenths) {
        return tenths == null ? null : tenths / 10.0;
    }

    private static void putValue(ObjectNode node, String name, Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            node.putNull(name);
        } else {
            node.set(name, MAPPER.valueToTree(value));
        }
    }

    private static void put(ObjectNode node, String key, String v) {
        if (v == null) {
            node.putNull(key);
        } else {
            node.put(key, v);
        }
    }

    private static void put(ObjectNode node, String key, Integer v) {
        if (v == null) {
            node.putNull(key);
        } else {
            node.put(key, v.intValue());
        }
    }

    private static void put(ObjectNode node, String key, Long v) {
        if (v == null) {
            node.putNull(key);
        } else {
            node.put(key, v.longValue());
        }
    }

    private static void put(ObjectNode node, String key, Boolean v) {
        if (v == null) {
            node.putNull(key);
        } else {
            node.put(key, v.booleanValue());
        }
    }
}
