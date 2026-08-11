package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.*;
import com.radiance.adapter.vanilla.VanillaMaterialAdapter;
import com.radiance.client.constant.Constants;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;

class SourceHardwareOpacityTest {
    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void aKnownDiscardOrBlendedProgramCannotBecomeHardwareOpaqueFromItsName() {
        var tex = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
        for (var source :
                List.of(RenderType.entityCutoutNoCull(tex), RenderType.entityTranslucent(tex))) {
            var state = ((RenderType.CompositeRenderType) source).state;
            var renamed =
                    RenderType.create(
                            "misleading_solid",
                            DefaultVertexFormat.NEW_ENTITY,
                            VertexFormat.Mode.QUADS,
                            256,
                            false,
                            false,
                            state);
            assertEquals(
                    VanillaMaterialAdapter.getAlphaMode(source),
                    VanillaMaterialAdapter.getAlphaMode(renamed));
            assertEquals(
                    Constants.GeometryTypes.WORLD_TRANSPARENT,
                    VanillaMaterialAdapter.getGeometryType(renamed, true));
            assertEquals(
                    VanillaMaterialAdapter.getGeometryType(source, true),
                    VanillaMaterialAdapter.getGeometryType(renamed, true));
        }
        var solid = (RenderType.CompositeRenderType) RenderType.entitySolid(tex);
        var renamed =
                RenderType.create(
                        "not_an_opaque_name",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        false,
                        solid.state);
        assertEquals(
                Constants.GeometryTypes.WORLD_SOLID,
                VanillaMaterialAdapter.getGeometryType(renamed, true));
    }
}
