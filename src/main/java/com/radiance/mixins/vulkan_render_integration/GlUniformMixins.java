package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Uniform.class)
public abstract class GlUniformMixins implements IGlUniformExt {

    @Shadow
    @Final
    private int count;

    @Shadow
    @Final
    private int type;

    @Shadow
    @Final
    private IntBuffer intValues;

    @Shadow
    @Final
    private FloatBuffer floatValues;

    @Inject(method = "glGetUniformLocation", at = @At("HEAD"), cancellable = true)
    private static void getUniformLocationWithoutOpenGL(int program, CharSequence name,
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }

    @Inject(method = "glGetAttribLocation", at = @At("HEAD"), cancellable = true)
    private static void getAttribLocationWithoutOpenGL(int program, CharSequence name,
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }

    @Inject(method = "glBindAttribLocation", at = @At("HEAD"), cancellable = true)
    private static void bindAttribLocationWithoutOpenGL(int program, int index, CharSequence name,
        CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "uploadInteger", at = @At("HEAD"), cancellable = true)
    private static void uploadIntegerWithoutOpenGL(int location, int value, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void uploadWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public int radiance$getDataTypeValue() {
        return this.type;
    }

    @Override
    public int radiance$getCountValue() {
        return this.count;
    }

    @Override
    public IntBuffer radiance$getIntDataValue() {
        return this.intValues;
    }

    @Override
    public FloatBuffer radiance$getFloatDataValue() {
        return this.floatValues;
    }
}
