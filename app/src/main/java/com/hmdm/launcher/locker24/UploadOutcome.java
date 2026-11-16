package com.hmdm.launcher.locker24;

public final class UploadOutcome {
    public enum Result { SENT, DROP, RETRY }

    private UploadOutcome() {
    }

    public static Result classify(int httpCode, String bodyStatus, String bodyMessage) {
        if (httpCode != 200 || bodyStatus == null) {
            return Result.RETRY;
        }
        if ("OK".equals(bodyStatus)) {
            return Result.SENT;
        }
        if ("error.notfound.device".equals(bodyMessage)
                || "error.signature".equals(bodyMessage)
                || "error.validation".equals(bodyMessage)) {
            return Result.DROP;
        }
        return Result.RETRY;
    }
}
