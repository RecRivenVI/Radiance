package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilVertexArrayAdapter;
import foundry.veil.api.client.render.vertex.VertexArray;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = VertexArray.class, remap = false)
public abstract class VeilVertexArrayMixins {

    @Inject(method = "create()Lfoundry/veil/api/client/render/vertex/VertexArray;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$create(CallbackInfoReturnable<VertexArray> cir) {
        cir.setReturnValue(VeilVertexArrayAdapter.create());
    }

    @Inject(method = "create(I)[Lfoundry/veil/api/client/render/vertex/VertexArray;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$createMany(int count,
        CallbackInfoReturnable<VertexArray[]> cir) {
        cir.setReturnValue(VeilVertexArrayAdapter.createMany(count));
    }

    @Inject(method = "create([Lfoundry/veil/api/client/render/vertex/VertexArray;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$createInto(VertexArray[] arrays, CallbackInfo ci) {
        VeilVertexArrayAdapter.createInto(arrays);
        ci.cancel();
    }

    @Inject(method = "upload(ILjava/nio/ByteBuffer;Lfoundry/veil/api/client/render/vertex/VertexArray$DrawUsage;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$upload(int buffer, ByteBuffer data,
        VertexArray.DrawUsage usage, CallbackInfo ci) {
        VeilVertexArrayAdapter.upload(buffer, data);
        ci.cancel();
    }

    @Inject(method = "unbind()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$unbind(CallbackInfo ci) {
        VeilVertexArrayAdapter.unbind();
        ci.cancel();
    }
}
