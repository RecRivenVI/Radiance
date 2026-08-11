package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraOverlayRendererTest {

    @Test
    void requiresTheWorldAndVanillaCameraOverlayGates() {
        assertTrue(CameraOverlayRenderer.shouldRenderGates(true, true, true, false, false));

        assertFalse(CameraOverlayRenderer.shouldRenderGates(false, true, true, false, false));
        assertFalse(CameraOverlayRenderer.shouldRenderGates(true, false, true, false, false));
        assertFalse(CameraOverlayRenderer.shouldRenderGates(true, true, false, false, false));
        assertFalse(CameraOverlayRenderer.shouldRenderGates(true, true, true, true, false));
        assertFalse(CameraOverlayRenderer.shouldRenderGates(true, true, true, false, true));
    }
}
