package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.veil.VeilAdapter;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderStateShard.class)
public class RenderPhaseMixins {

    @Mutable
    @Final
    @Shadow
    private Runnable setupState;

    @Mutable
    @Final
    @Shadow
    private Runnable clearState;

    @Unique
    public void setBeginAction(Runnable beginAction) {
        this.setupState = beginAction;
    }

    @Unique
    public void setEndAction(Runnable endAction) {
        this.clearState = endAction;
    }

    @Inject(method = {"setupRenderState", "clearRenderState"}, at = @At("HEAD"),
        cancellable = true)
    private void radiance$skipVeilDynamicBufferState(CallbackInfo ci) {
        if (VeilAdapter.shouldSkipRenderState(this)) {
            ci.cancel();
        }
    }
}
