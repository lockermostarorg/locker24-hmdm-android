package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hmdm.launcher.json.PushMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ForceAppUpdateTest {
    private static final class Req {
        final Object flow;
        final Object notifier;
        final boolean userInteraction;

        Req(Object flow, Object notifier, boolean userInteraction) {
            this.flow = flow;
            this.notifier = notifier;
            this.userInteraction = userInteraction;
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
                return new Req(next.flow, next.notifier,
                        ConfigFlowPolicy.mergeUserInteraction(pending.userInteraction, next.userInteraction));
            }

            @Override
            public boolean listenerGone(Req r) {
                return false;
            }
        });
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

    @Test public void forceRunsAsASeparateFlowAfterAMainActivityFlowWithoutTheFlag() {
        ConfigFlowGate<Req> gate = gate();
        Object activity = new Object();
        Object updater = new Object();
        Object forceUpdater = new Object();
        assertEquals(ConfigFlowGate.Decision.START, gate.request(new Req(updater, activity, false), 1_000));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(forceUpdater, null, true), 1_001));
        Req next = gate.finish(updater);
        assertSame(forceUpdater, next.flow);
        assertTrue(next.userInteraction);
    }

    private static PushMessage message(String type) {
        PushMessage message = new PushMessage();
        message.setMessageType(type);
        return message;
    }

    @Test public void typeIsSpelledAsTheServerSendsIt() {
        assertEquals("forceAppUpdate", PushMessage.TYPE_FORCE_APP_UPDATE);
    }

    @Test public void forcePushStartsUserInteractionUpdate() {
        List<Boolean> started = new ArrayList<>();
        assertTrue(ForceAppUpdate.handle(message("forceAppUpdate"), started::add));
        assertEquals(1, started.size());
        assertTrue(started.get(0));
    }

    @Test public void otherPushTypesAreNotHandled() {
        List<Boolean> started = new ArrayList<>();
        assertFalse(ForceAppUpdate.handle(message(PushMessage.TYPE_CONFIG_UPDATED), started::add));
        assertFalse(ForceAppUpdate.handle(message("reboot"), started::add));
        assertTrue(started.isEmpty());
    }

    @Test public void configUpdatedThenForceUnderTheBackgroundKeyMergesToOneFlowWithTheFlag() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        gate.request(new Req(flow, null, false), 1_000);
        gate.request(new Req(flow, null, false), 1_001);
        gate.request(new Req(flow, null, true), 1_002);
        Req rerun = gate.finish(flow);
        assertTrue(rerun.userInteraction);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(rerun, 1_010));
        assertNull(gate.finish(flow));
    }

    @Test public void burstOfForcePushesCoalesces() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        List<Req> requests = new ArrayList<>();
        ForceAppUpdate.Starter starter = ui -> requests.add(new Req(flow, null, ui));
        for (int i = 0; i < 3; i++) {
            ForceAppUpdate.handle(message("forceAppUpdate"), starter);
        }
        assertEquals(ConfigFlowGate.Decision.START, gate.request(requests.get(0), 1_000));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(requests.get(1), 1_001));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(requests.get(2), 1_002));

        Req rerun = gate.finish(flow);
        assertTrue(rerun.userInteraction);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(rerun, 1_010));
        assertNull(gate.finish(flow));
    }
}
