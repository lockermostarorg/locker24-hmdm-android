package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConfigFlowGateTest {
    private static final long T = 1_000_000;
    private static final long STALE = ConfigFlowGate.STALE_MS;

    private static final class Req {
        final Object flow;
        final Object key;
        final boolean userInteraction;
        boolean listenerGone;

        Req(Object flow, Object key, boolean userInteraction) {
            this.flow = flow;
            this.key = key;
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
                return r.key;
            }

            @Override
            public Req merge(Req pending, Req next) {
                return new Req(next.flow, next.key, pending.userInteraction || next.userInteraction);
            }

            @Override
            public boolean listenerGone(Req r) {
                return r.listenerGone;
            }
        });
    }

    @Test public void decideStartsWhenIdle() {
        assertEquals(ConfigFlowGate.Decision.START, ConfigFlowGate.decide(false, 0, false, false, T));
    }

    @Test public void decideCoalescesWhileAFlowRuns() {
        assertEquals(ConfigFlowGate.Decision.COALESCE, ConfigFlowGate.decide(true, T, false, false, T + 1));
        assertEquals(ConfigFlowGate.Decision.COALESCE, ConfigFlowGate.decide(true, T, false, true, T + STALE - 1));
    }

    @Test public void decideTakesOverAStaleFlow() {
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_STALE, ConfigFlowGate.decide(true, T, false, false, T + STALE));
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_STALE, ConfigFlowGate.decide(true, T, false, true, T + STALE));
    }

    @Test public void decideTakesOverAFlowWhoseListenerIsGoneOnlyForAnotherRequester() {
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_LISTENER_GONE,
                ConfigFlowGate.decide(true, T, true, false, T + 1));
        assertEquals(ConfigFlowGate.Decision.COALESCE, ConfigFlowGate.decide(true, T, true, true, T + 1));
    }

    @Test public void requestsDuringAFlowRunItOnceMoreAfterwards() {
        ConfigFlowGate<Req> gate = gate();
        Object launcher = new Object();
        Req first = new Req(launcher, launcher, false);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(first, T));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(launcher, launcher, false), T + 1));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(launcher, launcher, true), T + 2));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(launcher, launcher, false), T + 3));

        Req rerun = gate.finish(launcher);
        assertSame(launcher, rerun.flow);
        assertTrue(rerun.userInteraction);

        assertEquals(ConfigFlowGate.Decision.START, gate.request(rerun, T + 10));
        assertNull(gate.finish(launcher));
        assertEquals(ConfigFlowGate.Decision.START, gate.request(new Req(launcher, launcher, false), T + 20));
    }

    @Test public void configUpdatedThenForceRunsOneFlowWithUserInteraction() {
        ConfigFlowGate<Req> gate = gate();
        Object background = new Object();
        Object flow = new Object();
        assertEquals(ConfigFlowGate.Decision.START, gate.request(new Req(flow, background, false), T));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(flow, background, false), T + 1));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(flow, background, true), T + 2));

        Req rerun = gate.finish(flow);
        assertTrue(rerun.userInteraction);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(rerun, T + 3));
        assertNull(gate.finish(flow));
        assertNull(gate.takeNext());
    }

    @Test public void forceThenConfigUpdatedKeepsUserInteraction() {
        ConfigFlowGate<Req> gate = gate();
        Object background = new Object();
        Object flow = new Object();
        gate.request(new Req(flow, background, false), T);
        gate.request(new Req(flow, background, true), T + 1);
        gate.request(new Req(flow, background, false), T + 2);
        assertTrue(gate.finish(flow).userInteraction);
    }

    @Test public void requestsFromDifferentListenersRunOneAfterAnother() {
        ConfigFlowGate<Req> gate = gate();
        Object launcher = new Object();
        Object plugin = new Object();
        Object background = new Object();
        gate.request(new Req(launcher, launcher, false), T);
        gate.request(new Req(plugin, plugin, true), T + 1);
        gate.request(new Req(new Object(), background, false), T + 2);
        gate.request(new Req(new Object(), background, false), T + 3);

        Req next = gate.finish(launcher);
        assertSame(plugin, next.flow);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(next, T + 4));
        Req last = gate.finish(plugin);
        assertSame(background, last.key);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(last, T + 5));
        assertNull(gate.finish(last.flow));
    }

    @Test public void staleFlowIsTakenOverAndItsLateFinishIsIgnored() {
        ConfigFlowGate<Req> gate = gate();
        Object old = new Object();
        Object fresh = new Object();
        gate.request(new Req(old, old, false), T);
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_STALE, gate.request(new Req(fresh, fresh, false), T + STALE));
        assertTrue(gate.superseded(old));
        assertFalse(gate.superseded(fresh));

        assertNull(gate.finish(old));
        assertTrue(gate.superseded(old));
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(old, old, false), T + STALE + 1));
    }

    @Test public void newLauncherInstanceTakesOverTheFlowOfADestroyedOne() {
        ConfigFlowGate<Req> gate = gate();
        Object destroyed = new Object();
        Object recreated = new Object();
        Req first = new Req(destroyed, destroyed, false);
        gate.request(first, T);
        assertEquals(ConfigFlowGate.Decision.COALESCE, gate.request(new Req(recreated, recreated, false), T + 100));

        first.listenerGone = true;
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_LISTENER_GONE,
                gate.request(new Req(recreated, recreated, false), T + 200));
        assertTrue(gate.superseded(destroyed));
        assertNull(gate.finish(recreated));
    }

    @Test public void notSupersededWhenIdle() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        assertFalse(gate.superseded(flow));
        gate.request(new Req(flow, flow, false), T);
        assertNull(gate.finish(flow));
        assertFalse(gate.superseded(flow));
        assertNull(gate.finish(flow));
    }

    @Test public void expiryReleasesAStalledFlowAndRunsTheQueue() {
        ConfigFlowGate<Req> gate = gate();
        Object stalled = new Object();
        Object push = new Object();
        Req first = new Req(stalled, stalled, false);
        gate.request(first, T);
        gate.request(new Req(push, push, false), T + 1);

        assertTrue(gate.expire(first));
        Req next = gate.takeNext();
        assertSame(push, next.flow);
        assertEquals(ConfigFlowGate.Decision.START, gate.request(next, T + 2));
        assertNull(gate.finish(push));
        assertNull(gate.takeNext());
    }

    @Test public void expiryOfAFlowThatNoLongerOwnsTheGateDoesNothing() {
        ConfigFlowGate<Req> gate = gate();
        Object flow = new Object();
        Req first = new Req(flow, flow, false);
        gate.request(first, T);
        assertNull(gate.finish(flow));
        assertFalse(gate.expire(first));

        Req second = new Req(flow, flow, false);
        gate.request(second, T + 10);
        assertFalse(gate.expire(first));
        assertTrue(gate.superseded(new Object()));
        Object other = new Object();
        gate.request(new Req(other, other, false), T + 11);
        assertNull(gate.takeNext());
        assertTrue(gate.expire(second));
        assertSame(other, gate.takeNext().flow);
    }

    @Test public void queuedRequestsStillRunAfterATakeover() {
        ConfigFlowGate<Req> gate = gate();
        Object stale = new Object();
        Object plugin = new Object();
        Object launcher = new Object();
        gate.request(new Req(stale, stale, false), T);
        gate.request(new Req(plugin, plugin, true), T + 1);
        assertEquals(ConfigFlowGate.Decision.TAKEOVER_STALE, gate.request(new Req(launcher, launcher, false), T + STALE));

        assertNull(gate.finish(stale));
        Req next = gate.finish(launcher);
        assertSame(plugin, next.flow);
        assertTrue(next.userInteraction);
    }
}
