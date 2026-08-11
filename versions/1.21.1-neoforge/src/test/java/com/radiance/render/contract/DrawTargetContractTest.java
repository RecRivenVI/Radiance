package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DrawTargetContractTest {
    @Test
    void pureDecisionMatchesEveryExistingScopeAndFramebufferCase() {
        for (var kind : DrawTargetContract.ScopeKind.values()) {
            for (int framebuffer : new int[] {0, 1, 307}) {
                var expected =
                        switch (kind) {
                            case WORLD_RASTER ->
                                    DrawTargetContract.BufferDrawRoute.VULKAN_WORLD_RASTER;
                            case GUI, CAMERA_OVERLAY ->
                                    DrawTargetContract.BufferDrawRoute.VULKAN_UI;
                            case WORLD_STAGE, DIMENSION_EFFECT ->
                                    framebuffer != 0
                                            ? DrawTargetContract.BufferDrawRoute
                                                    .VULKAN_CUSTOM_TARGET
                                            : DrawTargetContract.BufferDrawRoute.REJECT_WORLD_MESH;
                            case OUTSIDE, UNSUPPORTED_OPENGL ->
                                    DrawTargetContract.BufferDrawRoute.REJECT_UNSCOPED;
                        };
                var scope = new DrawTargetContract.Scope(kind, "registered-source");
                var decision = DrawTargetContract.classify(scope, framebuffer);
                assertEquals(expected, decision.route(), kind + "/" + framebuffer);
                assertSame(scope, decision.scope());
                assertFalse(decision.reason().isBlank());
                assertEquals(decision, DrawTargetContract.classify(scope, framebuffer));
            }
        }
    }

    @Test
    void previewAndRawDrawPermissionAreSeparateContracts() {
        for (var kind : DrawTargetContract.ScopeKind.values()) {
            for (boolean explicitPreview : new boolean[] {false, true}) {
                boolean expected =
                        switch (kind) {
                            case GUI, CAMERA_OVERLAY, WORLD_RASTER -> true;
                            case WORLD_STAGE, DIMENSION_EFFECT -> false;
                            default -> explicitPreview;
                        };
                assertEquals(expected, DrawTargetContract.rasterPreview(kind, explicitPreview));
            }
        }
        assertTrue(DrawTargetContract.rasterPreview(DrawTargetContract.ScopeKind.OUTSIDE, true));
        assertEquals(
                DrawTargetContract.BufferDrawRoute.REJECT_UNSCOPED,
                DrawTargetContract.classify(new DrawTargetContract.Scope(null, null), 7).route());
    }
}
