package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HealthReportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static HealthSnapshot full() {
        HealthSnapshot s = new HealthSnapshot();
        s.ts = 1763290000000L;
        s.trigger = "schedule";
        s.batteryPct = 87;
        s.plugged = 1;
        s.batteryTemperatureTenths = 315;
        s.batteryHealth = 2;
        s.manufacturer = "samsung";
        s.model = "SM-X110";
        s.androidBuild = "AP3A.240905.015.A2.X110XXS5CYJ1";
        s.androidVersion = "15";
        s.sdkInt = 35;
        s.securityPatch = "2026-10-01";
        s.freeStorageMb = 40960L;
        s.freeRamMb = 1536L;
        s.uptimeS = 86400L;
        s.wifiSsid = "\"Locker24\"";
        s.rssiDbm = -61;
        s.ip = "192.168.1.23";
        s.mqttConnected = true;
        s.lastConfigSyncAt = 1763289700000L;
        s.kioskRequired = true;
        s.kioskRunning = true;
        s.adminUnlocked = false;
        s.foregroundPackage = "com.hpmoqa.tablet_client";
        s.appPackage = "com.hpmoqa.tablet_client";
        s.appVersionName = "1.2.8";
        s.appVersionCode = 23L;
        s.agentVersionName = "6.40-l24.5";
        s.agentVersionCode = 1540005L;
        s.watchdogRestarts24h = 0;
        s.appPublishedAt = 1763289950000L;
        Map<String, Object> app = new HashMap<>();
        app.put("locker_short_uid", "HP01");
        app.put("last_api_ok_at", 1763289900000L);
        app.put("last_api_error_at", 1763280000000L);
        app.put("last_api_error", "unlock HTTP 503");
        app.put("app_errors_24h", 2);
        s.appValues = app;
        return s;
    }

    private static String golden() throws Exception {
        try (InputStream in = HealthReportTest.class.getResourceAsStream("/health/report-v1.json")) {
            return new String(in.readAllBytes(), "UTF-8");
        }
    }

    @Test public void matchesGoldenFile() throws Exception {
        JsonNode actual = MAPPER.readTree(HealthReport.toJsonString(full()));
        assertEquals(MAPPER.readTree(golden()), actual);
    }

    @Test public void celsiusFromTenths() {
        assertEquals(31.5, HealthReport.celsius(315), 0.0);
        assertNull(HealthReport.celsius(null));
        HealthSnapshot s = full();
        s.batteryTemperatureTenths = null;
        JsonNode power = HealthReport.toJson(s).get("power");
        assertTrue(power.has("temperature_c"));
        assertTrue(power.get("temperature_c").isNull());
    }

    @Test public void chargeSourceMapping() {
        assertEquals("ac", HealthReport.chargeSource(1));
        assertEquals("usb", HealthReport.chargeSource(2));
        assertEquals("wireless", HealthReport.chargeSource(4));
        assertEquals("dock", HealthReport.chargeSource(8));
        assertEquals("none", HealthReport.chargeSource(0));
        HealthSnapshot s = full();
        s.plugged = 0;
        JsonNode power = HealthReport.toJson(s).get("power");
        assertEquals(false, power.get("charging").asBoolean(true));
        assertEquals("none", power.get("charge_source").asText());
        s.plugged = 2;
        power = HealthReport.toJson(s).get("power");
        assertEquals(true, power.get("charging").asBoolean(false));
        assertEquals("usb", power.get("charge_source").asText());
    }

    @Test public void batteryHealthMapping() {
        assertEquals("good", HealthReport.batteryHealth(2));
        assertEquals("overheat", HealthReport.batteryHealth(3));
        assertEquals("dead", HealthReport.batteryHealth(4));
        assertEquals("over_voltage", HealthReport.batteryHealth(5));
        assertEquals("failure", HealthReport.batteryHealth(6));
        assertEquals("cold", HealthReport.batteryHealth(7));
        assertEquals("unknown", HealthReport.batteryHealth(1));
        assertEquals("unknown", HealthReport.batteryHealth(99));
    }

    @Test public void ssidCleaning() {
        assertEquals("Locker24", HealthReport.cleanSsid("\"Locker24\""));
        assertEquals("Locker24", HealthReport.cleanSsid("Locker24"));
        assertNull(HealthReport.cleanSsid("<unknown ssid>"));
        assertNull(HealthReport.cleanSsid(null));
    }

    @Test public void nullsStayExplicit() {
        HealthSnapshot s = full();
        s.rssiDbm = null;
        JsonNode network = HealthReport.toJson(s).get("network");
        assertTrue(network.has("rssi_dbm"));
        assertTrue(network.get("rssi_dbm").isNull());
    }

    @Test public void appNullUntilPublished() {
        HealthSnapshot s = full();
        s.appValues = null;
        JsonNode json = HealthReport.toJson(s);
        assertTrue(json.has("app"));
        assertTrue(json.get("app").isNull());
    }

    @Test public void appWritesAllKeysWhenPartial() {
        HealthSnapshot s = full();
        s.appValues = new HashMap<>();
        s.appValues.put("locker_short_uid", "HP01");
        s.appPublishedAt = null;
        JsonNode app = HealthReport.toJson(s).get("app");
        assertEquals(6, app.size());
        assertEquals("HP01", app.get("locker_short_uid").asText());
        for (String key : new String[]{"published_at", "last_api_ok_at", "last_api_error_at", "last_api_error", "app_errors_24h"}) {
            assertTrue(key, app.has(key));
            assertTrue(key, app.get(key).isNull());
        }
    }

    @Test public void envelopeKeepsOrder() throws Exception {
        String body = HealthReport.envelope(Arrays.asList("{\"ts\":1}", "{\"ts\":2}"), 99);
        assertEquals(MAPPER.readTree("{\"sent_at\":99,\"reports\":[{\"ts\":1},{\"ts\":2}]}"), MAPPER.readTree(body));
        assertTrue(body.indexOf("\"ts\":1") < body.indexOf("\"ts\":2"));
    }
}
