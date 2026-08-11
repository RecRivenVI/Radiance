package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.vulkan.BufferProxy;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BufferProxy.class, remap = false)
public abstract class ProfileTextureMappingMixin {
    @WrapMethod(method = "updateMapping()V")
    private static void audit$mapping(Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.TEXTURE_MAPPING)) {
            original.call();
        }
    }
}
