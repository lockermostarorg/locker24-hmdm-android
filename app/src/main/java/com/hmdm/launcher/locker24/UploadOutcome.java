package com.hmdm.launcher.locker24;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class UploadOutcome {
    public enum Result { SENT, DROP, RETRY }

    public static final int MAX_RUNS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /** True when a failed run should end the work instead of retrying, so KEEP does not block new uploads for hours. */
    public static boolean stopRetrying(int runAttemptCount) {
        return runAttemptCount + 1 >= MAX_RUNS;
    }

    public static final class Body {
        public final String status;
        public final String message;

        private Body(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public static Body parse(String text) {
            if (text == null || text.trim().isEmpty()) {
                return new Body(null, null);
            }
            try {
                JsonNode root = MAPPER.readTree(text);
                if (root == null || !root.isObject()) {
                    return new Body(null, null);
                }
                return new Body(textOrNull(root.get("status")), textOrNull(root.get("message")));
            } catch (Exception e) {
                return new Body(null, null);
            }
        }

        private static String textOrNull(JsonNode node) {
            return node != null && node.isTextual() ? node.asText() : null;
        }
    }
}
