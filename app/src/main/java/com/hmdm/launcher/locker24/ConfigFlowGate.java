package com.hmdm.launcher.locker24;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets one configuration update flow run per process. A request made while a flow runs is kept
 * and runs once more after that flow finishes; requests with the same key are merged. A flow that
 * has run for STALE_MS, or whose listener is gone, is taken over by the next request from another
 * flow, and the old flow is told it is superseded so it stops at its next step. A flow that stalls
 * without a request behind it is released by expire() after STALE_MS.
 */
public final class ConfigFlowGate<T> {
    public static final long STALE_MS = 10 * 60_000L;

    public enum Decision { START, COALESCE, TAKEOVER_STALE, TAKEOVER_LISTENER_GONE }

    public interface Requests<T> {
        Object flow(T request);

        Object key(T request);

        T merge(T pending, T next);

        boolean listenerGone(T request);
    }

    private final Requests<T> requests;
    private final Map<Object, T> pending = new LinkedHashMap<>();
    private T owner;
    private long ownerSince;

    public ConfigFlowGate(Requests<T> requests) {
        this.requests = requests;
    }

    public static Decision decide(boolean inFlight, long inFlightSince, boolean ownerListenerGone,
                                  boolean requesterIsOwner, long now) {
        if (!inFlight) {
            return Decision.START;
        }
        if (now - inFlightSince >= STALE_MS) {
            return Decision.TAKEOVER_STALE;
        }
        if (ownerListenerGone && !requesterIsOwner) {
            return Decision.TAKEOVER_LISTENER_GONE;
        }
        return Decision.COALESCE;
    }

    public synchronized Decision request(T request, long now) {
        boolean inFlight = owner != null;
        Decision decision = decide(inFlight, ownerSince, inFlight && requests.listenerGone(owner),
                inFlight && requests.flow(owner) == requests.flow(request), now);
        Object key = requests.key(request);
        if (decision == Decision.COALESCE) {
            T queued = pending.get(key);
            pending.put(key, queued == null ? request : requests.merge(queued, request));
            return decision;
        }
        owner = request;
        ownerSince = now;
        pending.remove(key);
        return decision;
    }

    /** Ends the flow if it still owns the gate and returns the next request to run, or null. */
    public synchronized T finish(Object flow) {
        if (owner == null || requests.flow(owner) != flow) {
            return null;
        }
        owner = null;
        return takeNext();
    }

    /** Releases the gate if the flow started by this request still owns it. */
    public synchronized boolean expire(T request) {
        if (owner != request) {
            return false;
        }
        owner = null;
        return true;
    }

    /** The next queued request when no flow owns the gate, or null. */
    public synchronized T takeNext() {
        if (owner != null) {
            return null;
        }
        Iterator<T> it = pending.values().iterator();
        if (!it.hasNext()) {
            return null;
        }
        T next = it.next();
        it.remove();
        return next;
    }

    public synchronized boolean superseded(Object flow) {
        return owner != null && requests.flow(owner) != flow;
    }
}
