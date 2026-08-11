package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.NameTagBackgroundStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins<T extends Entity> {

    @WrapOperation(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"))
    private void radiance$captureNameTag(EntityRenderer<T> renderer, T entity,
        Component content, PoseStack poseStack, MultiBufferSource vertexConsumers, int light,
        float partialTick, Operation<Void> original) {
        MultiBufferSource destination = EntityProxy.postTextVertexConsumerProvider != null
            ? EntityProxy.postTextVertexConsumerProvider
            : vertexConsumers;
        original.call(renderer, entity, content, poseStack, destination, light, partialTick);
    }

    @WrapOperation(method = "renderNameTag",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int radiance$splitNameTagBackground(Font font, Component content, float x, float y,
        int color, boolean dropShadow, Matrix4f pose, MultiBufferSource vertexConsumers,
        Font.DisplayMode displayMode, int backgroundColor, int light, Operation<Integer> original) {
        if (backgroundColor != 0 && EntityProxy.postTextVertexConsumerProvider != null) {
            renderNameTagBackground(font, content, x, y, pose, vertexConsumers, displayMode,
                NameTagBackgroundStyle.applyOpacity(backgroundColor), light);
            backgroundColor = 0;
        }
        return original.call(font, content, x, y, color, dropShadow, pose, vertexConsumers,
            displayMode, backgroundColor, light);
    }

    private static void renderNameTagBackground(Font font, Component content, float x, float y,
        Matrix4f pose, MultiBufferSource vertexConsumers, Font.DisplayMode displayMode,
        int backgroundColor, int light) {
        RenderType backgroundLayer = displayMode == Font.DisplayMode.SEE_THROUGH
            ? RenderType.textBackgroundSeeThrough()
            : RenderType.textBackground();
        VertexConsumer background = vertexConsumers.getBuffer(backgroundLayer);
        float x0 = x - 1.0F;
        float x1 = x + font.width(content) + 1.0F;
        float y0 = y + 9.0F;
        float y1 = y - 1.0F;
        float depth = 0.01F;
        background.addVertex(pose, x0, y0, depth).setColor(backgroundColor).setLight(light);
        background.addVertex(pose, x1, y0, depth).setColor(backgroundColor).setLight(light);
        background.addVertex(pose, x1, y1, depth).setColor(backgroundColor).setLight(light);
        background.addVertex(pose, x0, y1, depth).setColor(backgroundColor).setLight(light);
    }

}
