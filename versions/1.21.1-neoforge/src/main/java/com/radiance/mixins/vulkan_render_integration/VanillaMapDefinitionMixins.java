package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.adapter.vanilla.VanillaMapRequests;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.SourceCompatibility;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class VanillaMapDefinitionMixins {
    private VanillaMapDefinitionMixins() {}

    @Mixin(MapRenderer.class)
    public interface Decorations {
        @Accessor("decorationTextures")
        MapDecorationTextureManager radiance$decorations();
    }

    @Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
    public abstract static class Maps {
        @Shadow private MapItemSavedData data;
        @Shadow @Final private RenderType renderType;
        @Shadow private boolean requiresUpload;
        @Shadow @Final private MapRenderer this$0;

        @Invoker("updateTexture")
        protected abstract void radiance$updateTexture();

        @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
        private void radiance$mapRequests(
                PoseStack pose,
                MultiBufferSource output,
                boolean frame,
                int light,
                CallbackInfo ci) {
            if (!(output instanceof StorageVertexConsumerProvider storage)
                    || !storage.rigidBakedEnabled()
                    || !SourceCompatibility.producer(getClass())
                    || !SourceCompatibility.producer(MapRenderer.class)) return;
            if (requiresUpload) {
                radiance$updateTexture();
                requiresUpload = false;
            }
            VanillaMapRequests.collect(
                    pose,
                    output,
                    frame,
                    light,
                    data,
                    renderType,
                    ((Decorations) this$0).radiance$decorations());
            ci.cancel();
        }
    }
}
