package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PaintingModelCapture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.decoration.Painting;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PaintingModelCapture.class, remap = false)
public abstract class ProfilePaintingModelMixin {
    @WrapMethod(method = "capture")
    private static int audit$painting(
            PoseStack pose,
            VertexConsumer consumer,
            Painting painting,
            int width,
            int height,
            TextureAtlasSprite front,
            TextureAtlasSprite back,
            PaintingModelCapture.Renderer renderer,
            Operation<Integer> original) {
        int captured =
                original.call(pose, consumer, painting, width, height, front, back, renderer);
        if (captured == PaintingModelCapture.CAPTURED)
            com.radiance.audit.PaintingModelProbe.verify(pose, consumer, renderer);
        return captured;
    }
}
