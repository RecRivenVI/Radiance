package com.radiance.adapter.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.radiance.client.render.RenderCaptureContract;
import com.radiance.render.contract.DrawTargetContract;
import net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension;
import org.junit.jupiter.api.Test;

class DimensionSpecialEffectsCompatibilityTest {

    @Test
    void extensionInvocationHasAnExplicitNonTlasScope() {
        IDimensionSpecialEffectsExtension extension = new IDimensionSpecialEffectsExtension() {};

        DimensionSpecialEffectsCompatibility.InvocationResult<DrawTargetContract.BufferDrawDecision>
                result =
                        DimensionSpecialEffectsCompatibility.invoke(
                                extension,
                                "renderSky",
                                () -> {
                                    assertEquals(
                                            DrawTargetContract.ScopeKind.DIMENSION_EFFECT,
                                            RenderCaptureContract.currentScope().kind());
                                    return RenderCaptureContract.classifyBufferDraw();
                                });

        DrawTargetContract.BufferDrawDecision decision = result.value();
        assertEquals(DrawTargetContract.BufferDrawRoute.REJECT_WORLD_MESH, decision.route());
        assertEquals(0, result.acceptedWorldMeshes());
        assertEquals(
                DrawTargetContract.ScopeKind.OUTSIDE, RenderCaptureContract.currentScope().kind());
    }
}
