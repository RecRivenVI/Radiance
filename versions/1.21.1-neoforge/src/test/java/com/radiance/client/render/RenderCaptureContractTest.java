package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.radiance.render.contract.DrawTargetContract;
import org.junit.jupiter.api.Test;

class RenderCaptureContractTest {

    @Test
    void worldRasterEffectDoesNotAuthorizeSurroundingWorldDraws() {
        try (var world =
                RenderCaptureContract.enter(
                        DrawTargetContract.ScopeKind.WORLD_STAGE, "after_particles")) {
            assertThrows(
                    IllegalStateException.class,
                    () -> {
                        try (var effect =
                                RenderCaptureContract.enter(
                                        DrawTargetContract.ScopeKind.WORLD_RASTER,
                                        "simulated:end_sea")) {
                            assertEquals(
                                    DrawTargetContract.BufferDrawRoute.VULKAN_WORLD_RASTER,
                                    RenderCaptureContract.classifyBufferDraw().route());
                            throw new IllegalStateException("effect failed");
                        }
                    });
            assertEquals(
                    DrawTargetContract.BufferDrawRoute.REJECT_WORLD_MESH,
                    RenderCaptureContract.classifyBufferDraw().route());
        }
        assertEquals(
                DrawTargetContract.BufferDrawRoute.REJECT_UNSCOPED,
                RenderCaptureContract.classifyBufferDraw().route());
    }

    @Test
    void onlyExplicitUiScopesAcceptBufferUploaderDraws() {
        assertEquals(
                DrawTargetContract.BufferDrawRoute.REJECT_UNSCOPED,
                RenderCaptureContract.classifyBufferDraw().route());

        try (RenderCaptureContract.ScopeToken ignored =
                RenderCaptureContract.enter(
                        DrawTargetContract.ScopeKind.WORLD_STAGE, "after_entities")) {
            DrawTargetContract.BufferDrawDecision decision =
                    RenderCaptureContract.classifyBufferDraw();
            assertEquals(DrawTargetContract.BufferDrawRoute.REJECT_WORLD_MESH, decision.route());
        }

        try (RenderCaptureContract.ScopeToken ignored =
                RenderCaptureContract.enter(DrawTargetContract.ScopeKind.GUI, "gui")) {
            assertEquals(
                    DrawTargetContract.BufferDrawRoute.VULKAN_UI,
                    RenderCaptureContract.classifyBufferDraw().route());
        }

        try (RenderCaptureContract.ScopeToken ignored =
                RenderCaptureContract.enter(
                        DrawTargetContract.ScopeKind.CAMERA_OVERLAY, "camera_overlay")) {
            assertEquals(
                    DrawTargetContract.BufferDrawRoute.VULKAN_UI,
                    RenderCaptureContract.classifyBufferDraw().route());
        }
    }

    @Test
    void scopesAreNestedAndMustCloseInOrder() {
        RenderCaptureContract.ScopeToken outer =
                RenderCaptureContract.enter(DrawTargetContract.ScopeKind.GUI, "outer");
        RenderCaptureContract.ScopeToken inner =
                RenderCaptureContract.enter(DrawTargetContract.ScopeKind.CAMERA_OVERLAY, "inner");

        assertEquals(
                DrawTargetContract.ScopeKind.CAMERA_OVERLAY,
                RenderCaptureContract.currentScope().kind());
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertEquals(
                DrawTargetContract.ScopeKind.OUTSIDE, RenderCaptureContract.currentScope().kind());
    }

    @Test
    void directOpenGlRejectionIsExplicit() {
        UnsupportedOperationException error =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> {
                            throw RenderCaptureContract.rejectOpenGl("_glBindFramebuffer");
                        });
        assertEquals(true, error.getMessage().contains("GLFW_NO_API"));
    }

    @Test
    void tryWithResourcesClosesScopeAfterRendererException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (RenderCaptureContract.ScopeToken ignored =
                            RenderCaptureContract.enter(
                                    DrawTargetContract.ScopeKind.WORLD_STAGE,
                                    "exceptional_stage")) {
                        throw new IllegalArgumentException("renderer failed");
                    }
                });
        assertEquals(
                DrawTargetContract.ScopeKind.OUTSIDE, RenderCaptureContract.currentScope().kind());
    }
}
