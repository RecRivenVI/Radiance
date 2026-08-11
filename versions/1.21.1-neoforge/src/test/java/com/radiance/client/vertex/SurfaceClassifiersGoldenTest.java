package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SurfaceClassifiersGoldenTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void aKnownProgramDoesNotOverrideADifferentActualBlendState() {
        var layer =
                RenderType.create(
                        "custom_draw",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        true,
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                                .createCompositeState(false));
        assertEquals(11, PBRVertexConsumer.getAlphaMode(layer));
        assertEquals(2, Constants.GeometryTypes.getGeometryType(layer, true).getValue());
    }

    @Test
    void productionFactoriesUseVerifiedDiscardProfilesAndKeepExecutionRoutes() {
        var texture = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
        record Case(RenderType layer, int alpha, int group) {}
        var cases =
                List.of(
                        new Case(RenderType.solid(), 0, 1),
                        new Case(RenderType.cutout(), 9, 2),
                        new Case(RenderType.cutoutMipped(), 1, 2),
                        new Case(RenderType.translucent(), 2, 2),
                        new Case(RenderType.entitySolid(texture), 0, 1),
                        new Case(RenderType.entityCutout(texture), 64, 2),
                        new Case(RenderType.entityTranslucent(texture), 74, 2),
                        new Case(RenderType.eyes(texture), 11, 2),
                        new Case(RenderType.text(texture), 174, 2),
                        new Case(RenderType.textSeeThrough(texture), 241, 2),
                        new Case(RenderType.crumbling(texture), 22, 2),
                        new Case(RenderType.lightning(), 20, 2));
        int[] previousAlpha = {0, 1, 1, 2, 0, 1, 10, 11, 14, 17, 22, 11};
        int classifiedDifferences = 0, row = 0;
        for (var sample : cases) {
            if (previousAlpha[row++] != sample.alpha()) classifiedDifferences++;
            assertEquals(
                    sample.alpha(),
                    PBRVertexConsumer.getAlphaMode(sample.layer()),
                    sample.layer().name);
            assertEquals(
                    sample.group(),
                    Constants.GeometryTypes.getGeometryType(sample.layer(), true).getValue(),
                    sample.layer().name);
            assertEquals(
                    3,
                    Constants.GeometryTypes.getGeometryType(sample.layer(), false).getValue(),
                    sample.layer().name);
        }
        assertEquals(
                6,
                classifiedDifferences,
                "Six verified source-profile corrections; no other execution-route difference");
    }

    @Test
    void bufferedParticleBlendComesFromTheOriginalSheetAndNotItsEntityCarrier() {
        var alpha = com.radiance.compatibility.vanilla.VanillaParticleMaterials.class;
        assertEquals(
                170,
                com.radiance.compatibility.vanilla.VanillaParticleMaterials.alphaMode(
                        net.minecraft.client.particle.ParticleRenderType.TERRAIN_SHEET));
        assertEquals(
                170,
                com.radiance.compatibility.vanilla.VanillaParticleMaterials.alphaMode(
                        net.minecraft.client.particle.ParticleRenderType
                                .PARTICLE_SHEET_TRANSLUCENT));
        assertEquals(
                9,
                com.radiance.compatibility.vanilla.VanillaParticleMaterials.alphaMode(
                        net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_OPAQUE));
        assertEquals(
                9,
                com.radiance.compatibility.vanilla.VanillaParticleMaterials.alphaMode(
                        net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_LIT));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        com.radiance.compatibility.vanilla.VanillaParticleMaterials.alphaMode(
                                net.minecraft.client.particle.ParticleRenderType.CUSTOM));
    }
}
