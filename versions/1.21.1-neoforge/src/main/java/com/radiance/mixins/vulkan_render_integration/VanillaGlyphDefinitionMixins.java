package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.GlyphLocalModel;
import com.radiance.mixin_related.SourceCompatibility;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedGlyph.class)
public abstract class VanillaGlyphDefinitionMixins {
    @Shadow @Final private float u0, u1, v0, v1, left, right, up, down;
    @Unique private final Object radiance$upright = new Object();
    @Unique private final Object radiance$italic = new Object();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void radiance$glyphResource(
            boolean italic,
            float x,
            float y,
            Matrix4f matrix,
            VertexConsumer output,
            float r,
            float g,
            float b,
            float a,
            int light,
            CallbackInfo ci) {
        if (GlyphLocalModel.eligible(output)
                && SourceCompatibility.producer(getClass())
                && GlyphLocalModel.request(
                        italic ? radiance$italic : radiance$upright,
                        italic,
                        x,
                        y,
                        matrix,
                        output,
                        r,
                        g,
                        b,
                        a,
                        light,
                        left,
                        right,
                        up,
                        down,
                        u0,
                        u1,
                        v0,
                        v1)) ci.cancel();
    }
}
