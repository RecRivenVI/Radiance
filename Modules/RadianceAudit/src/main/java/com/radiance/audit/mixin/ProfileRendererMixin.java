package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.vulkan.RendererProxy;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=RendererProxy.class, remap=false)
public abstract class ProfileRendererMixin {
    @WrapMethod(method="acquireContext")
    private static boolean audit$acquire(Operation<Boolean> original) {
        try(var ignored=FrameProfiler.span(Stage.ACQUIRE)) { return original.call(); }
    }
    @WrapMethod(method={"submitCommandAndPresent","submitCommand","present"})
    private static void audit$submit(Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.SUBMIT_PRESENT)) { original.call(); }
    }
}
