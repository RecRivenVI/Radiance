package com.radiance.compatibility.veil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RasterDrawBridge;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.CustomVertexLayout;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderField;
import com.radiance.client.shader.ShaderRegistry;
import com.radiance.compatibility.veil.VeilSimulatedShaderAdapter;
import foundry.veil.api.client.render.shader.ShaderSourceSet;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.compiler.ShaderCompiler;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import foundry.veil.api.client.render.shader.program.ProgramDefinition;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.program.ShaderUniformCache;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import static org.lwjgl.opengl.GL20C.GL_BOOL;
import static org.lwjgl.opengl.GL20C.GL_BOOL_VEC2;
import static org.lwjgl.opengl.GL20C.GL_BOOL_VEC3;
import static org.lwjgl.opengl.GL20C.GL_BOOL_VEC4;
import static org.lwjgl.opengl.GL20C.GL_FLOAT;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_MAT2;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_MAT3;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_MAT4;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_VEC2;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_VEC3;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_VEC4;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_INT;
import static org.lwjgl.opengl.GL20C.GL_INT_VEC2;
import static org.lwjgl.opengl.GL20C.GL_INT_VEC3;
import static org.lwjgl.opengl.GL20C.GL_INT_VEC4;
import static org.lwjgl.opengl.GL20C.GL_SAMPLER_2D;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL40C.GL_TESS_CONTROL_SHADER;
import static org.lwjgl.opengl.GL40C.GL_TESS_EVALUATION_SHADER;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_VEC2;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_VEC3;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_VEC4;

/** CPU-side Veil program model consumed by Radiance's real Vulkan raster pipeline. */
public final class VeilShaderBridge {

    private static final int FIRST_STAGE_HANDLE = 0x20000000;
    private static final AtomicInteger NEXT_STAGE_HANDLE = new AtomicInteger(FIRST_STAGE_HANDLE);
    private static final Map<Integer, Stage> STAGES = new ConcurrentHashMap<>();
    private static final Map<Integer, ProgramModel> MODELS_BY_NATIVE_ID = new ConcurrentHashMap<>();
    private static final Map<ShaderProgram, ProgramModel> MODELS =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ShaderProgram, Map<String, Integer>> TEXTURES =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<ShaderProgram> CURRENT_PROGRAM = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, ShaderBlock<?>>> BOUND_BLOCKS =
        ThreadLocal.withInitial(HashMap::new);

    private static final Pattern ATTRIBUTE = Pattern.compile(
        "(?m)^\\s*layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s+\\w+\\s+(\\w+)\\s*;");
    private static final Pattern BLOCK = Pattern.compile(
        "(?s)(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+(\\w+)\\s*\\{(.*?)\\}\\s*(\\w+)?\\s*;");
    private static final Pattern BLOCK_FIELD = Pattern.compile(
        "(?m)^\\s*(\\w+)\\s+(\\w+)\\s*(?:\\[([^]]+)])?\\s*;");
    private static final Pattern UNIFORM = Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(\\w+)"
            + "\\s*(?:\\[([^]]+)])?\\s+(\\w+)\\s*(?:\\[([^]]+)])?"
            + "\\s*(?:=[^;]+)?;");
    private static final Pattern PATCH_VERTICES = Pattern.compile(
        "layout\\s*\\(\\s*vertices\\s*=\\s*(\\d+)\\s*\\)\\s*out\\s*;");

    private VeilShaderBridge() {
    }

    public static CompiledShader compileStage(int type, VeilShaderSource source)
        throws ShaderException {
        if (type != GL_VERTEX_SHADER && type != GL_FRAGMENT_SHADER
            && type != GL_TESS_CONTROL_SHADER && type != GL_TESS_EVALUATION_SHADER) {
            throw new ShaderException(
                "Radiance Vulkan raster bridge does not support this Veil shader stage",
                "stage=0x%04X".formatted(type));
        }
        int handle = NEXT_STAGE_HANDLE.getAndIncrement();
        STAGES.put(handle, new Stage(type, source));
        return new CompiledShader(source.sourceId(), handle, source.uniformBindings(),
            source.definitionDependencies(), source.includes());
    }

    public static void releaseStage(int handle) {
        if (handle >= FIRST_STAGE_HANDLE) {
            STAGES.remove(handle);
        }
    }

    public static ShaderProgramImpl.CompiledProgram compileProgram(ResourceLocation name,
        int activeBuffers, ProgramDefinition definition, ShaderSourceSet sourceSet,
        ShaderCompiler compiler) throws IOException, ShaderException {
        if (definition == null) {
            throw new ShaderException("Veil program has no source definition", name.toString());
        }

        Int2ObjectMap<CompiledShader> shaders = new Int2ObjectArrayMap<>(2);
        try {
            for (Int2ObjectMap.Entry<ResourceLocation> entry : definition.shaders()
                .int2ObjectEntrySet()) {
                int type = entry.getIntKey();
                if (type != GL_VERTEX_SHADER && type != GL_FRAGMENT_SHADER
                    && type != GL_TESS_CONTROL_SHADER && type != GL_TESS_EVALUATION_SHADER) {
                    throw new ShaderException(
                        "Radiance cannot load non-raster Veil program " + name,
                        "unsupported stage=0x%04X".formatted(type));
                }
                CompiledShader shader = compiler.compile(type,
                    sourceSet.getTypeConverter(type).idToFile(entry.getValue()));
                shaders.put(type, shader);
            }
            if (!shaders.containsKey(GL_VERTEX_SHADER)) {
                shaders.put(GL_VERTEX_SHADER, compileStage(GL_VERTEX_SHADER,
                    new VeilShaderSource(null, """
                        void main() {
                            const vec2 positions[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
                            gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
                        }
                        """)));
            }
            if (!shaders.containsKey(GL_FRAGMENT_SHADER)) {
                shaders.put(GL_FRAGMENT_SHADER, compileStage(GL_FRAGMENT_SHADER,
                    ShaderProgramImpl.DUMMY_FRAGMENT_SHADER));
            }

            Stage vertex = requireStage(shaders.get(GL_VERTEX_SHADER).id());
            Stage fragment = requireStage(shaders.get(GL_FRAGMENT_SHADER).id());
            Stage tessControl = shaders.containsKey(GL_TESS_CONTROL_SHADER)
                ? requireStage(shaders.get(GL_TESS_CONTROL_SHADER).id()) : null;
            Stage tessEvaluation = shaders.containsKey(GL_TESS_EVALUATION_SHADER)
                ? requireStage(shaders.get(GL_TESS_EVALUATION_SHADER).id()) : null;
            if ((tessControl == null) != (tessEvaluation == null)) {
                throw new ShaderException("Incomplete Veil tessellation stage pair", name.toString());
            }
            ProgramModel model = ProgramModel.parse(name.toString(), vertex.source.sourceCode(),
                tessControl == null ? null : tessControl.source.sourceCode(),
                tessEvaluation == null ? null : tessEvaluation.source.sourceCode(),
                fragment.source.sourceCode());
            ShaderDefinition warm;
            try {
                warm = ShaderRegistry.createExternal(model.metadata, VertexFormat.Mode.QUADS);
            } catch (RuntimeException exception) {
                throw new ShaderException("Failed to compile Vulkan Veil program " + name,
                    exception.toString());
            }
            MODELS_BY_NATIVE_ID.put(warm.nativeId(), model);

            Int2ObjectMap<CompiledShader> view = Int2ObjectMaps.unmodifiable(shaders);
            ShaderUniformCache cache = new ShaderUniformCache(warm::nativeId);
            Set<String> dependencies = new HashSet<>();
            shaders.values().forEach(shader -> dependencies.addAll(shader.definitionDependencies()));
            return new ShaderProgramImpl.CompiledProgram(warm.nativeId(), shaders, view, cache,
                dependencies, activeBuffers);
        } catch (IOException | ShaderException | RuntimeException | Error failure) {
            shaders.values().forEach(CompiledShader::free);
            throw failure;
        }
    }

    private static Stage requireStage(int handle) throws ShaderException {
        Stage stage = STAGES.get(handle);
        if (stage == null) {
            throw new ShaderException("Missing captured Veil shader stage", "handle=" + handle);
        }
        return stage;
    }

    public static void attachProgram(ShaderProgram program, int nativeId) {
        ProgramModel model = MODELS_BY_NATIVE_ID.get(nativeId);
        if (model == null) {
            throw new IllegalStateException("Missing Vulkan model for Veil program id " + nativeId);
        }
        MODELS.put(program, model);
    }

    public static void releaseProgram(ShaderProgram program, int nativeId) {
        ProgramModel model = MODELS.remove(program);
        TEXTURES.remove(program);
        if (model != null) {
            MODELS_BY_NATIVE_ID.values().removeIf(candidate -> candidate == model);
        } else {
            MODELS_BY_NATIVE_ID.remove(nativeId);
        }
        if (CURRENT_PROGRAM.get() == program) {
            CURRENT_PROGRAM.remove();
        }
    }

    public static ExternalShaderMetadata metadata(ShaderProgram program,
        VertexFormat.Mode drawMode) {
        return requireModel(program).metadata;
    }

    public static void bindProgram(ShaderProgram program) {
        requireModel(program);
        CURRENT_PROGRAM.set(program);
    }

    public static void setDefaultUniforms(ShaderProgram program, VertexFormat.Mode mode,
        Matrix4fc modelView, Matrix4fc projection) {
        VeilSimulatedShaderAdapter.setLightingDefaults(program, modelView);
        ShaderUniform uniform = program.getUniform("ModelViewMat");
        if (uniform != null) uniform.setMatrix(modelView, false);
        uniform = program.getUniform("ProjMat");
        if (uniform != null) uniform.setMatrix(projection, false);
        uniform = program.getUniform("TextureMat");
        if (uniform != null) uniform.setMatrix(RenderSystem.getTextureMatrix(), false);

        float[] color = RenderSystem.getShaderColor();
        uniform = program.getUniform("ColorModulator");
        if (uniform != null) uniform.setVector(color[0], color[1], color[2], color[3]);
        uniform = program.getUniform("GlintAlpha");
        if (uniform != null) uniform.setFloat(RenderSystem.getShaderGlintAlpha());
        uniform = program.getUniform("FogStart");
        if (uniform != null) uniform.setFloat(RenderSystem.getShaderFogStart());
        uniform = program.getUniform("FogEnd");
        if (uniform != null) uniform.setFloat(RenderSystem.getShaderFogEnd());
        float[] fog = RenderSystem.getShaderFogColor();
        uniform = program.getUniform("FogColor");
        if (uniform != null) uniform.setVector(fog[0], fog[1], fog[2], fog[3]);
        uniform = program.getUniform("FogShape");
        if (uniform != null) uniform.setInt(RenderSystem.getShaderFogShape().getIndex());
        uniform = program.getUniform("GameTime");
        if (uniform != null) uniform.setFloat(RenderSystem.getShaderGameTime());
        if (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP) {
            uniform = program.getUniform("LineWidth");
            if (uniform != null) uniform.setFloat(RenderSystem.getShaderLineWidth());
        }
        uniform = program.getUniform("ScreenSize");
        if (uniform != null) {
            var window = Minecraft.getInstance().getWindow();
            uniform.setVector(window.getWidth(), window.getHeight());
        }
    }

    public static void unbindProgram() {
        CURRENT_PROGRAM.remove();
    }

    public static ShaderProgram currentProgram() {
        ShaderProgram program = CURRENT_PROGRAM.get();
        if (program == null) {
            throw new IllegalStateException("Veil draw requires a bound ShaderProgram");
        }
        return program;
    }

    public static void setTexture(ShaderProgram program, CharSequence name, int textureId) {
        TEXTURES.computeIfAbsent(program, ignored -> new ConcurrentHashMap<>())
            .put(name.toString(), textureId);
    }

    public static void bindBlock(CharSequence name, ShaderBlock<?> block) {
        BOUND_BLOCKS.get().put(name.toString(), block);
    }

    public static void unbindBlock(ShaderBlock<?> block) {
        BOUND_BLOCKS.get().values().removeIf(candidate -> candidate == block);
    }

    public static void writeUniforms(ShaderProgram program, ShaderDefinition definition,
        ByteBuffer destination) {
        ProgramModel model = requireModel(program);
        if (model.metadata.patchControlPoints() > 0
            && VeilPatchState.current() != model.metadata.patchControlPoints()) {
            throw new IllegalStateException("Veil patch state mismatch for "
                + model.metadata.name() + ": expected " + model.metadata.patchControlPoints()
                + ", active " + VeilPatchState.current());
        }
        if (VeilSimulatedShaderAdapter.DIAGRAM_POST.equals(model.metadata.name())) {
            float width = com.radiance.compatibility.simulated.SimulatedDiagramCompatibility.logicalWidth();
            float height = com.radiance.compatibility.simulated.SimulatedDiagramCompatibility.logicalHeight();
            if (width <= 0 || height <= 0) {
                int[] dimensions = FramebufferProxy.dimensions(FramebufferProxy.DRAW_FRAMEBUFFER);
                width = dimensions[0]; height = dimensions[1];
            }
            program.getUniformSafe("RadianceDiagramLogicalSize").setVector(width, height);
            program.getUniformSafe("InSize").setVector(width, height);
        }
        Map<String, Integer> textures = TEXTURES.getOrDefault(program, Map.of());
        for (Value value : model.values) {
            ShaderUniform uniform = program.getUniform(value.sourceName);
            if (!(uniform instanceof VeilShaderUniformData data)) {
                continue;
            }
            copyValue(data.radiance$getUniformBytes(), destination, value.field);
        }
        for (Sampler sampler : model.samplers) {
            int textureId = resolveSamplerTexture(textures, sampler.sourceName,
                sampler.slot, VeilShaderBridge::resolveImplicitSamplerTexture);
            if (textureId == 0 && "Sampler2".equals(sampler.sourceName)) {
                textureId = ShaderProxy.getWhiteTextureId();
            }
            destination.putInt(sampler.field.offset(),
                ShaderProxy.encodeSamplerTextureId(textureId));
        }
        Map<String, ShaderBlock<?>> blocks = BOUND_BLOCKS.get();
        for (BlockLayout block : model.blocks) {
            ShaderBlock<?> shaderBlock = blocks.get(block.sourceName);
            if (!(shaderBlock instanceof VeilShaderBlockData data)) {
                throw new IllegalStateException("Veil shader block is not bound: "
                    + block.sourceName + " for " + model.metadata.name());
            }
            ByteBuffer source = data.radiance$snapshotBlock();
            if (source.remaining() < block.size) {
                throw new IllegalStateException("Veil shader block " + block.sourceName
                    + " is smaller than its std140 declaration");
            }
            ByteBuffer copy = source.slice();
            copy.limit(block.size);
            ByteBuffer target = destination.duplicate();
            target.position(block.offset).limit(block.offset + block.size);
            target.put(copy);
        }
        if (model.fragmentCoordinateHeightOffset >= 0) {
            int[] dimensions = FramebufferProxy.dimensions(FramebufferProxy.DRAW_FRAMEBUFFER);
            if (dimensions == null || dimensions.length < 2 || dimensions[1] <= 0) {
                throw new IllegalStateException(
                    "Levitite fragment coordinate requires a drawable target height");
            }
            destination.putFloat(model.fragmentCoordinateHeightOffset, dimensions[1]);
        }
    }

    private static void copyValue(ByteBuffer source, ByteBuffer destination, ShaderField field) {
        ByteBuffer input = source.slice().order(source.order());
        if (field.kind() == ShaderField.Kind.MATRIX) {
            int dimension = field.componentCount();
            int sourceMatrixSize = dimension * dimension * 4;
            int targetMatrixStride = field.size() / field.arrayLength();
            for (int element = 0; element < field.arrayLength(); element++) {
                for (int column = 0; column < dimension; column++) {
                    for (int row = 0; row < dimension; row++) {
                        destination.putFloat(field.offset() + element * targetMatrixStride
                                + column * 16 + row * 4,
                            input.getFloat(element * sourceMatrixSize
                                + (column * dimension + row) * 4));
                    }
                }
            }
            return;
        }
        if (field.arrayLength() > 1) {
            int sourceElementSize = field.componentCount() * 4;
            int targetStride = field.size() / field.arrayLength();
            for (int element = 0; element < field.arrayLength(); element++) {
                for (int offset = 0; offset < sourceElementSize; offset++) {
                    destination.put(field.offset() + element * targetStride + offset,
                        input.get(element * sourceElementSize + offset));
                }
            }
            return;
        }
        int bytes = Math.min(input.remaining(), field.size());
        ByteBuffer target = destination.duplicate();
        target.position(field.offset()).limit(field.offset() + bytes);
        input.limit(bytes);
        target.put(input);
    }

    static void copyUniformValueForTest(ByteBuffer source, ByteBuffer destination,
        ShaderField field) {
        copyValue(source, destination, field);
    }

    static int resolveSamplerTexture(Map<String, Integer> textures, String name, int slot,
        IntUnaryOperator fallback) {
        Integer bound = textures.get(name);
        return bound != null ? bound : fallback.applyAsInt(slot);
    }

    private static int resolveImplicitSamplerTexture(int slot) {
        if (slot < 0) {
            return 0;
        }
        int shaderTexture = slot < 12 ? RenderSystem.getShaderTexture(slot) : 0;
        return TextureProxy.effectiveTexture(slot, shaderTexture);
    }

    static int resolveImplicitSamplerTexture(int slot, IntUnaryOperator legacyTextureUnits,
        IntUnaryOperator renderSystemSlots) {
        if (slot < 0) {
            return 0;
        }
        int textureId = legacyTextureUnits.applyAsInt(slot);
        return textureId > 0 ? textureId : renderSystemSlots.applyAsInt(slot);
    }

    public static Map<String, ShaderUniformCache.Uniform> uniforms(int nativeId) {
        ProgramModel model = requireModel(nativeId);
        return model.uniformCache;
    }

    public static Map<String, ShaderUniformCache.Uniform> samplers(int nativeId) {
        ProgramModel model = requireModel(nativeId);
        return model.samplerCache;
    }

    public static Map<String, ShaderUniformCache.UniformBlock> uniformBlocks(int nativeId) {
        ProgramModel model = requireModel(nativeId);
        return model.blockCache;
    }

    /**
     * Returns the std140-parsed uniform block of a compiled Veil program so the Vulkan
     * compatibility layer can replace Veil's OpenGL layout reflection queries.
     */
    public static ShaderUniformCache.UniformBlock block(ShaderProgram program, String name) {
        ProgramModel model = MODELS.get(program);
        return model == null ? null : model.blockCache.get(name);
    }

    public static VertexFormat format(int nativeId) {
        return requireModel(nativeId).metadata.vertexFormat();
    }

    public static void drawScreenQuad() {
        ShaderProgram program = CURRENT_PROGRAM.get();
        if (program == null) {
            throw new IllegalStateException("Veil screen quad requires a bound ShaderProgram");
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.POSITION);
        builder.addVertex(-1.0F, -1.0F, 0.0F);
        builder.addVertex(3.0F, -1.0F, 0.0F);
        builder.addVertex(-1.0F, 3.0F, 0.0F);
        try (MeshData mesh = builder.buildOrThrow()) {
            BufferProxy.VertexIndexBufferHandle buffers =
                BufferProxy.createAndUploadVertexIndexBuffer(mesh);
            RasterDrawBridge.drawWithShader(buffers, VertexFormat.Mode.TRIANGLES,
                mesh.drawState().indexCount(), mesh.drawState().indexType(),
                new Matrix4f(), new Matrix4f(), program.toShaderInstance());
        }
    }

    public static ExternalShaderMetadata analyze(String name, String vertexSource,
        String fragmentSource) throws ShaderException {
        return ProgramModel.parse(name, vertexSource, null, null, fragmentSource).metadata;
    }

    public static ExternalShaderMetadata analyze(String name, String vertexSource,
        String tessellationControlSource, String tessellationEvaluationSource,
        String fragmentSource) throws ShaderException {
        return ProgramModel.parse(name, vertexSource, tessellationControlSource,
            tessellationEvaluationSource, fragmentSource).metadata;
    }

    private static ProgramModel requireModel(ShaderProgram program) {
        ProgramModel model = MODELS.get(program);
        if (model == null) {
            throw new IllegalStateException("Veil ShaderProgram has no Vulkan compilation: "
                + program.getName());
        }
        return model;
    }

    private static ProgramModel requireModel(int nativeId) {
        ProgramModel model = MODELS_BY_NATIVE_ID.get(nativeId);
        if (model == null) {
            throw new IllegalStateException("Unknown Veil Vulkan shader id " + nativeId);
        }
        return model;
    }

    private record Stage(int type, VeilShaderSource source) {
    }

    private record Value(String sourceName, ShaderField field) {
    }

    private record Sampler(String sourceName, int slot, ShaderField field) {
    }

    private record BlockLayout(String sourceName, int offset, int size) {
    }

    private static final class ProgramModel {
        private final ExternalShaderMetadata metadata;
        private final List<Value> values;
        private final List<Sampler> samplers;
        private final List<BlockLayout> blocks;
        private final Map<String, ShaderUniformCache.Uniform> uniformCache;
        private final Map<String, ShaderUniformCache.Uniform> samplerCache;
        private final Map<String, ShaderUniformCache.UniformBlock> blockCache;
        private final int fragmentCoordinateHeightOffset;
        private ProgramModel(ExternalShaderMetadata metadata, List<Value> values,
            List<Sampler> samplers, List<BlockLayout> blocks,
            Map<String, ShaderUniformCache.Uniform> uniformCache,
            Map<String, ShaderUniformCache.Uniform> samplerCache,
            Map<String, ShaderUniformCache.UniformBlock> blockCache,
            int fragmentCoordinateHeightOffset) {
            this.metadata = metadata;
            this.values = values;
            this.samplers = samplers;
            this.blocks = blocks;
            this.uniformCache = uniformCache;
            this.samplerCache = samplerCache;
            this.blockCache = blockCache;
            this.fragmentCoordinateHeightOffset = fragmentCoordinateHeightOffset;
        }

        static ProgramModel parse(String name, String vertexSource,
            String tessellationControlSource, String tessellationEvaluationSource,
            String fragmentSource) throws ShaderException {
            DetectedFormat detectedFormat = detectFormat(name, vertexSource);
            VertexFormat format = detectedFormat.vertexFormat;
            Layout layout = new Layout();
            ParsedSource vertex = parseSource(vertexSource, layout);
            ParsedSource tessControl = tessellationControlSource == null ? null
                : parseSource(tessellationControlSource, layout);
            ParsedSource tessEvaluation = tessellationEvaluationSource == null ? null
                : parseSource(tessellationEvaluationSource, layout);
            fragmentSource = VeilSimulatedShaderAdapter.adaptDiagramFragment(name, fragmentSource);
            ParsedSource fragment = parseSource(fragmentSource, layout);
            int patchControlPoints = 0;
            if (tessControl != null) {
                Matcher patch = PATCH_VERTICES.matcher(tessellationControlSource);
                if (!patch.find()) {
                    throw new ShaderException("Tessellation control shader has no patch size", name);
                }
                patchControlPoints = Integer.parseInt(patch.group(1));
                if (patchControlPoints != 4) {
                    throw new ShaderException("Levitite requires four control points",
                        "actual=" + patchControlPoints);
                }
            }
            int fragmentHeightOffset = -1;
            boolean usesFragmentHeight = Pattern.compile("\\bgl_FragCoord\\b").matcher(fragmentSource).find();
            if (usesFragmentHeight) {
                layout.offset = align(layout.offset, 4);
                fragmentHeightOffset = layout.offset;
                layout.addField(new ShaderField("RadianceTargetHeight", "RadianceTargetHeight",
                    ShaderField.Kind.FLOAT, 1, layout.offset, 4, -1));
                layout.offset += 4;
            }
            int size = align(layout.offset, 16);
            ExternalShaderMetadata metadata = new ExternalShaderMetadata(name, format,
                detectedFormat.customLayout,
                vertex.source, tessControl == null ? null : tessControl.source,
                tessEvaluation == null ? null : tessEvaluation.source, fragment.source,
                patchControlPoints, usesFragmentHeight, size, List.copyOf(layout.fields));
            return new ProgramModel(metadata, List.copyOf(layout.values),
                List.copyOf(layout.samplers), List.copyOf(layout.blocks),
                Map.copyOf(layout.uniformCache), Map.copyOf(layout.samplerCache),
                Map.copyOf(layout.blockCache), fragmentHeightOffset);
        }

        private static DetectedFormat detectFormat(String name, String source) throws ShaderException {
            VertexFormat consumerFormat = VeilSimulatedShaderAdapter.vertexFormat(name, source);
            if (consumerFormat != null) {
                return new DetectedFormat(consumerFormat, null);
            }
            Map<Integer, String> names = new LinkedHashMap<>();
            Matcher matcher = ATTRIBUTE.matcher(source);
            while (matcher.find()) {
                names.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
            }
            if (names.isEmpty()) {
                return new DetectedFormat(DefaultVertexFormat.POSITION, null);
            }
            List<String> ordered = names.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
            if (ordered.equals(List.of("QuadPosition", "SableNormal", "SableData"))) {
                return new DetectedFormat(DefaultVertexFormat.POSITION,
                    CustomVertexLayout.SABLE_FANCY);
            }
            for (Constants.VertexFormats candidate : Constants.VertexFormats.values()) {
                if (candidate.getVertexFormat().getElementAttributeNames().equals(ordered)) {
                    return new DetectedFormat(candidate.getVertexFormat(), null);
                }
            }
            throw new ShaderException("Unsupported Veil vertex layout", ordered.toString());
        }

        private static ParsedSource parseSource(String source, Layout layout)
            throws ShaderException {
            String rewritten = source;
            Matcher blockMatcher = BLOCK.matcher(source);
            StringBuffer blockBuffer = new StringBuffer();
            while (blockMatcher.find()) {
                String blockName = blockMatcher.group(2);
                String instanceName = blockMatcher.group(4);
                ShaderUniformCache.UniformBlock existingBlock = layout.blockCache.get(blockName);
                if (existingBlock != null) {
                    for (ShaderUniformCache.Uniform field : existingBlock.fields()) {
                        String reference = instanceName == null ? field.name()
                            : instanceName + "." + field.name();
                        layout.blockReplacements.put(reference,
                            "radiance_" + sanitize(blockName) + '_' + sanitize(field.name()));
                    }
                    blockMatcher.appendReplacement(blockBuffer, "");
                    continue;
                }
                int base = align(layout.offset, 16);
                int relative = 0;
                List<ShaderUniformCache.Uniform> cacheFields = new ArrayList<>();
                Matcher fieldMatcher = BLOCK_FIELD.matcher(blockMatcher.group(3));
                while (fieldMatcher.find()) {
                    TypeInfo type = type(fieldMatcher.group(1));
                    int arrayLength = parseArrayLength(fieldMatcher.group(3));
                    int alignment = arrayLength > 1 ? 16 : type.alignment;
                    relative = align(relative, alignment);
                    int stride = arrayLength > 1 ? align(type.size, 16) : type.size;
                    int fieldSize = stride * arrayLength;
                    String fieldName = fieldMatcher.group(2);
                    String alias = "radiance_" + sanitize(blockName) + '_' + sanitize(fieldName);
                    ShaderField field = new ShaderField(alias, alias, type.kind,
                        type.components, base + relative, fieldSize, -1, arrayLength);
                    layout.addField(field);
                    cacheFields.add(new ShaderUniformCache.Uniform(fieldName, -1, relative,
                        type.glType, arrayLength));
                    String reference = instanceName == null ? fieldName
                        : instanceName + "." + fieldName;
                    layout.blockReplacements.put(reference, alias);
                    relative += fieldSize;
                }
                int blockSize = align(relative, 16);
                layout.offset = base + blockSize;
                layout.blocks.add(new BlockLayout(blockName, base, blockSize));
                ShaderUniformCache.UniformBlock cache = new ShaderUniformCache.UniformBlock(
                    blockName, layout.blockCache.size(), blockSize,
                    cacheFields.toArray(ShaderUniformCache.Uniform[]::new));
                layout.blockCache.putIfAbsent(blockName, cache);
                blockMatcher.appendReplacement(blockBuffer, "");
            }
            blockMatcher.appendTail(blockBuffer);
            rewritten = blockBuffer.toString();

            Matcher uniformMatcher = UNIFORM.matcher(rewritten);
            while (uniformMatcher.find()) {
                String typeName = uniformMatcher.group(1);
                String uniformName = uniformMatcher.group(3);
                TypeInfo type = type(typeName);
                String typeArray = uniformMatcher.group(2);
                String nameArray = uniformMatcher.group(4);
                if (typeArray != null && nameArray != null) {
                    throw new ShaderException("Veil uniform has two array dimensions", uniformName);
                }
                int arrayLength = parseArrayLength(typeArray != null ? typeArray : nameArray);
                if (type.sampler) {
                    if (!layout.samplerCache.containsKey(uniformName)) {
                        int slot = layout.samplers.size();
                        layout.offset = align(layout.offset, 4);
                        ShaderField field = new ShaderField(uniformName,
                            sanitize(uniformName) + "Index", ShaderField.Kind.SAMPLER, 1,
                            layout.offset, 4, slot);
                        layout.offset += 4;
                        layout.addField(field);
                        layout.samplers.add(new Sampler(uniformName, slot, field));
                        ShaderUniformCache.Uniform cache = new ShaderUniformCache.Uniform(
                            uniformName, slot, 0, type.glType, arrayLength);
                        layout.uniformCache.put(uniformName, cache);
                        layout.samplerCache.put(uniformName, cache);
                    }
                } else if (!layout.uniformCache.containsKey(uniformName)) {
                    int alignment = arrayLength > 1 ? 16 : type.alignment;
                    layout.offset = align(layout.offset, alignment);
                    int stride = arrayLength > 1 ? align(type.size, 16) : type.size;
                    int fieldSize = stride * arrayLength;
                    ShaderField field = new ShaderField(uniformName, sanitize(uniformName),
                        type.kind, type.components, layout.offset, fieldSize, -1, arrayLength);
                    layout.offset += fieldSize;
                    layout.addField(field);
                    layout.values.add(new Value(uniformName, field));
                    layout.uniformCache.put(uniformName, new ShaderUniformCache.Uniform(
                        uniformName, layout.uniformCache.size(), 0, type.glType, arrayLength));
                }
            }

            List<Map.Entry<String, String>> replacements = layout.blockReplacements.entrySet()
                .stream().sorted(Comparator.comparingInt((Map.Entry<String, String> entry) ->
                    entry.getKey().length()).reversed()).toList();
            for (Map.Entry<String, String> replacement : replacements) {
                rewritten = rewritten.replaceAll(
                    "(?<![A-Za-z0-9_])" + Pattern.quote(replacement.getKey())
                        + "(?![A-Za-z0-9_])",
                    Matcher.quoteReplacement(replacement.getValue()));
            }
            return new ParsedSource(rewritten);
        }

        private static TypeInfo type(String name) throws ShaderException {
            return switch (name) {
                case "float" -> new TypeInfo(ShaderField.Kind.FLOAT, 1, 4, 4, GL_FLOAT, false);
                case "vec2" -> new TypeInfo(ShaderField.Kind.FLOAT, 2, 8, 8, GL_FLOAT_VEC2, false);
                case "vec3" -> new TypeInfo(ShaderField.Kind.FLOAT, 3, 16, 16, GL_FLOAT_VEC3, false);
                case "vec4" -> new TypeInfo(ShaderField.Kind.FLOAT, 4, 16, 16, GL_FLOAT_VEC4, false);
                case "int" -> new TypeInfo(ShaderField.Kind.INT, 1, 4, 4, GL_INT, false);
                case "ivec2" -> new TypeInfo(ShaderField.Kind.INT, 2, 8, 8, GL_INT_VEC2, false);
                case "ivec3" -> new TypeInfo(ShaderField.Kind.INT, 3, 16, 16, GL_INT_VEC3, false);
                case "ivec4" -> new TypeInfo(ShaderField.Kind.INT, 4, 16, 16, GL_INT_VEC4, false);
                case "bool" -> new TypeInfo(ShaderField.Kind.BOOL, 1, 4, 4, GL_BOOL, false);
                case "bvec2" -> new TypeInfo(ShaderField.Kind.BOOL, 2, 8, 8, GL_BOOL_VEC2, false);
                case "bvec3" -> new TypeInfo(ShaderField.Kind.BOOL, 3, 16, 16, GL_BOOL_VEC3, false);
                case "bvec4" -> new TypeInfo(ShaderField.Kind.BOOL, 4, 16, 16, GL_BOOL_VEC4, false);
                case "uint" -> new TypeInfo(ShaderField.Kind.UINT, 1, 4, 4, GL_UNSIGNED_INT, false);
                case "uvec2" -> new TypeInfo(ShaderField.Kind.UINT, 2, 8, 8, GL_UNSIGNED_INT_VEC2, false);
                case "uvec3" -> new TypeInfo(ShaderField.Kind.UINT, 3, 16, 16, GL_UNSIGNED_INT_VEC3, false);
                case "uvec4" -> new TypeInfo(ShaderField.Kind.UINT, 4, 16, 16, GL_UNSIGNED_INT_VEC4, false);
                case "mat2" -> new TypeInfo(ShaderField.Kind.MATRIX, 2, 16, 32, GL_FLOAT_MAT2, false);
                case "mat3" -> new TypeInfo(ShaderField.Kind.MATRIX, 3, 16, 48, GL_FLOAT_MAT3, false);
                case "mat4" -> new TypeInfo(ShaderField.Kind.MATRIX, 4, 16, 64, GL_FLOAT_MAT4, false);
                case "sampler2D" -> new TypeInfo(ShaderField.Kind.SAMPLER, 1, 4, 4, GL_SAMPLER_2D, true);
                default -> throw new ShaderException("Unsupported Veil uniform type", name);
            };
        }

        private static int parseArrayLength(String expression) throws ShaderException {
            if (expression == null || expression.isBlank()) {
                return 1;
            }
            int product = 1;
            for (String factor : expression.split("\\*")) {
                try {
                    product = Math.multiplyExact(product, Integer.parseInt(factor.trim()));
                } catch (ArithmeticException | NumberFormatException exception) {
                    throw new ShaderException("Unsupported Veil uniform array length", expression);
                }
            }
            return product;
        }

        private static String sanitize(String value) {
            return value.replaceAll("[^A-Za-z0-9_]", "_");
        }
    }

    private static final class Layout {
        private int offset;
        private final List<ShaderField> fields = new ArrayList<>();
        private final Set<String> fieldNames = new HashSet<>();
        private final List<Value> values = new ArrayList<>();
        private final List<Sampler> samplers = new ArrayList<>();
        private final List<BlockLayout> blocks = new ArrayList<>();
        private final Map<String, String> blockReplacements = new LinkedHashMap<>();
        private final Map<String, ShaderUniformCache.Uniform> uniformCache = new LinkedHashMap<>();
        private final Map<String, ShaderUniformCache.Uniform> samplerCache = new LinkedHashMap<>();
        private final Map<String, ShaderUniformCache.UniformBlock> blockCache = new LinkedHashMap<>();

        void addField(ShaderField field) throws ShaderException {
            if (!fieldNames.add(field.fieldName())) {
                throw new ShaderException("Duplicate Veil shader field", field.fieldName());
            }
            fields.add(field);
        }
    }

    private record ParsedSource(String source) {
    }

    private record DetectedFormat(VertexFormat vertexFormat,
                                  CustomVertexLayout customLayout) {
    }

    private record TypeInfo(ShaderField.Kind kind, int components, int alignment, int size,
                            int glType, boolean sampler) {
    }

    private static int align(int value, int alignment) {
        return Math.ceilDiv(value, alignment) * alignment;
    }
}
