package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.compatibility.vanilla.VanillaEmissionAdapter;
import com.radiance.compatibility.vanilla.VanillaMaterialAdapter;
import com.radiance.render.contract.DrawStateSnapshot;
import com.radiance.render.contract.SourceEmission;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmissiveLayerSourceTest {
    private static RenderType fixture;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        fixture =
                new RenderType(
                        "uv_fixture",
                        PBRVertexFormats.PBR_TRIANGLE,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        false,
                        () -> {},
                        () -> {}) {};
    }

    @Test
    void realEnergyProgramCapturesAnImmutablePrimaryUvTransformButGlintAndBodyDoNot() {
        var texture =
                ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
        var energy = RenderType.energySwirl(texture, .2f, .4f);
        var matrix = new Matrix4f().translation(.2f, .4f, 0);
        var uv = VanillaMaterialAdapter.primaryUv(energy, matrix);
        matrix.translation(9, 8, 0);
        assertEquals(.45f, uv.u(.25f, .5f), 1e-6);
        assertEquals(.9f, uv.v(.25f, .5f), 1e-6);
        assertTrue(VanillaMaterialAdapter.transformsPrimaryUv(energy));
        assertEquals(
                DrawStateSnapshot.UvTransform.IDENTITY,
                VanillaMaterialAdapter.primaryUv(RenderType.entityCutoutNoCull(texture), matrix));
        assertEquals(
                DrawStateSnapshot.UvTransform.IDENTITY,
                VanillaMaterialAdapter.primaryUv(RenderType.entityGlint(), matrix));
        assertTrue(
                VanillaEmissionAdapter.policy(energy, SourceEmission.Role.ORDINARY, false)
                        .forceUnit());
    }

    @Test
    void breezeWindKeepsItsOwnUvDiscardColourAndNonEmissiveCoverageContract() {
        var texture = ResourceLocation.withDefaultNamespace("audit/wind.png");
        var wind = RenderType.breezeWind(texture, .2f, .4f);
        var uv = VanillaMaterialAdapter.primaryUv(wind, new Matrix4f().translation(.2f, .4f, 0));
        assertEquals(.45f, uv.u(.25f, .5f), 1e-6);
        assertEquals(.9f, uv.v(.25f, .5f), 1e-6);
        assertEquals(170, VanillaMaterialAdapter.getAlphaMode(wind));
        var state = new DrawStateSnapshot(0, 1, .2f, .3f, .4f, .5f, uv);
        assertEquals(state, VanillaMaterialAdapter.sourceColour(wind, state));
        assertFalse(
                VanillaEmissionAdapter.policy(wind, SourceEmission.Role.ORDINARY, false)
                        .forceUnit());
        assertTrue(VanillaMaterialAdapter.transformsPrimaryUv(wind));
    }

    @Test
    void actualCompanionFactoriesKeepCoverageAndOrdinaryDecorationSeparate() {
        assertFalse(
                VanillaEmissionAdapter.hasRenderedArmor(
                        slot -> net.minecraft.world.item.ItemStack.EMPTY));
        assertTrue(
                VanillaEmissionAdapter.hasRenderedArmor(
                        slot ->
                                slot == net.minecraft.world.entity.EquipmentSlot.HEAD
                                        ? new net.minecraft.world.item.ItemStack(
                                                net.minecraft.world.item.Items.IRON_HELMET)
                                        : net.minecraft.world.item.ItemStack.EMPTY));
        assertFalse(
                VanillaEmissionAdapter.hasRenderedArmor(
                        slot ->
                                slot == net.minecraft.world.entity.EquipmentSlot.CHEST
                                        ? new net.minecraft.world.item.ItemStack(
                                                net.minecraft.world.item.Items.IRON_HELMET)
                                        : net.minecraft.world.item.ItemStack.EMPTY));
        var texture = ResourceLocation.withDefaultNamespace("audit/companion.png");
        var warden =
                new net.minecraft.client.renderer.entity.layers.WardenEmissiveLayer<>(
                        null, texture, (entity, time, partial) -> .5f, model -> List.of());
        var breeze = new net.minecraft.client.renderer.entity.layers.BreezeEyesLayer(null);
        assertTrue(VanillaEmissionAdapter.requiresSurfaceLayers(List.of(warden), false));
        assertTrue(VanillaEmissionAdapter.requiresSurfaceLayers(List.of(breeze), false));
        assertFalse(VanillaEmissionAdapter.requiresEyeComposition(List.of(breeze)));
        assertFalse(VanillaEmissionAdapter.requiresSurfaceLayers(List.of(new Object()), true));
        for (var type :
                List.of(
                        RenderType.entityTranslucentEmissive(texture),
                        RenderType.breezeEyes(texture))) {
            assertTrue(VanillaMaterialAdapter.supportsSurfaceLayers(type));
            var policy =
                    VanillaEmissionAdapter.policy(
                            type, SourceEmission.Role.SURFACE_LAYER_OWNER, false);
            assertTrue(policy.forceUnit());
            assertFalse(policy.eyeOverlay());
        }
        for (var type :
                List.of(
                        RenderType.entityCutoutNoCull(texture),
                        RenderType.entityTranslucent(texture),
                        RenderType.armorCutoutNoCull(texture))) {
            assertTrue(VanillaMaterialAdapter.supportsSurfaceLayers(type));
            assertFalse(
                    VanillaEmissionAdapter.policy(
                                    type, SourceEmission.Role.SURFACE_LAYER_OWNER, false)
                            .forceUnit());
        }
        assertEquals(
                160, VanillaMaterialAdapter.getAlphaMode(RenderType.armorCutoutNoCull(texture)));
        assertFalse(VanillaMaterialAdapter.supportsSurfaceLayers(fixture));
        assertFalse(
                VanillaMaterialAdapter.supportsSurfaceLayers(
                        RenderType.createArmorDecalCutoutNoCull(texture)));
        assertFalse(
                VanillaMaterialAdapter.supportsSurfaceLayers(
                        RenderType.energySwirl(texture, .2f, .4f)));
    }

    @Test
    void actualEntitySubmissionTransformsCanonicalCompactAndRawUvExactlyOnce() {
        for (int layout = 0; layout < 3; layout++) {
            try (var allocation = new ByteBufferBuilder(2048)) {
                VertexConsumer consumer =
                        layout == 2
                                ? new BufferBuilder(
                                        allocation,
                                        VertexFormat.Mode.QUADS,
                                        DefaultVertexFormat.NEW_ENTITY)
                                : PBRVertexConsumer.dynamic(allocation, fixture, layout == 1);
                quad(consumer);
                try (MeshData mesh =
                        consumer instanceof PBRVertexConsumer pbr
                                ? pbr.end()
                                : ((BufferBuilder) consumer).buildOrThrow()) {
                    var state =
                            new DrawStateSnapshot(
                                    0,
                                    1,
                                    .2f,
                                    .3f,
                                    .4f,
                                    1,
                                    new DrawStateSnapshot.UvTransform(1, 0, .2f, 0, 1, .4f));
                    var draw =
                            new EntityProxy.EntityRenderLayer(
                                    fixture, mesh, true, "uv", null, state);
                    assertEquals(
                            DrawStateSnapshot.UvTransform.IDENTITY, draw.drawState().uvTransform());
                    assertEquals(.2f, draw.drawState().red());
                    int uvOffset =
                            mesh.drawState()
                                    .format()
                                    .getOffset(
                                            layout == 2
                                                    ? VertexFormatElement.UV0
                                                    : PBRVertexFormatElements.PBR_TEXTURE_UV);
                    var bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                    assertEquals(.45f, bytes.getFloat(uvOffset), 1e-6);
                    assertEquals(.9f, bytes.getFloat(uvOffset + 4), 1e-6);
                    new EntityProxy.EntityRenderLayer(
                            fixture, mesh, true, "again", null, draw.drawState());
                    assertEquals(.45f, bytes.getFloat(uvOffset), 1e-6);
                    assertEquals(.9f, bytes.getFloat(uvOffset + 4), 1e-6);
                }
            }
        }
    }

    @Test
    void subsequentFramesUseTheirOwnSourceOffsetAndInvalidTransformsDoNotMutateBytes() {
        for (float offset : new float[] {.1f, .8f}) {
            try (var allocation = new ByteBufferBuilder(2048)) {
                var builder =
                        new BufferBuilder(
                                allocation,
                                VertexFormat.Mode.QUADS,
                                DefaultVertexFormat.NEW_ENTITY);
                quad(builder);
                try (var mesh = builder.buildOrThrow()) {
                    int at = mesh.drawState().format().getOffset(VertexFormatElement.UV0);
                    var bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                    var invalid =
                            new DrawStateSnapshot(
                                    0,
                                    1,
                                    1,
                                    1,
                                    1,
                                    1,
                                    new DrawStateSnapshot.UvTransform(1, 0, Float.NaN, 0, 1, 0));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> CapturedTextureUv.apply(mesh, invalid));
                    assertEquals(.25f, bytes.getFloat(at));
                    CapturedTextureUv.apply(
                            mesh,
                            new DrawStateSnapshot(
                                    0,
                                    1,
                                    1,
                                    1,
                                    1,
                                    1,
                                    new DrawStateSnapshot.UvTransform(1, 0, offset, 0, 1, 0)));
                    assertEquals(.25f + offset, bytes.getFloat(at), 1e-6);
                }
            }
        }
    }

    @Test
    void actualSpiderAndEndermanModelsEmitTheSameBodyAndEyesGeometry() {
        for (ModelPart root :
                List.of(
                        SpiderModel.createSpiderBodyLayer().bakeRoot(),
                        EndermanModel.createBodyLayer().bakeRoot())) {
            root.yRot = .4f;
            root.xRot = -.2f;
            byte[] body = modelBytes(root), eyes = modelBytes(root);
            assertTrue(body.length > 0);
            assertArrayEquals(body, eyes);
        }
        var eyes =
                RenderType.eyes(
                        ResourceLocation.withDefaultNamespace("textures/entity/spider_eyes.png"));
        var policy = VanillaEmissionAdapter.policy(eyes, SourceEmission.Role.ORDINARY, false);
        assertTrue(policy.eyeOverlay());
        assertTrue(policy.forceUnit());
        assertTrue(
                VanillaEmissionAdapter.requiresEyeComposition(
                        List.of(
                                new net.minecraft.client.renderer.entity.layers.SpiderEyesLayer<>(
                                        null))));
        assertFalse(VanillaEmissionAdapter.requiresEyeComposition(List.of(new Object())));
        assertDoesNotThrow(
                () ->
                        net.minecraft.client.renderer.entity.LivingEntityRenderer.class
                                .getDeclaredField("layers"));
    }

    private static byte[] modelBytes(ModelPart model) {
        try (var allocation = new ByteBufferBuilder(32768)) {
            var builder =
                    new BufferBuilder(
                            allocation, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            model.render(new PoseStack(), builder, 15728880, 655360, -1);
            try (var mesh = builder.buildOrThrow()) {
                byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().duplicate().get(bytes);
                return bytes;
            }
        }
    }

    private static void quad(VertexConsumer consumer) {
        for (int i = 0; i < 4; i++)
            consumer.addVertex(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0, 0)
                    .setColor(-1)
                    .setUv(.25f, .5f)
                    .setOverlay(0)
                    .setLight(15728880)
                    .setNormal(0, 0, 1);
    }
}
