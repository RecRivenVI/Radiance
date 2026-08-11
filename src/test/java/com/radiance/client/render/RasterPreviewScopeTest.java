package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class RasterPreviewScopeTest {
    @Test void rasterKeepsOriginalAmbientOcclusionChoiceWithoutChangingWorldPolicy() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier enabled = () -> { calls.incrementAndGet(); return true; };
        assertFalse(RasterPreviewScope.useAmbientOcclusion(enabled));
        assertEquals(0, calls.get());
        try (var gui = RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.GUI, "diagram")) {
            assertTrue(RasterPreviewScope.useAmbientOcclusion(enabled));
            assertFalse(RasterPreviewScope.useAmbientOcclusion(() -> false));
            try (var world = RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.WORLD_STAGE, "PT")) {
                assertFalse(RasterPreviewScope.useAmbientOcclusion(enabled));
            }
        }
        assertEquals(1, calls.get());
        assertFalse(RasterPreviewScope.useAmbientOcclusion(enabled));
    }
    @Test void allExplicitRasterEntrypointsRetainLightingAndShadows() {
        for (var kind : new RenderCaptureContract.ScopeKind[] {
            RenderCaptureContract.ScopeKind.GUI,
            RenderCaptureContract.ScopeKind.CAMERA_OVERLAY,
            RenderCaptureContract.ScopeKind.WORLD_RASTER}) {
            try (var scope = RenderCaptureContract.enter(kind, "preview")) {
                assertTrue(RasterPreviewScope.active(), kind.name());
            }
            assertFalse(RasterPreviewScope.active());
        }
    }

    @Test void nestedWorldCaptureDoesNotInheritRasterLighting() {
        try (var preview = RasterPreviewScope.enter();
             var gui = RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.GUI, "screen")) {
            assertTrue(RasterPreviewScope.active());
            try (var world = RenderCaptureContract.enter(
                    RenderCaptureContract.ScopeKind.WORLD_STAGE, "world producer")) {
                assertFalse(RasterPreviewScope.active());
            }
            assertTrue(RasterPreviewScope.active());
        }
        assertFalse(RasterPreviewScope.active());
    }

    @Test void nestedPreviewFailureRestoresWorldLightingPolicy() {
        assertFalse(RasterPreviewScope.active());
        try (var outer = RasterPreviewScope.enter()) {
            assertTrue(RasterPreviewScope.active());
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = RasterPreviewScope.enter()) {
                    assertTrue(RasterPreviewScope.active());
                    throw new IllegalStateException("renderer failed");
                }
            });
            assertTrue(RasterPreviewScope.active());
        }
        assertFalse(RasterPreviewScope.active());
    }
}
