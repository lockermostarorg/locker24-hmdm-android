package com.hmdm.launcher.locker24;

import com.hmdm.launcher.json.PushMessage;

/** The forceAppUpdate push: runs the configuration update with userInteraction, which ignores the update window. */
public final class ForceAppUpdate {
    public interface Starter {
        void start(boolean userInteraction);
    }

    private ForceAppUpdate() {
    }

    public static boolean isForceAppUpdate(PushMessage message) {
        return PushMessage.TYPE_FORCE_APP_UPDATE.equals(message.getMessageType());
    }

    /** Returns true when the message was a forceAppUpdate push and the update was started. */
    public static boolean handle(PushMessage message, Starter starter) {
        if (!isForceAppUpdate(message)) {
            return false;
        }
        starter.start(true);
        return true;
    }
}
