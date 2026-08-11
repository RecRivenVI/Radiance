package com.radiance.compatibility.veil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.render.AppliedShaderState;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.ShaderDefinition;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.program.ShaderUniformCache;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.ShaderSourceSet;
import foundry.veil.api.client.render.shader.compiler.ShaderCompiler;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.api.client.render.shader.program.ProgramDefinition;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import java.util.Set;
import java.util.function.BiConsumer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.minecraft.resources.ResourceLocation;

/** Draw-facing facade for Veil programs; the parser/compiler remains in {@link VeilShaderBridge}. */
public final class VeilShaderAdapter {
    private VeilShaderAdapter() {
    }

    public static void applyWrapper(ShaderProgramImpl.Wrapper wrapper) {
        RenderSystem.assertOnRenderThread();
        AppliedShaderState.apply(wrapper);
    }

    public static void clearWrapper() {
        AppliedShaderState.clear();
    }

    public static void setDefaultUniforms(ShaderProgram program, VertexFormat.Mode mode,
        Matrix4fc modelView, Matrix4fc projection) {
        VeilShaderBridge.setDefaultUniforms(program, mode, modelView, projection);
    }

    public static CompiledShader compileStage(int type, VeilShaderSource source)
        throws ShaderException {
        return VeilShaderBridge.compileStage(type, source);
    }

    public static ShaderProgramImpl.CompiledProgram compileProgram(ResourceLocation name,
        int activeBuffers, ProgramDefinition definition, ShaderSourceSet sourceSet,
        ShaderCompiler compiler) throws ShaderException, IOException {
        return VeilShaderBridge.compileProgram(name, activeBuffers, definition, sourceSet,
            compiler);
    }

    public static void replaceProgram(ShaderProgram owner,
        Int2ObjectMap<ShaderProgramImpl.CompiledProgram> programs, int activeBuffers,
        ShaderProgramImpl.CompiledProgram replacement,
        java.util.function.Consumer<ShaderProgramImpl.CompiledProgram> apply) {
        ShaderProgramImpl.CompiledProgram previous = programs.put(activeBuffers, replacement);
        if (previous != null) {
            VeilShaderBridge.releaseProgram(owner, previous.program());
            previous.free();
        }
        VeilShaderBridge.attachProgram(owner, replacement.program());
        apply.accept(replacement);
    }

    public static void bindProgramAndBlocks(ShaderProgram program,
        Object2ObjectMap<CharSequence, ShaderBlock<?>> shaderBlocks) {
        VeilShaderBridge.bindProgram(program);
        for (Object2ObjectMap.Entry<CharSequence, ShaderBlock<?>> entry
            : shaderBlocks.object2ObjectEntrySet()) {
            VeilShaderBridge.bindBlock(entry.getKey(), entry.getValue());
        }
    }

    public static void setTexture(ShaderProgram program, CharSequence name, int textureId) {
        VeilShaderBridge.setTexture(program, name, textureId);
    }

    public static void releaseProgram(ShaderProgram program, int nativeId) {
        VeilShaderBridge.releaseProgram(program, nativeId);
    }

    public static void releaseStage(int nativeId) {
        VeilShaderBridge.releaseStage(nativeId);
    }

    public static void bindBlock(CharSequence name, ShaderBlock<?> block) {
        VeilShaderBridge.bindBlock(name, block);
    }

    public static void unbindBlock(ShaderBlock<?> block) {
        VeilShaderBridge.unbindBlock(block);
    }

    public static ExternalShaderMetadata metadata(ShaderProgram program,
        VertexFormat.Mode drawMode) {
        return VeilShaderBridge.metadata(program, drawMode);
    }

    public static void writeUniforms(ShaderProgram program, ShaderDefinition definition,
        ByteBuffer destination) {
        VeilShaderBridge.writeUniforms(program, definition, destination);
    }

    public static VertexFormat vertexFormat(int nativeId) {
        return VeilShaderBridge.format(nativeId);
    }

    public static void freeCompiledProgram(Int2ObjectMap<CompiledShader> shaders,
        ShaderUniformCache cache, Set<String> dependencies) {
        shaders.values().forEach(CompiledShader::free);
        shaders.clear();
        cache.clear();
        dependencies.clear();
    }

    public static void populateUniformCache(int nativeId,
        Object2ObjectMap<String, ShaderUniformCache.Uniform> samplers,
        Object2ObjectMap<String, ShaderUniformCache.Uniform> uniforms,
        Object2ObjectMap<String, ShaderUniformCache.UniformBlock> uniformBlocks,
        Object2ObjectMap<String, ShaderUniformCache.StorageBlock> storageBlocks) {
        samplers.clear();
        uniforms.clear();
        uniformBlocks.clear();
        storageBlocks.clear();
        uniforms.putAll(VeilShaderBridge.uniforms(nativeId));
        samplers.putAll(VeilShaderBridge.samplers(nativeId));
        uniformBlocks.putAll(VeilShaderBridge.uniformBlocks(nativeId));
    }

    public static ByteBuffer snapshotBlock(Object value, int size,
        BiConsumer<Object, ByteBuffer> serializer) {
        ByteBuffer result = ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());
        if (value != null) {
            serializer.accept(value, result);
        }
        result.position(0).limit(size);
        return result;
    }

    public static void bindProgram(ShaderProgram program) {
        VeilShaderBridge.bindProgram(program);
    }

    public static void unbindProgram() {
        VeilShaderBridge.unbindProgram();
    }

    public static UnsupportedOperationException unsupportedStorageBlock(CharSequence name) {
        return new UnsupportedOperationException(
            "Radiance Veil raster shaders do not support storage block " + name);
    }
}
