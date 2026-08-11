package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GLX;
import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

@Mixin(GLX.class)
public class GLXMixins {

    @Shadow
    private static String cpuInfo;

    @Inject(method = "_getCpuInfo()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void initializeCpuInfoForVulkan(CallbackInfoReturnable<String> cir) {
        if (cpuInfo == null) {
            try {
                CentralProcessor processor = new SystemInfo().getHardware().getProcessor();
                cpuInfo = String.format(Locale.ROOT, "%dx %s",
                        processor.getLogicalProcessorCount(),
                        processor.getProcessorIdentifier().getName())
                    .replaceAll("\\s+", " ");
            } catch (Throwable ignored) {
                // Preserve vanilla's <unknown> result if OSHI cannot inspect the machine.
            }
        }
        cir.setReturnValue(cpuInfo == null ? "<unknown>" : cpuInfo);
    }

    @Redirect(
        method = "_init(IZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlDebug;enableDebugCallback(IZ)V"
        )
    )
    private static void cancelOpenGLDebug(int verbosity, boolean sync) {

    }
}
