package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import java.util.concurrent.atomic.AtomicInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProgramManager.class)
public class ShaderLoaderMixins {

    @Unique
    private static final AtomicInteger RADIANCE$NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);

    @Inject(method = "createProgram", at = @At("HEAD"), cancellable = true)
    private static void createProgramWithoutOpenGL(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RADIANCE$NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement());
    }

    @Inject(method = "linkShader", at = @At("HEAD"), cancellable = true)
    private static void linkWithoutOpenGL(Shader shader, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "glUseProgram", at = @At("HEAD"), cancellable = true)
    private static void useWithoutOpenGL(int id, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "releaseProgram", at = @At("HEAD"), cancellable = true)
    private static void releaseWithoutOpenGL(Shader shader, CallbackInfo ci) {
        shader.getFragmentProgram().close();
        shader.getVertexProgram().close();
        ci.cancel();
    }
}
