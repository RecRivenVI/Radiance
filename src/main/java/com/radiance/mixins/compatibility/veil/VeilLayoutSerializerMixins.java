package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilLayoutAdapter;
import foundry.veil.api.client.render.VeilShaderBufferLayout;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Veil's OpenGL uniform layout reflection with Radiance's std140 offsets.
 *
 * <p>Veil 4.3.2 queries field offsets through {@code glGetUniformIndices} and
 * {@code glGetActiveUniformsi}, which cannot run without a current OpenGL context.
 * Radiance parses the same blocks while translating the shader, so the offsets are
 * served from that data instead.</p>
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.LayoutSerializer", remap = false)
public abstract class VeilLayoutSerializerMixins {

    @Inject(method = "create", at = @At("HEAD"), remap = false)
    private static void radiance$captureBlock(VeilShaderBufferLayout<?> layout,
        ShaderProgram shader, String name, int blockIndex,
        CallbackInfoReturnable<?> cir) {
        VeilLayoutAdapter.begin(shader, name);
    }

    @Inject(method = "create", at = @At("RETURN"), remap = false)
    private static void radiance$releaseBlock(VeilShaderBufferLayout<?> layout,
        ShaderProgram shader, String name, int blockIndex,
        CallbackInfoReturnable<?> cir) {
        VeilLayoutAdapter.end();
    }

    @Redirect(method = "create", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL31C;glGetUniformIndices(ILjava/lang/CharSequence;)I"),
        remap = false)
    private static int radiance$uniformIndex(int program, CharSequence fieldName) {
        return VeilLayoutAdapter.uniformIndex(fieldName);
    }

    @Redirect(method = "create", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL31C;glGetActiveUniformsi(III)I"), remap = false)
    private static int radiance$uniformOffset(int program, int index, int pname) {
        return VeilLayoutAdapter.uniformOffset(index, pname);
    }

    @Redirect(method = "create", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL31C;glGetActiveUniformBlocki(III)I"), remap = false)
    private static int radiance$blockSize(int program, int blockIndex, int pname) {
        return VeilLayoutAdapter.blockSize();
    }
}
