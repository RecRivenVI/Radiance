package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.FramebufferStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FramebufferStack.class, remap = false)
public abstract class FramebufferStackMixins {
    @Inject(method = "push", at = @At("HEAD"), cancellable = true)
    private static void radiance$push(@Nullable ResourceLocation name, CallbackInfo ci) {
        VeilFramebufferCompatibility.push(name);
        ci.cancel();
    }

    @Inject(method = "pop", at = @At("HEAD"), cancellable = true)
    private static void radiance$pop(@Nullable ResourceLocation name, CallbackInfo ci) {
        VeilFramebufferCompatibility.pop(name);
        ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private static void radiance$clear(CallbackInfo ci) {
        VeilFramebufferCompatibility.clearStack();
        ci.cancel();
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private static void radiance$isEmpty(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(VeilFramebufferCompatibility.isStackEmpty());
    }
}
