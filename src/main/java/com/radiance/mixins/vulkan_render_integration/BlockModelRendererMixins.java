package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.compatibility.sable.SableVertexConsumerCompat;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class BlockModelRendererMixins {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/resources/model/BakedModel;FFFIILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V")
    private void radiance$rigidModel(PoseStack.Pose pose, VertexConsumer consumer, BlockState state,
        net.minecraft.client.resources.model.BakedModel model, float r, float g, float b, int light, int overlay,
        net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType layer,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        com.radiance.client.vertex.RigidModelCapture.render(model, pose, consumer,
            target -> original.call(pose, target, state, model, r, g, b, light, overlay, data, layer));
    }

    @Final
    @Shadow
    private BlockColors blockColors;

    @Inject(method =
        "putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;"
            + "FFFFIIIII)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void redirectRenderQuad(BlockAndTintGetter world,
        BlockState state,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        PoseStack.Pose matrixEntry,
        BakedQuad quad,
        float brightness0,
        float brightness1,
        float brightness2,
        float brightness3,
        int light0,
        int light1,
        int light2,
        int light3,
        int overlay,
        CallbackInfo ci) {
        boolean pathTraced = !com.radiance.client.render.RasterPreviewScope.active();
        VertexConsumer bulkDataConsumer = SableVertexConsumerCompat.bulkDataConsumer(vertexConsumer);
        if (!pathTraced && bulkDataConsumer == vertexConsumer) {
            // Catnip preview draws retain their original raster producer.
            return;
        }
        float f;
        float g;
        float h;
        float emission;
        if (quad.isTinted()) {
            int i = this.blockColors.getColor(state, world, pos, quad.getTintIndex());
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = ((IBlockColorsExt) this.blockColors).radiance$getEmission(state, world, pos,
                quad.getTintIndex());
        } else {
            f = 1.0F;
            g = 1.0F;
            h = 1.0F;

            emission = 0.0F;
        }

        float[] brightness = {brightness0, brightness1, brightness2, brightness3};
        // Path tracing derives directional light from the surface normal, so the vanilla baked
        // face shade is removed from the vertex color for every section (world and sub-level
        // alike). Ambient occlusion is preserved: it is a geometric term, not a light-direction
        // approximation.
        float directionalShade = world.getShade(quad.getDirection(), quad.isShade());
        if (pathTraced && directionalShade > 0.0001F && directionalShade < 0.9999F) {
            for (int index = 0; index < brightness.length; index++) {
                brightness[index] = Mth.clamp(brightness[index] / directionalShade, 0.0F, 1.0F);
            }
        }

        bulkDataConsumer.putBulkData(matrixEntry,
            quad,
            brightness,
            f,
            g,
            h,
            1.0F,
            new int[]{light0, light1, light2, light3},
            overlay,
            true);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            pbrVertexConsumer.albedoEmission(emission);
        }

        ci.cancel();
    }
}
