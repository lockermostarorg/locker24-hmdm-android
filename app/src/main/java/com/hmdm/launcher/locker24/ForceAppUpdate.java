package com.hmdm.launcher.locker24;

import com.hmdm.launcher.json.PushMessage;

/** The forceAppUpdate push: starts the update with the apps named in the payload exempt from the window. */
public final class ForceAppUpdate {
    public interface Starter {
        void forced(ForcedApps apps);

        void normal();
    }

    private ForceAppUpdate() {
    }

    public static boolean isForceAppUpdate(PushMessage message) {
        return PushMessage.TYPE_FORCE_APP_UPDATE.equals(message.getMessageType());
    }

    /** The payload as text: the string payload of a polled push, or the JSON object of an MQTT push. */
    public static String payloadText(PushMessage message) {
        String text = message.getPayload();
        if (text != null) {
            return text;
        }
        Object json = message.getPayloadJSON();
        return json != null ? json.toString() : null;
    }

    /** Returns true when the message was a forceAppUpdate push. A stale or bad payload starts a normal update. */
    public static boolean handle(PushMessage message, long nowMs, Starter starter) {
        if (!isForceAppUpdate(message)) {
            return false;
        }
        ForcedApps apps = ForcedApps.parse(payloadText(message), nowMs);
        if (apps.isEmpty()) {
            starter.normal();
        } else {
            starter.forced(apps);
        }
        return true;
    }
}
