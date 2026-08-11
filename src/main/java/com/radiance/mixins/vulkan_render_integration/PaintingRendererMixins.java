package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererMixins {

    /**
     * 1.21.4 为画使用独立的 entity_solid_z_offset_forward 层。Radiance native 依靠该层名关闭
     * 高度图追踪；1.21.1 尚未提供该层，因此在兼容层中补回等价的实体实心层。
     */
    @Unique
    private static final Function<ResourceLocation, RenderType> RADIANCE_PAINTING_LAYER =
        Util.memoize(texture -> RenderType.create("entity_solid_z_offset_forward",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RenderType.RENDERTYPE_ENTITY_SOLID_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderType.NO_TRANSPARENCY)
                .setLightmapState(RenderType.LIGHTMAP)
                .setOverlayState(RenderType.OVERLAY)
                .createCompositeState(true)));

    @Redirect(method = "render(Lnet/minecraft/world/entity/decoration/Painting;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType usePaintingLayer(ResourceLocation texture) {
        return RADIANCE_PAINTING_LAYER.apply(texture);
    }
}
