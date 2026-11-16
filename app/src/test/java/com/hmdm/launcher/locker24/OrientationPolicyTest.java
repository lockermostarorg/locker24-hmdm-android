package com.hmdm.launcher.locker24;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;

import org.junit.Test;

public class OrientationPolicyTest {
    @Test public void landscapeConfigRequestsLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, OrientationPolicy.requestedOrientation(2));
    }

    @Test public void portraitConfigRequestsPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, OrientationPolicy.requestedOrientation(1));
    }

    @Test public void missingOrUnknownConfigIsNotConfigured() {
        assertEquals(OrientationPolicy.NOT_CONFIGURED, OrientationPolicy.requestedOrientation(null));
        assertEquals(OrientationPolicy.NOT_CONFIGURED, OrientationPolicy.requestedOrientation(0));
        assertEquals(OrientationPolicy.NOT_CONFIGURED, OrientationPolicy.requestedOrientation(3));
        assertEquals(OrientationPolicy.NOT_CONFIGURED, OrientationPolicy.requestedOrientation(-1));
    }

    @Test public void requestsWhenCurrentDiffers() {
        assertTrue(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertTrue(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertTrue(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    }

    @Test public void noRequestWhenAlreadyApplied() {
        assertFalse(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    }

    @Test public void noRequestWhenNotConfigured() {
        assertFalse(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                OrientationPolicy.NOT_CONFIGURED));
        assertFalse(OrientationPolicy.needsRequest(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationPolicy.NOT_CONFIGURED));
    }

    @Test public void namesForLogs() {
        assertEquals("landscape", OrientationPolicy.name(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertEquals("portrait", OrientationPolicy.name(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertEquals("-1", OrientationPolicy.name(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
    }
}
