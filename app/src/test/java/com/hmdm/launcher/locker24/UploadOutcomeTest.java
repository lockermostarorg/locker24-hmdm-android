package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UploadOutcomeTest {
    private static UploadOutcome.Result classify(int code, String status, String message) {
        return UploadOutcome.classify(code, status, message);
    }

    @Test
    public void okIsSent() {
        assertEquals(UploadOutcome.Result.SENT, classify(200, "OK", null));
    }

    @Test
    public void permanentErrorsAreDropped() {
        assertEquals(UploadOutcome.Result.DROP, classify(200, "ERROR", "error.notfound.device"));
        assertEquals(UploadOutcome.Result.DROP, classify(200, "ERROR", "error.signature"));
        assertEquals(UploadOutcome.Result.DROP, classify(200, "ERROR", "error.validation"));
    }

    @Test
    public void transientErrorsAreRetried() {
        assertEquals(UploadOutcome.Result.RETRY, classify(200, "ERROR", "error.internal.server"));
        assertEquals(UploadOutcome.Result.RETRY, classify(200, "ERROR", "error.resource.disabled"));
        assertEquals(UploadOutcome.Result.RETRY, classify(404, null, null));
        assertEquals(UploadOutcome.Result.RETRY, classify(500, null, null));
        assertEquals(UploadOutcome.Result.RETRY, classify(-1, null, null));
    }

    @Test
    public void unparseableOrUnknownBodyIsRetried() {
        assertEquals(UploadOutcome.Result.RETRY, classify(200, null, null));
        assertEquals(UploadOutcome.Result.RETRY, classify(200, "ERROR", null));
        assertEquals(UploadOutcome.Result.RETRY, classify(200, "ERROR", "error.something.new"));
    }
}
