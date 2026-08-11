package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RenderCaptureContractTest {

    @Test
    void worldRasterEffectDoesNotAuthorizeSurroundingWorldDraws() {
        try (var world = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.WORLD_STAGE, "after_particles")) {
            assertThrows(IllegalStateException.class, () -> {
                try (var effect = RenderCaptureContract.enter(
                    RenderCaptureContract.ScopeKind.WORLD_RASTER, "simulated:end_sea")) {
                    assertEquals(RenderCaptureContract.BufferDrawRoute.VULKAN_WORLD_RASTER,
                        RenderCaptureContract.classifyBufferDraw().route());
                    throw new IllegalStateException("effect failed");
                }
            });
            assertEquals(RenderCaptureContract.BufferDrawRoute.REJECT_WORLD_MESH,
                RenderCaptureContract.classifyBufferDraw().route());
        }
        assertEquals(RenderCaptureContract.BufferDrawRoute.REJECT_UNSCOPED,
            RenderCaptureContract.classifyBufferDraw().route());
    }

    @Test
    void onlyExplicitUiScopesAcceptBufferUploaderDraws() {
        assertEquals(RenderCaptureContract.BufferDrawRoute.REJECT_UNSCOPED,
            RenderCaptureContract.classifyBufferDraw().route());

        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.WORLD_STAGE, "after_entities")) {
            RenderCaptureContract.BufferDrawDecision decision =
                RenderCaptureContract.classifyBufferDraw();
            assertEquals(RenderCaptureContract.BufferDrawRoute.REJECT_WORLD_MESH,
                decision.route());
        }

        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.GUI, "gui")) {
            assertEquals(RenderCaptureContract.BufferDrawRoute.VULKAN_UI,
                RenderCaptureContract.classifyBufferDraw().route());
        }

        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.CAMERA_OVERLAY, "camera_overlay")) {
            assertEquals(RenderCaptureContract.BufferDrawRoute.VULKAN_UI,
                RenderCaptureContract.classifyBufferDraw().route());
        }
    }

    @Test
    void scopesAreNestedAndMustCloseInOrder() {
        RenderCaptureContract.ScopeToken outer = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.GUI, "outer");
        RenderCaptureContract.ScopeToken inner = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.CAMERA_OVERLAY, "inner");

        assertEquals(RenderCaptureContract.ScopeKind.CAMERA_OVERLAY,
            RenderCaptureContract.currentScope().kind());
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertEquals(RenderCaptureContract.ScopeKind.OUTSIDE,
            RenderCaptureContract.currentScope().kind());
    }

    @Test
    void directOpenGlRejectionIsExplicit() {
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
            () -> {
                throw RenderCaptureContract.rejectOpenGl("_glBindFramebuffer");
            });
        assertEquals(true, error.getMessage().contains("GLFW_NO_API"));
    }

    @Test
    void tryWithResourcesClosesScopeAfterRendererException() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
                RenderCaptureContract.ScopeKind.WORLD_STAGE, "exceptional_stage")) {
                throw new IllegalArgumentException("renderer failed");
            }
        });
        assertEquals(RenderCaptureContract.ScopeKind.OUTSIDE,
            RenderCaptureContract.currentScope().kind());
    }
}
