package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HealthValuesTest {

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }

    @Test
    public void keepsOnlyTheFiveKeys() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("locker_short_uid", "abc");
        raw.put("last_api_ok_at", 1L);
        raw.put("last_api_error_at", 2L);
        raw.put("last_api_error", "boom");
        raw.put("app_errors_24h", 3);
        raw.put("foo", "bar");
        Map<String, Object> out = HealthValues.sanitize(raw);
        assertEquals(5, out.size());
        assertFalse(out.containsKey("foo"));
        assertEquals("abc", out.get("locker_short_uid"));
        assertEquals(3, out.get("app_errors_24h"));
    }

    @Test
    public void typesAreEnforced() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("last_api_ok_at", "123");
        raw.put("last_api_error_at", 123);
        raw.put("app_errors_24h", -1);
        Map<String, Object> out = HealthValues.sanitize(raw);
        assertFalse(out.containsKey("last_api_ok_at"));
        assertEquals(123L, out.get("last_api_error_at"));
        assertFalse(out.containsKey("app_errors_24h"));

        Map<String, Object> longs = new HashMap<>();
        longs.put("last_api_ok_at", 123L);
        longs.put("app_errors_24h", 7L);
        Map<String, Object> out2 = HealthValues.sanitize(longs);
        assertEquals(123L, out2.get("last_api_ok_at"));
        assertEquals(7, out2.get("app_errors_24h"));
    }

    @Test
    public void truncates() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("last_api_error", repeat('e', 500));
        raw.put("locker_short_uid", repeat('u', 100));
        Map<String, Object> out = HealthValues.sanitize(raw);
        assertEquals(200, ((String) out.get("last_api_error")).length());
        assertEquals(64, ((String) out.get("locker_short_uid")).length());
    }

    @Test
    public void onlyTheKioskAppMayPublish() {
        assertTrue(HealthValues.isAllowedCaller(new String[]{"com.hpmoqa.tablet_client"}, "com.hpmoqa.tablet_client"));
        assertFalse(HealthValues.isAllowedCaller(new String[]{"com.other"}, "com.hpmoqa.tablet_client"));
        assertFalse(HealthValues.isAllowedCaller(null, "x"));
        assertFalse(HealthValues.isAllowedCaller(new String[]{"x"}, null));
    }

    @Test
    public void nullInputGivesEmpty() {
        assertTrue(HealthValues.sanitize(null).isEmpty());
        assertNull(HealthValues.sanitize(new HashMap<String, Object>()).get("foo"));
    }
}
