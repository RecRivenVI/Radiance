package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.compatibility.vanilla.VanillaEmissionAdapter;
import com.radiance.render.contract.SourceEmission;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;

class SourceEmissionPolicyTest {
    @BeforeAll
    static void initialize() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void productionFactoriesAndEventEntryAgreeOnExplicitRoles() {
        var texture = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
        for (var layer :
                List.of(
                        RenderType.entitySolid(texture),
                        RenderType.eyes(texture),
                        RenderType.entityTranslucentEmissive(texture),
                        RenderType.beaconBeam(texture, true),
                        RenderType.lightning(),
                        RenderType.dragonRays())) {
            var ordinary =
                    VanillaEmissionAdapter.policy(layer, SourceEmission.Role.ORDINARY, false);
            var event = VanillaEmissionAdapter.policy(layer, SourceEmission.Role.ORDINARY, true);
            assertEquals(ordinary, event, layer.name);
            assertEquals(!layer.name.equals("entity_solid"), ordinary.forceUnit(), layer.name);
        }
        assertTrue(
                VanillaEmissionAdapter.policy(
                                RenderType.eyes(texture), SourceEmission.Role.ORDINARY, false)
                        .eyeOverlay());
        assertEquals(
                171,
                PBRVertexConsumer.getAlphaMode(RenderType.energySwirl(texture, .1f, .2f)),
                "Energy shader discards after source modulation");
        assertTrue(
                VanillaEmissionAdapter.policy(
                                RenderType.text(texture), SourceEmission.Role.NAME_TAG, false)
                        .forceUnit());
        assertFalse(
                VanillaEmissionAdapter.policy(
                                RenderType.textBackground(), SourceEmission.Role.NAME_TAG, false)
                        .forceUnit());
        assertTrue(
                VanillaEmissionAdapter.policy(
                                RenderType.entitySolid(texture),
                                SourceEmission.Role.PRIORITY_OUTLINE,
                                false)
                        .forceUnit());
    }

    @Test
    void actualSourceProgramWinsOverAnUnrelatedNameAndPolicyBitsStayOutOfFaces() {
        var renamed =
                RenderType.create(
                        "unrelated_name",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        false,
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                                .createCompositeState(false));
        assertTrue(
                VanillaEmissionAdapter.policy(renamed, SourceEmission.Role.ORDINARY, false)
                        .eyeOverlay());
        var misleading =
                RenderType.create(
                        "eyes",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        false,
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                                .createCompositeState(false));
        assertFalse(
                VanillaEmissionAdapter.policy(misleading, SourceEmission.Role.ORDINARY, false)
                        .forceUnit());
        for (int bits = 0; bits < 8; bits++)
            for (int type = 0; type < 8; type++)
                for (int face = 0; face < 8; face++) {
                    int original = type | (face << 8),
                            encoded = SourceEmission.encode(original, bits);
                    assertEquals(original, encoded & 0xFFFFFF);
                    assertEquals(bits, SourceEmission.decode(encoded));
                }
    }
}
