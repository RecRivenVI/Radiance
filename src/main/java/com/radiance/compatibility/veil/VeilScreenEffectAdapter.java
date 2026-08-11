package com.radiance.compatibility.veil;

import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.HdrCameraEffectRenderer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Consumes an actual Veil screen-quad call after its uniforms and texture are bound. */
final class VeilScreenEffectAdapter {
    private static final ResourceLocation BLIT_SCREEN_EFFECT =
        ResourceLocation.fromNamespaceAndPath("veil", "core/blit_screen_effect");

    private VeilScreenEffectAdapter() {
    }

    private static final ResourceLocation UNDERWATER =
        ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    static boolean captureCurrentScreenQuad() {
        ShaderProgram program = VeilShaderBridge.currentProgram();
        if (!BLIT_SCREEN_EFFECT.equals(program.getName())) return false;

        ShaderUniform texOffset = program.getUniform("TexOffset");
        ShaderUniform colorModulator = program.getUniform("ColorModulator");
        if (!(texOffset instanceof VeilShaderUniformData texOffsetData)
            || !(colorModulator instanceof VeilShaderUniformData colorModulatorData)) {
            return false;
        }

        float[] offset = readVec4(texOffsetData);
        float[] color = readVec4(colorModulatorData);
        if (offset == null || color == null) return false;
        ScreenQuadParameters parameters = ScreenQuadParameters.from(offset, color);

        Minecraft minecraft = Minecraft.getInstance();
        int textureId = TextureProxy.boundTexture(0);
        int underwaterId = minecraft.getTextureManager().getTexture(UNDERWATER).getId();
        if (textureId == underwaterId) {
            return HdrCameraEffectRenderer.captureVeilFluid(textureId,
                parameters.uStart(), parameters.vStart(), parameters.uSpan(),
                parameters.vSpan(), parameters.alpha(), parameters.brightness());
        }
        return HdrCameraEffectRenderer.captureVeilBlock(textureId,
            parameters.uStart(), parameters.vStart(), parameters.uSpan(),
            parameters.vSpan(), parameters.brightness());
    }

    /** Reads the CPU shadow that Radiance keeps when Veil's OpenGL uniform upload is suppressed. */
    static float[] readVec4(VeilShaderUniformData uniform) {
        ByteBuffer bytes = uniform.radiance$getUniformBytes();
        if (bytes == null || bytes.capacity() < 4 * Float.BYTES) return null;
        bytes.order(ByteOrder.nativeOrder());
        return new float[] {
            bytes.getFloat(0), bytes.getFloat(Float.BYTES),
            bytes.getFloat(2 * Float.BYTES), bytes.getFloat(3 * Float.BYTES)
        };
    }

    record ScreenQuadParameters(float uStart, float vStart, float uSpan, float vSpan,
                                float alpha, float brightness) {
        static ScreenQuadParameters from(float[] texOffset, float[] colorModulator) {
            if (texOffset == null || texOffset.length < 4
                || colorModulator == null || colorModulator.length < 4) {
                throw new IllegalArgumentException("Veil screen effect requires vec4 uniforms");
            }
            return new ScreenQuadParameters(texOffset[0], texOffset[1], texOffset[2],
                texOffset[3], colorModulator[3], colorModulator[0]);
        }
    }
}
