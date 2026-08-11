package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.*;
import com.radiance.adapter.vanilla.VanillaMaterialAdapter;
import com.radiance.render.contract.LineContract;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.*;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;

class LineBlendPolicyTest {
    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void sourceBlendFactorsReachTheRetainedLineStrategyWithoutChangingOpaqueDefault() {
        var states =
                List.of(
                        RenderStateShard.NO_TRANSPARENCY,
                        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
                        RenderStateShard.ADDITIVE_TRANSPARENCY,
                        RenderStateShard.LIGHTNING_TRANSPARENCY,
                        RenderStateShard.GLINT_TRANSPARENCY,
                        RenderStateShard.CRUMBLING_TRANSPARENCY);
        int[] wanted = {0, 10, 11, 20, 21, 22};
        for (int i = 0; i < states.size(); i++) {
            var type =
                    RenderType.create(
                            "same_source_identity",
                            DefaultVertexFormat.POSITION_COLOR_NORMAL,
                            VertexFormat.Mode.DEBUG_LINES,
                            256,
                            false,
                            false,
                            RenderType.CompositeState.builder()
                                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                                    .setTransparencyState(states.get(i))
                                    .createCompositeState(false));
            assertEquals(wanted[i], VanillaMaterialAdapter.drawBlendMode(type));
            assertEquals(wanted[i], LineDrawAdapter.alphaMode(type));
            assertEquals(
                    wanted[i],
                    LineContract.OpacityPolicy.SOURCE_ALPHA.material(wanted[i], 0, 1).alphaMode());
            assertEquals(
                    0, LineContract.OpacityPolicy.OPAQUE.material(wanted[i], 0, 1).alphaMode());
        }
    }
}
