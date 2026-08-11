package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PartModelCapture;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteCoordinateExpander.class)
public interface SpriteCoordinateTakeoverAccessor extends PartModelCapture.SpriteAccess {
    @Override
    @Accessor("delegate")
    VertexConsumer radiance$delegate();

    @Override
    @Accessor("sprite")
    TextureAtlasSprite radiance$sprite();
}
