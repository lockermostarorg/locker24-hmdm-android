package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.PushMessageJson;

import org.json.JSONObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ForceAppUpdateTest {
    private static final long NOW = 1_800_000_000_000L;

    private static final class Req {
        final Object flow;
        final Object notifier;
        final ForcedApps forced;

        Req(Object flow, Object notifier, ForcedApps forced) {
            this.flow = flow;
            this.notifier = notifier;
            this.forced = forced;
        }
    }

    private static ConfigFlowGate<Req> gate() {
        return new ConfigFlowGate<>(new ConfigFlowGate.Requests<Req>() {
            @Override
            public Object flow(Req r) {
                return r.flow;
            }

            @Override
            public Object key(Req r) {
                return ConfigFlowPolicy.key(r.notifier);
            }

            @Override
            public Req merge(Req pending, Req next) {
                return new Req(next.flow, next.notifier, ConfigFlowPolicy.mergeForced(pending.forced, next.forced));
            }

            @Override
            public boolean listenerGone(Req r) {
                return false;
            }
        });
    }

    private static PushMessage message(String type, String payload) {
        PushMessage message = new PushMessage();
        message.setMessageType(type);
        message.setPayload(payload);
        return message;
    }

    private static String payload(String pkg, String version, long issuedAt) {
        return "{\"apps\":[{\"pkg\":\"" + pkg + "\",\"version\":\"" + version + "\"}],\"issuedAt\":" + issuedAt + "}";
    }

    private static final class Recorder implements ForceAppUpdate.Starter {
        final List<ForcedApps> forced = new ArrayList<>();
        int normal;

        @Override
        public void forced(ForcedApps apps) {
            forced.add(apps);
        }

        @Override
        public void normal() {
            normal++;
        }
    }

    @Test public void nullNotifierUsesTheBackgroundKeyAndOthersTheirOwn() {
        Object activity = new Object();
        assertSame(ConfigFlowPolicy.BACKGROUND, ConfigFlowPolicy.key(null));
        assertSame(activity, ConfigFlowPolicy.key(activity));
    }

    @Test public void mergeOrsUserInteractionAndAndsSkipLoad() {
        assertTrue(ConfigFlowPolicy.mergeUserInteraction(false, true));
        assertTrue(ConfigFlowPolicy.mergeUserInteraction(true, false));
        assertFalse(ConfigFlowPolicy.mergeUserInteraction(false, false));
        assertTrue(ConfigFlowPolicy.mergeSkipLoad(true, true));
        assertFalse(ConfigFlowPolicy.mergeSkipLoad(true, false));
    }

    @Test public void typeIsSpelledAsTheServerSendsIt() {
        assertEquals("forceAppUpdate", PushMessage.TYPE_FORCE_APP_UPDATE);
    }

    @Test public void freshForcePushStartsAForcedUpdateWithTheNamedApps() {
        Recorder recorder = new Recorder();
        assertTrue(ForceAppUpdate.handle(message("forceAppUpdate", payload("kiosk", "2.1.0", NOW - 1000)), NOW, recorder));
        assertEquals(1, recorder.forced.size());
        assertEquals(0, recorder.normal);
        assertTrue(recorder.forced.get(0).matches("kiosk", "2.1.0"));
    }

    @Test public void stalePushStartsANormalUpdate() {
        Recorder recorder = new Recorder();
        assertTrue(ForceAppUpdate.handle(
                message("forceAppUpdate", payload("kiosk", "2.1.0", NOW - 61 * 60_000L)), NOW, recorder));
        assertTrue(recorder.forced.isEmpty());
        assertEquals(1, recorder.normal);
    }

    @Test public void malformedPushStartsANormalUpdate() {
        Recorder recorder = new Recorder();
        assertTrue(ForceAppUpdate.handle(message("forceAppUpdate", "oops"), NOW, recorder));
        assertTrue(ForceAppUpdate.handle(message("forceAppUpdate", null), NOW, recorder));
        assertTrue(recorder.forced.isEmpty());
        assertEquals(2, recorder.normal);
    }

    @Test public void otherPushTypesAreNotHandled() {
        Recorder recorder = new Recorder();
        assertFalse(ForceAppUpdate.handle(message(PushMessage.TYPE_CONFIG_UPDATED, null), NOW, recorder));
        assertFalse(ForceAppUpdate.handle(message("reboot", null), NOW, recorder));
        assertTrue(recorder.forced.isEmpty());
        assertEquals(0, recorder.normal);
    }

    @Test public void configUpdatedThenForceMergeToOneFlowWithTheForcedApps() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        gate.request(new Req(flow, null, ForcedApps.EMPTY), 1_000);
        gate.request(new Req(flow, null, ForcedApps.EMPTY), 1_001);
        gate.request(new Req(flow, null, ForcedApps.of("kiosk", "2.1.0")), 1_002);
        gate.request(new Req(flow, null, ForcedApps.EMPTY), 1_003);
        Req rerun = gate.finish(flow);
        assertTrue(rerun.forced.matches("kiosk", "2.1.0"));
        assertEquals(1, rerun.forced.size());
        assertEquals(ConfigFlowGate.Decision.START, gate.request(rerun, 1_010));
        assertNull(gate.finish(flow));
    }

    @Test public void forcePushesUnionTheirAppsInOneFlow() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        gate.request(new Req(flow, null, ForcedApps.EMPTY), 1_000);
        gate.request(new Req(flow, null, ForcedApps.of("kiosk", "2.1.0")), 1_001);
        gate.request(new Req(flow, null, ForcedApps.of("agent", "6.40-l24.6")), 1_002);
        gate.request(new Req(flow, null, ForcedApps.of("kiosk", "2.1.0")), 1_003);
        Req rerun = gate.finish(flow);
        assertEquals(2, rerun.forced.size());
        assertTrue(rerun.forced.matches("agent", "6.40-l24.6"));
        assertNull(gate.finish(new Object()));
    }

    @Test public void forceRunsAsASeparateFlowAfterAMainActivityFlow() {
        ConfigFlowGate<Req> gate = gate();
        Object activity = new Object();
        Object updater = new Object();
        Object forceUpdater = new Object();
        assertEquals(ConfigFlowGate.Decision.START, gate.request(new Req(updater, activity, ForcedApps.EMPTY), 1_000));
        assertEquals(ConfigFlowGate.Decision.COALESCE,
                gate.request(new Req(forceUpdater, null, ForcedApps.of("kiosk", "2.1.0")), 1_001));
        Req next = gate.finish(updater);
        assertSame(forceUpdater, next.flow);
        assertTrue(next.forced.matches("kiosk", "2.1.0"));
    }

    @Test public void mqttPushWithAJsonObjectPayloadCarriesTheForcedApps() throws Exception {
        JSONObject envelope = new JSONObject("{\"messageType\":\"forceAppUpdate\",\"payload\":"
                + payload("kiosk", "2.1.0", NOW - 1000) + "}");
        PushMessageJson message = new PushMessageJson(envelope.getString("messageType"),
                envelope.optJSONObject("payload"));
        Recorder recorder = new Recorder();
        assertTrue(ForceAppUpdate.handle(message, NOW, recorder));
        assertEquals(0, recorder.normal);
        assertTrue(recorder.forced.get(0).matches("kiosk", "2.1.0"));
    }

    @Test public void mqttPushWithoutAPayloadStartsANormalUpdate() throws Exception {
        JSONObject envelope = new JSONObject("{\"messageType\":\"forceAppUpdate\"}");
        PushMessageJson message = new PushMessageJson(envelope.getString("messageType"),
                envelope.optJSONObject("payload"));
        Recorder recorder = new Recorder();
        assertTrue(ForceAppUpdate.handle(message, NOW, recorder));
        assertEquals(1, recorder.normal);
    }

    @Test public void stringPayloadStillWorks() {
        Recorder recorder = new Recorder();
        ForceAppUpdate.handle(message("forceAppUpdate", payload("kiosk", "2.1.0", NOW)), NOW, recorder);
        assertEquals(1, recorder.forced.size());
    }
}
