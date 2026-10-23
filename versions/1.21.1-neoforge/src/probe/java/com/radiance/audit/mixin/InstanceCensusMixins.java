package com.radiance.audit.mixin;

import com.radiance.audit.InstanceInputCensus;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class InstanceCensusMixins {
    @Mixin(value = StorageVertexConsumerProvider.class, remap = false)
    public static class Provider {
        @Inject(method = "enablePartModels", at = @At("HEAD"))
        private void owner(Object owner, Object renderer, boolean block, CallbackInfo ci) {
            InstanceInputCensus.owner(this, renderer);
        }

        @Inject(method = "getBuffer", at = @At("HEAD"))
        private void selected(CallbackInfoReturnable<?> ci) {
            InstanceInputCensus.selected(this);
        }
    }

    @Mixin(targets = "com.radiance.client.vertex.BoundPartCapture", remap = false)
    public static class Bindings {
        @Inject(method = "emit", at = @At("HEAD"))
        private static void replay(
                @org.spongepowered.asm.mixin.injection.Coerce Object input, CallbackInfo ci) {
            InstanceInputCensus.replay(input);
        }
    }

    @Mixin(targets = "com.radiance.client.vertex.PartModelCapture", remap = false)
    public static class Versions {
        @Inject(
                method =
                        "localVersion(Lcom/radiance/client/vertex/PartModelCapture$Snapshot;Lnet/minecraft/client/renderer/RenderType;Lcom/radiance/client/texture/TextureTasks$Owner;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/radiance/render/contract/SurfaceMaterial$MaterialPolicy;IIILnet/minecraft/client/renderer/RenderType;Lcom/radiance/client/texture/TextureTasks$Owner;)Lcom/radiance/client/vertex/PartModelCapture$Entry;",
                at = @At("RETURN"))
        private static void rejected(CallbackInfoReturnable<?> ci) {
            if (ci.getReturnValue() == null) InstanceInputCensus.rejectedVersion();
        }
    }
}
