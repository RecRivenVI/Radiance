package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderField;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IExternalShaderProgram;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ShaderProxy {

    private static final ResourceLocation WHITE_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath("radiance",
        "generated/white");
    private static Integer whiteTextureId;
    private ShaderProxy() {
    }

    public static native int registerShader(String shaderKey, int vertexFormatType,
        int drawMode, int uniformSize, String vertexShaderPath, String fragmentShaderPath,
        String tessellationControlShaderPath, String tessellationEvaluationShaderPath,
        int patchControlPoints, int[] customVertexBindings, int[] customVertexAttributes,
        String[] defineNames, String[] defineValues);

    public static native void draw(int vertexId, int indexId, int patchIndexId, int shaderId,
        int indexCount, int patchIndexCount, int indexType, long uniformPtr, int uniformSize);

    public static void draw(BufferProxy.VertexIndexBufferHandle handle, int shaderId, int indexCount,
        int indexType, long uniformPtr, int uniformSize) {
        draw(handle.vertexId, handle.indexId, handle.patchIndexId, shaderId, indexCount,
            handle.patchIndexCount, indexType, uniformPtr, uniformSize);
    }

    public static UniformHandle createUniform(ShaderDefinition shader, ShaderInstance shaderProgram,
        MemoryStack stack) {
        ByteBuffer bb = stack.calloc(shader.uniformBufferSize());
        if (shaderProgram instanceof IExternalShaderProgram external) {
            external.radiance$writeExternalUniforms(shader, bb);
            return new UniformHandle(MemoryUtil.memAddress(bb), shader.uniformBufferSize());
        }
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        writeUniformValues(bb, shader.fields(), ext.radiance$getUniformsValue());
        Map<String, Object> samplerTextures = ext.radiance$getSamplerTexturesValue();
        boolean traceDrawState = RenderAuditBridge.accepts("SHADER_DRAW_STATE");
        List<String> samplerTrace = traceDrawState ? new java.util.ArrayList<>() : List.of();
        boolean suspiciousSampler = false;
        for (ShaderField field : shader.fields()) {
            if (field.isSampler()) {
                int resolvedTextureId = resolveSamplerTextureId(ext, field);
                int encodedTextureId = encodeSamplerTextureId(resolvedTextureId);
                bb.putInt(field.offset(), encodedTextureId);
                if (traceDrawState) {
                    Object source = samplerTextures.get(field.name());
                    int sourceTextureId = source == null ? 0 : resolveTextureId(source);
                    Integer samplerSlot = tryParseSamplerSlot(field.name());
                    int slotTextureId = samplerSlot == null ? 0
                        : RenderSystem.getShaderTexture(samplerSlot);
                    samplerTrace.add(field.name() + "@" + field.offset()
                        + " source=" + (source == null ? "none" : source.getClass().getName())
                        + " sourceId=" + sourceTextureId
                        + " slot=" + (samplerSlot == null ? "none" : samplerSlot)
                        + " slotId=" + slotTextureId
                        + " resolved=" + resolvedTextureId
                        + " encoded=" + encodedTextureId);
                    suspiciousSampler |= resolvedTextureId <= 0
                        || resolvedTextureId >= 4096;
                }
            }
        }
        if (traceDrawState) traceDrawState(shader, samplerTrace, suspiciousSampler);
        return new UniformHandle(MemoryUtil.memAddress(bb), shader.uniformBufferSize());
    }

    private static void traceDrawState(ShaderDefinition shader, List<String> samplerTrace,
        boolean suspiciousSampler) {
        StringBuilder fields = new StringBuilder();
        for (ShaderField field : shader.fields()) {
            if (fields.length() != 0) {
                fields.append(',');
            }
            fields.append(field.name()).append('@').append(field.offset())
                .append(':').append(field.kind()).append('/').append(field.size());
        }
        long auditId = RenderAuditBridge.beginIntent("SHADER_DRAW_STATE", shader.key(),
            "nativeId=" + shader.nativeId() + ", uniformSize=" + shader.uniformBufferSize());
        RenderAuditBridge.transition(auditId,
            suspiciousSampler ? "SUSPICIOUS" : "OBSERVED", "SHADER_UNIFORM",
            "fields=" + fields + ", samplers=" + samplerTrace, true);
    }

    static void writeUniformValues(ByteBuffer bb, List<ShaderField> fields,
        List<Uniform> uniforms) {
        Map<String, Uniform> uniformsByName = new HashMap<>();
        for (Uniform uniform : uniforms) {
            uniformsByName.put(uniform.getName(), uniform);
        }
        for (ShaderField field : fields) {
            if (field.isSampler()) {
                continue;
            }
            if (field.isArray()) {
                if (field.size() % field.arrayLength() != 0) {
                    throw new IllegalStateException(
                        "Shader uniform array has an invalid stride: " + field.name());
                }
                int stride = field.size() / field.arrayLength();
                for (int i = 0; i < field.arrayLength(); i++) {
                    String elementName = field.name() + '[' + i + ']';
                    Uniform uniform = uniformsByName.get(elementName);
                    if (uniform == null) {
                        throw new IllegalStateException(
                            "Missing shader uniform array element " + elementName);
                    }
                    putUniform(bb, field, field.offset() + i * stride, uniform);
                }
            } else {
                Uniform uniform = uniformsByName.get(field.name());
                if (uniform == null) {
                    throw new IllegalStateException(
                        "Missing shader uniform " + field.name());
                }
                putUniform(bb, field, field.offset(), uniform);
            }
        }
    }

    public record UniformHandle(long addr, int size) {

    }

    private static int resolveSamplerTextureId(IShaderProgramExt ext, ShaderField field) {
        Map<String, Object> samplerTextures = ext.radiance$getSamplerTexturesValue();
        if (samplerTextures.containsKey(field.name())) {
            int textureId = resolveTextureId(samplerTextures.get(field.name()));
            if (textureId != 0) {
                return textureId;
            }
        }
        Integer fallbackSlot = tryParseSamplerSlot(field.name());
        if (fallbackSlot != null) {
            int textureId = TextureProxy.effectiveTexture(fallbackSlot,
                RenderSystem.getShaderTexture(fallbackSlot));
            if (textureId != 0) {
                return textureId;
            }
        }
        if (field.samplerSlot() >= 0) {
            int textureId = TextureProxy.effectiveTexture(field.samplerSlot(),
                RenderSystem.getShaderTexture(field.samplerSlot()));
            if (textureId != 0) {
                return textureId;
            }
        }
        if ("Sampler2".equals(field.name())) {
            return getWhiteTextureId();
        }
        return 0;
    }

    private static int resolveTextureId(Object texture) {
        if (texture instanceof RenderTarget renderTarget) {
            return renderTarget.getColorTextureId();
        }
        if (texture instanceof AbstractTexture abstractTexture) {
            return abstractTexture.getId();
        }
        if (texture instanceof Integer textureId) {
            return textureId;
        }
        return 0;
    }

    public static int getWhiteTextureId() {
        Integer cached = whiteTextureId;
        if (cached != null) {
            return cached;
        }

        NativeImage image = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(x, y, 0xFFFFFFFF);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance()
            .getTextureManager()
            .register(WHITE_TEXTURE_ID, texture);
        whiteTextureId = texture.getId();
        return whiteTextureId;
    }

    public static int encodeSamplerTextureId(int textureId) {
        if (textureId <= 0) {
            return textureId;
        }
        if ((textureId & 0x80000000) != 0) {
            throw new IllegalArgumentException("Texture id exceeds sampler payload range: "
                + textureId);
        }
        return TextureProxy.isFramebufferTexture(textureId)
            ? textureId | 0x80000000
            : textureId;
    }

    private static Integer tryParseSamplerSlot(String samplerName) {
        if (!samplerName.startsWith("Sampler")) {
            return null;
        }
        try {
            return Integer.parseInt(samplerName.substring("Sampler".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void putUniform(ByteBuffer bb, ShaderField field, int offset, Uniform uniform) {
        IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
        switch (field.kind()) {
            case INT, UINT, BOOL -> putInts(bb, offset, ext.radiance$getIntDataValue(),
                field.componentCount());
            case FLOAT -> putFloats(bb, offset, ext.radiance$getFloatDataValue(),
                field.componentCount());
            case MATRIX -> putMatrix(bb, offset, field.componentCount(), uniform.getName(),
                ext.radiance$getFloatDataValue());
            case SAMPLER -> throw new IllegalStateException("Sampler fields are written separately");
        }
    }

    private static void putInts(ByteBuffer bb, int offset, IntBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putInt(offset + i * Integer.BYTES, values.get(i));
        }
    }

    private static void putFloats(ByteBuffer bb, int offset, FloatBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putFloat(offset + i * Float.BYTES, values.get(i));
        }
    }

    private static void putMatrix(ByteBuffer bb, int offset, int dimension, String uniformName,
        FloatBuffer values) {
        if (dimension == 4) {
            float[] matrix = new float[16];
            for (int i = 0; i < 16; i++) {
                matrix[i] = values.get(i);
            }
            if ("ProjMat".equals(uniformName)) {
                mapProjectionMatrix(matrix);
            }
            for (int i = 0; i < 16; i++) {
                bb.putFloat(offset + i * Float.BYTES, matrix[i]);
            }
            return;
        }

        int columnStride = Float.BYTES * 4;
        for (int column = 0; column < dimension; column++) {
            for (int row = 0; row < dimension; row++) {
                bb.putFloat(offset + column * columnStride + row * Float.BYTES,
                    values.get(column * dimension + row));
            }
        }
    }

    private static void mapProjectionMatrix(float[] matrix) {
        for (int column = 0; column < 4; column++) {
            int base = column * 4;
            float row0 = matrix[base];
            float row1 = matrix[base + 1];
            float row2 = matrix[base + 2];
            float row3 = matrix[base + 3];
            matrix[base] = row0;
            matrix[base + 1] = -row1;
            matrix[base + 2] = row2 * 0.5F + row3 * 0.5F;
            matrix[base + 3] = row3;
        }
    }
}
