package com.radiance.compatibility.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.radiance.client.render.RenderCaptureContract;
import net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension;
import org.junit.jupiter.api.Test;

class DimensionSpecialEffectsCompatibilityTest {

    @Test
    void extensionInvocationHasAnExplicitNonTlasScope() {
        IDimensionSpecialEffectsExtension extension = new IDimensionSpecialEffectsExtension() {
        };

        DimensionSpecialEffectsCompatibility.InvocationResult<RenderCaptureContract.BufferDrawDecision> result =
            DimensionSpecialEffectsCompatibility.invoke(extension, "renderSky", () -> {
                assertEquals(RenderCaptureContract.ScopeKind.DIMENSION_EFFECT,
                    RenderCaptureContract.currentScope().kind());
                return RenderCaptureContract.classifyBufferDraw();
            });

        RenderCaptureContract.BufferDrawDecision decision = result.value();
        assertEquals(RenderCaptureContract.BufferDrawRoute.REJECT_WORLD_MESH,
            decision.route());
        assertEquals(0, result.acceptedWorldMeshes());
        assertEquals(RenderCaptureContract.ScopeKind.OUTSIDE,
            RenderCaptureContract.currentScope().kind());
    }
}
