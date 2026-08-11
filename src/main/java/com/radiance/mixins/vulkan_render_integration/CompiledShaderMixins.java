package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Program.class)
public abstract class CompiledShaderMixins implements ICompiledShaderExt {

    @Unique
    private static final AtomicInteger RADIANCE$NEXT_VIRTUAL_SHADER_ID = new AtomicInteger(1);
    @Unique
    private static final Map<Integer, String> RADIANCE$SOURCES = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private Program.Type type;
    @Shadow
    @Final
    private String name;
    @Shadow
    private int id;

    @Unique
    private String radiance$resolvedSource;

    @Inject(method = "compileShaderInternal", at = @At("HEAD"), cancellable = true)
    private static void compileWithoutOpenGL(Program.Type type, String name, InputStream input,
        String sourcePack, GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir)
        throws IOException {
        String source = IOUtils.toString(input, StandardCharsets.UTF_8);
        if (source == null) {
            throw new IOException("Could not load program " + type.getName());
        }
        int virtualId = RADIANCE$NEXT_VIRTUAL_SHADER_ID.getAndIncrement();
        RADIANCE$SOURCES.put(virtualId, String.join("", preprocessor.process(source)));
        cir.setReturnValue(virtualId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureResolvedSource(Program.Type type, int id, String name, CallbackInfo ci) {
        this.radiance$resolvedSource = RADIANCE$SOURCES.remove(id);
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void closeWithoutOpenGL(CallbackInfo ci) {
        if (this.id == -1) {
            return;
        }
        this.id = -1;
        this.type.getPrograms().remove(this.name);
        ci.cancel();
    }

    @Override
    public String radiance$getResolvedSource() {
        return this.radiance$resolvedSource;
    }

    @Override
    public void radiance$setResolvedSource(String resolvedSource) {
        this.radiance$resolvedSource = resolvedSource;
    }
}
