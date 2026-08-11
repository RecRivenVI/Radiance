package com.radiance.client.shader;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IExternalShaderProgram;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.ShaderInstance;

public final class ShaderRegistry {

    private static final Pattern SAMPLER_UNIFORM_PATTERN = Pattern.compile(
        "^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+sampler\\w+\\s+(\\w+)\\s*;\\s*$");
    private static final Pattern SAMPLER_SLOT_PATTERN = Pattern.compile("\\bSampler(\\d+)\\b");
    private static final Pattern UNIFORM_ARRAY_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+([A-Za-z0-9_]+)"
            + "\\s*(\\[[^]\\r\\n]+])?\\s+([A-Za-z_][A-Za-z0-9_]*)"
            + "\\s*(\\[[^]\\r\\n]+])?\\s*;");
    private static final Pattern ARRAY_ELEMENT_PATTERN = Pattern.compile(
        "^([A-Za-z_][A-Za-z0-9_]*)\\[(\\d+)]$");
    private static final List<ShaderWarmupHistory.Hint> DEFAULT_WARMUP_HINTS = List.of(
        new ShaderWarmupHistory.Hint("position_color", VertexFormat.Mode.QUADS.name()),
        new ShaderWarmupHistory.Hint("position_tex", VertexFormat.Mode.QUADS.name()),
        new ShaderWarmupHistory.Hint("position_tex_color", VertexFormat.Mode.QUADS.name()));
    private static final Object LOCK = new Object();

    private static final Map<ShaderInstance, Map<VertexFormat.Mode, ShaderDefinition>> CACHE =
        new WeakHashMap<>();
    private static final Map<ShaderInstance, Long> LIVE_SHADERS = new WeakHashMap<>();
    private static ShaderWarmupHistory warmupHistory = new ShaderWarmupHistory();
    private static boolean warmupHistoryLoaded;
    private static long generation;
    private static boolean reloadGenerationOpen;

    private ShaderRegistry() {
    }

    public static ShaderDefinition getOrCreate(ShaderInstance shaderProgram,
        VertexFormat.Mode drawMode) {
        synchronized (LOCK) {
            Map<VertexFormat.Mode, ShaderDefinition> shadersByMode = CACHE.computeIfAbsent(
                shaderProgram, ignored -> new EnumMap<>(VertexFormat.Mode.class));
            ShaderDefinition cached = shadersByMode.get(drawMode);
            if (cached != null) {
                return cached;
            }

            ShaderDefinition created = create(shaderProgram, drawMode);
            shadersByMode.put(drawMode, created);
            return created;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
            if (reloadGenerationOpen) {
                LIVE_SHADERS.entrySet().removeIf(entry -> entry.getValue() != generation);
            } else {
                LIVE_SHADERS.clear();
                generation++;
            }
        }
    }

    public static void beginReloadGeneration() {
        synchronized (LOCK) {
            generation++;
            reloadGenerationOpen = true;
            CACHE.clear();
            LIVE_SHADERS.clear();
        }
    }

    public static void finishReloadGeneration(boolean keepCandidates) {
        synchronized (LOCK) {
            reloadGenerationOpen = false;
            if (!keepCandidates) {
                CACHE.clear();
                LIVE_SHADERS.clear();
                generation++;
            }
        }
    }

    public static void registerLiveShader(ShaderInstance shaderProgram) {
        if (!hasCompleteMetadata(shaderProgram)) {
            return;
        }
        synchronized (LOCK) {
            LIVE_SHADERS.put(shaderProgram, generation);
        }
    }

    public static void unregisterLiveShader(ShaderInstance shaderProgram) {
        synchronized (LOCK) {
            LIVE_SHADERS.remove(shaderProgram);
            CACHE.remove(shaderProgram);
        }
    }

    public static void warmupAfterReload() {
        loadWarmupHistory();
        long expectedGeneration;
        synchronized (LOCK) {
            expectedGeneration = generation;
        }
        Runnable warmup = () -> {
            try {
                warmup(expectedGeneration);
            } catch (RuntimeException | LinkageError exception) {
                RadianceClient.LOGGER.warn("Dynamic shader warmup was skipped", exception);
            }
        };
        try {
            if (RenderSystem.isOnRenderThread()) {
                warmup.run();
            } else {
                RenderSystem.recordRenderCall(warmup::run);
            }
        } catch (RuntimeException | LinkageError exception) {
            RadianceClient.LOGGER.warn("Could not schedule dynamic shader warmup", exception);
        }
    }

    public static void saveWarmupHistory() {
        loadWarmupHistory();
        ShaderWarmupHistory history;
        Path path;
        synchronized (LOCK) {
            history = warmupHistory;
            path = warmupHistoryPath();
        }
        if (path == null) {
            return;
        }
        try {
            history.save(path);
        } catch (IOException exception) {
            RadianceClient.LOGGER.warn("Failed to save dynamic shader warmup history to {}",
                path, exception);
        }
    }

    private static ShaderDefinition create(ShaderInstance shaderProgram,
        VertexFormat.Mode drawMode) {
        if (shaderProgram instanceof IExternalShaderProgram external) {
            return createExternal(external.radiance$getExternalShader(drawMode), drawMode);
        }
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        VertexFormat vertexFormat = ext.radiance$getVertexFormat();
        String vertexSource = ext.radiance$getVertexSource();
        String fragmentSource = ext.radiance$getFragmentSource();
        String shaderName = ext.radiance$getShaderName();
        if (vertexFormat == null || vertexSource == null || fragmentSource == null
            || shaderName == null) {
            throw new IllegalStateException("Missing shader metadata for dynamic registration");
        }
        List<ShaderField> fields = buildFields(ext.radiance$getUniformsValue(),
            ext.radiance$getSamplerNamesValue(), vertexSource, fragmentSource);

        ShaderTranslator.Result result = ShaderTranslator.translate(shaderName, vertexFormat,
            vertexSource, fragmentSource, fields);

        String key = buildKey(shaderName, vertexFormat, drawMode, result.vertexSource(),
            result.fragmentSource(), fields);
        Path directory = getShaderDirectory();
        Path vertexPath = directory.resolve(key + ".vert");
        Path fragmentPath = directory.resolve(key + ".frag");
        writeIfChanged(vertexPath, result.vertexSource());
        writeIfChanged(fragmentPath, result.fragmentSource());

        int nativeId = ShaderProxy.registerShader(key,
            Constants.VertexFormats.getValue(vertexFormat),
            Constants.DrawModes.getValue(drawMode),
            result.uniformBufferSize(),
            vertexPath.toString(),
            fragmentPath.toString(),
            null, null, 0,
            null, null,
            new String[0],
            new String[0]);
        if (nativeId < 0) {
            throw new IllegalStateException("Native shader registration returned " + nativeId
                + " for " + shaderName + " / " + drawMode);
        }
        return new ShaderDefinition(key, shaderName, nativeId, result.uniformBufferSize(), fields);
    }

    public static ShaderDefinition createExternal(ExternalShaderMetadata metadata,
        VertexFormat.Mode drawMode) {
        ShaderTranslator.ExternalResult result = ShaderTranslator.translateExternalStages(
            metadata.name(), metadata.vertexFormat(), metadata.vertexSource(),
            metadata.tessellationControlSource(), metadata.tessellationEvaluationSource(),
            metadata.fragmentSource(), metadata.fields());
        if (result.uniformBufferSize() != metadata.uniformBufferSize()) {
            throw new IllegalStateException(
                "External shader uniform layout changed during translation: expected "
                    + metadata.uniformBufferSize() + ", got " + result.uniformBufferSize());
        }

        String key = buildKey(metadata.name(), metadata.vertexFormat(), drawMode,
            result.vertexSource(), result.fragmentSource()
                + '\n' + String.valueOf(result.tessellationControlSource())
                + '\n' + String.valueOf(result.tessellationEvaluationSource()), metadata.fields())
            + (metadata.patchControlPoints() == 0 ? "" : "-patch" + metadata.patchControlPoints())
            + (metadata.customVertexLayout() == null ? ""
                : "-layout" + Integer.toUnsignedString(
                    metadata.customVertexLayout().hashCode(), 16));
        Path directory = getShaderDirectory();
        Path vertexPath = directory.resolve(key + ".vert");
        Path fragmentPath = directory.resolve(key + ".frag");
        Path tessControlPath = metadata.tessellationControlSource() == null ? null
            : directory.resolve(key + ".tesc");
        Path tessEvaluationPath = metadata.tessellationEvaluationSource() == null ? null
            : directory.resolve(key + ".tese");
        writeIfChanged(vertexPath, result.vertexSource());
        writeIfChanged(fragmentPath, result.fragmentSource());
        if (tessControlPath != null) writeIfChanged(tessControlPath,
            result.tessellationControlSource());
        if (tessEvaluationPath != null) writeIfChanged(tessEvaluationPath,
            result.tessellationEvaluationSource());
        int nativeId = ShaderProxy.registerShader(key,
            Constants.VertexFormats.getValue(metadata.vertexFormat()),
            Constants.DrawModes.getValue(drawMode), result.uniformBufferSize(),
            vertexPath.toString(), fragmentPath.toString(),
            tessControlPath == null ? null : tessControlPath.toString(),
            tessEvaluationPath == null ? null : tessEvaluationPath.toString(),
            metadata.patchControlPoints(),
            metadata.customVertexLayout() == null ? null
                : metadata.customVertexLayout().bindingData(),
            metadata.customVertexLayout() == null ? null
                : metadata.customVertexLayout().attributeData(),
            new String[0], new String[0]);
        if (nativeId < 0) {
            throw new IllegalStateException("Native external shader registration returned "
                + nativeId + " for " + metadata.name() + " / " + drawMode);
        }
        return new ShaderDefinition(key, metadata.name(), nativeId,
            result.uniformBufferSize(), metadata.fields());
    }

    private static void warmup(long expectedGeneration) {
        RenderSystem.assertOnRenderThread();
        synchronized (LOCK) {
            if (generation != expectedGeneration) {
                return;
            }
            Map<String, ShaderInstance> liveByName = new HashMap<>();
            for (Map.Entry<ShaderInstance, Long> entry : LIVE_SHADERS.entrySet()) {
                ShaderInstance shader = entry.getKey();
                if (shader != null && entry.getValue() == expectedGeneration
                    && hasCompleteMetadata(shader)) {
                    liveByName.put(((IShaderProgramExt) (Object) shader)
                        .radiance$getShaderName(), shader);
                }
            }

            for (ShaderWarmupHistory.Hint hint : buildWarmupHints(warmupHistory)) {
                if (generation != expectedGeneration) {
                    return;
                }
                ShaderInstance shader = liveByName.get(hint.shaderName());
                Long liveGeneration = shader == null ? null : LIVE_SHADERS.get(shader);
                if (liveGeneration == null || liveGeneration != expectedGeneration
                    || !hasCompleteMetadata(shader)) {
                    continue;
                }
                VertexFormat.Mode drawMode;
                try {
                    drawMode = VertexFormat.Mode.valueOf(hint.drawMode());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                try {
                    getOrCreateForWarmup(shader, drawMode);
                } catch (RuntimeException | LinkageError exception) {
                    RadianceClient.LOGGER.warn(
                        "Skipping dynamic shader warmup for {} / {}",
                        hint.shaderName(), hint.drawMode(), exception);
                }
            }
        }
    }

    static List<ShaderWarmupHistory.Hint> buildWarmupHints(
        ShaderWarmupHistory history) {
        LinkedHashSet<ShaderWarmupHistory.Hint> hints = new LinkedHashSet<>(
            DEFAULT_WARMUP_HINTS);
        hints.addAll(history.newestFirst());
        return List.copyOf(hints);
    }

    private static ShaderDefinition getOrCreateForWarmup(ShaderInstance shaderProgram,
        VertexFormat.Mode drawMode) {
        Map<VertexFormat.Mode, ShaderDefinition> shadersByMode = CACHE.computeIfAbsent(
            shaderProgram, ignored -> new EnumMap<>(VertexFormat.Mode.class));
        ShaderDefinition cached = shadersByMode.get(drawMode);
        if (cached != null) {
            return cached;
        }
        ShaderDefinition created = create(shaderProgram, drawMode);
        shadersByMode.put(drawMode, created);
        return created;
    }

    private static boolean hasCompleteMetadata(ShaderInstance shaderProgram) {
        IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
        return ext.radiance$getShaderName() != null
            && ext.radiance$getVertexFormat() != null
            && ext.radiance$getVertexSource() != null
            && ext.radiance$getFragmentSource() != null
            && ext.radiance$getUniformsValue() != null
            && ext.radiance$getSamplerNamesValue() != null;
    }

    public static void recordSuccessfulUse(ShaderInstance shaderProgram,
        VertexFormat.Mode drawMode) {
        String shaderName = ((IShaderProgramExt) (Object) shaderProgram).radiance$getShaderName();
        if (shaderName != null) {
            synchronized (LOCK) {
                warmupHistory.record(shaderName, drawMode.name());
            }
        }
    }

    private static void loadWarmupHistory() {
        synchronized (LOCK) {
            if (warmupHistoryLoaded) {
                return;
            }
            Path path = warmupHistoryPath();
            if (path == null) {
                return;
            }
            ShaderWarmupHistory loaded = ShaderWarmupHistory.load(path);
            List<ShaderWarmupHistory.Hint> pending = warmupHistory.newestFirst();
            for (int i = pending.size() - 1; i >= 0; i--) {
                ShaderWarmupHistory.Hint hint = pending.get(i);
                loaded.record(hint.shaderName(), hint.drawMode());
            }
            warmupHistory = loaded;
            warmupHistoryLoaded = true;
        }
    }

    private static Path warmupHistoryPath() {
        return RadianceClient.radianceDir == null ? null : RadianceClient.radianceDir
            .resolve("cache")
            .resolve("warmup")
            .resolve("shader-variants-v1.txt");
    }

    private static List<ShaderField> buildFields(List<Uniform> uniforms,
        List<String> samplerNames, String vertexSource, String fragmentSource) {
        ArrayList<ShaderField> fields = new ArrayList<>();
        LinkedHashMap<String, UniformArray> arrays = collectUniformArrays(vertexSource,
            fragmentSource);
        int offset = 0;

        for (Uniform uniform : uniforms) {
            Matcher element = ARRAY_ELEMENT_PATTERN.matcher(uniform.getName());
            if ((element.matches() && arrays.containsKey(element.group(1)))
                || arrays.containsKey(uniform.getName())) {
                continue;
            }
            IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
            int dataType = ext.radiance$getDataTypeValue();
            int componentCount = getComponentCount(dataType);
            ShaderField.Kind kind = getKind(dataType);
            int alignment = getAlignment(kind, componentCount);
            int size = getSize(kind, componentCount);
            offset = align(offset, alignment);
            fields.add(new ShaderField(uniform.getName(), uniform.getName(), kind,
                componentCount, offset, size, -1));
            offset += size;
        }

        Map<String, Uniform> uniformsByName = new HashMap<>();
        for (Uniform uniform : uniforms) {
            uniformsByName.put(uniform.getName(), uniform);
        }
        for (UniformArray array : arrays.values()) {
            for (int i = 0; i < array.length(); i++) {
                String elementName = array.name() + '[' + i + ']';
                Uniform uniform = uniformsByName.get(elementName);
                if (uniform == null) {
                    throw new IllegalStateException(
                        "Missing processed vanilla uniform array element " + elementName);
                }
                IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
                ShaderField.Kind storageKind = getKind(ext.radiance$getDataTypeValue());
                boolean integerStorage = storageKind == ShaderField.Kind.INT
                    && (array.kind() == ShaderField.Kind.UINT
                        || array.kind() == ShaderField.Kind.BOOL);
                if ((!integerStorage && storageKind != array.kind())
                    || getComponentCount(ext.radiance$getDataTypeValue())
                        != array.componentCount()) {
                    throw new IllegalStateException(
                        "Processed vanilla uniform array element type mismatch: " + elementName);
                }
            }
            int elementSize = getSize(array.kind(), array.componentCount());
            int stride = align(elementSize, 16);
            offset = align(offset, 16);
            fields.add(new ShaderField(array.name(), array.name(), array.kind(),
                array.componentCount(), offset, stride * array.length(), -1, array.length(), true));
            offset += stride * array.length();
        }

        List<String> resolvedSamplerNames = resolveSamplerNames(samplerNames, vertexSource,
            fragmentSource);
        for (int i = 0; i < resolvedSamplerNames.size(); i++) {
            String samplerName = resolvedSamplerNames.get(i);
            offset = align(offset, Integer.BYTES);
            fields.add(new ShaderField(samplerName, samplerName + "Index",
                ShaderField.Kind.SAMPLER, 1, offset, Integer.BYTES,
                getSamplerSlot(samplerName, i)));
            offset += Integer.BYTES;
        }

        return List.copyOf(fields);
    }

    static List<ShaderField> buildFieldsForTest(List<Uniform> uniforms,
        List<String> samplerNames, String vertexSource, String fragmentSource) {
        return buildFields(uniforms, samplerNames, vertexSource, fragmentSource);
    }

    private static LinkedHashMap<String, UniformArray> collectUniformArrays(String vertexSource,
        String fragmentSource) {
        LinkedHashMap<String, UniformArray> arrays = new LinkedHashMap<>();
        collectUniformArrays(vertexSource, arrays);
        collectUniformArrays(fragmentSource, arrays);
        return arrays;
    }

    private static void collectUniformArrays(String source,
        Map<String, UniformArray> arrays) {
        Matcher matcher = UNIFORM_ARRAY_PATTERN.matcher(source);
        while (matcher.find()) {
            String typeArray = matcher.group(2);
            String nameArray = matcher.group(4);
            if (typeArray == null && nameArray == null) {
                continue;
            }
            String name = matcher.group(3);
            if (typeArray != null && nameArray != null) {
                throw new IllegalArgumentException(
                    "Vanilla uniform has two array dimensions: " + name);
            }
            UniformType type = uniformType(matcher.group(1), name);
            if (type.kind() == ShaderField.Kind.SAMPLER) {
                throw new IllegalArgumentException(
                    "Vanilla sampler arrays are not supported: " + name);
            }
            int length = parseArrayLength(typeArray != null ? typeArray : nameArray, name);
            UniformArray array = new UniformArray(name, type.kind(), type.componentCount(), length);
            UniformArray previous = arrays.putIfAbsent(name, array);
            if (previous != null && !previous.equals(array)) {
                throw new IllegalArgumentException(
                    "Conflicting vanilla uniform array declarations: " + name);
            }
        }
    }

    private static int parseArrayLength(String expression, String name) {
        String value = expression.substring(1, expression.length() - 1);
        int length = 1;
        for (String factor : value.split("\\*")) {
            try {
                length = Math.multiplyExact(length, Integer.parseInt(factor.trim()));
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(
                    "Unsupported vanilla uniform array length for " + name + ": " + expression,
                    exception);
            }
        }
        if (length < 1) {
            throw new IllegalArgumentException(
                "Vanilla uniform array length must be positive: " + name);
        }
        return length;
    }

    private static UniformType uniformType(String type, String name) {
        return switch (type) {
            case "int" -> new UniformType(ShaderField.Kind.INT, 1);
            case "ivec2" -> new UniformType(ShaderField.Kind.INT, 2);
            case "ivec3" -> new UniformType(ShaderField.Kind.INT, 3);
            case "ivec4" -> new UniformType(ShaderField.Kind.INT, 4);
            case "uint" -> new UniformType(ShaderField.Kind.UINT, 1);
            case "uvec2" -> new UniformType(ShaderField.Kind.UINT, 2);
            case "uvec3" -> new UniformType(ShaderField.Kind.UINT, 3);
            case "uvec4" -> new UniformType(ShaderField.Kind.UINT, 4);
            case "bool" -> new UniformType(ShaderField.Kind.BOOL, 1);
            case "bvec2" -> new UniformType(ShaderField.Kind.BOOL, 2);
            case "bvec3" -> new UniformType(ShaderField.Kind.BOOL, 3);
            case "bvec4" -> new UniformType(ShaderField.Kind.BOOL, 4);
            case "float" -> new UniformType(ShaderField.Kind.FLOAT, 1);
            case "vec2" -> new UniformType(ShaderField.Kind.FLOAT, 2);
            case "vec3" -> new UniformType(ShaderField.Kind.FLOAT, 3);
            case "vec4" -> new UniformType(ShaderField.Kind.FLOAT, 4);
            case "mat2" -> new UniformType(ShaderField.Kind.MATRIX, 2);
            case "mat3" -> new UniformType(ShaderField.Kind.MATRIX, 3);
            case "mat4" -> new UniformType(ShaderField.Kind.MATRIX, 4);
            default -> throw new IllegalArgumentException(
                "Unsupported vanilla uniform array type " + type + " for " + name);
        };
    }

    private static List<String> resolveSamplerNames(List<String> declaredSamplerNames,
        String vertexSource, String fragmentSource) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>(declaredSamplerNames);
        collectSamplerNames(vertexSource, resolved);
        collectSamplerNames(fragmentSource, resolved);
        return List.copyOf(resolved);
    }

    private static void collectSamplerNames(String source, LinkedHashSet<String> names) {
        for (String line : source.split("\\R", -1)) {
            Matcher uniformMatcher = SAMPLER_UNIFORM_PATTERN.matcher(line);
            if (uniformMatcher.matches()) {
                names.add(uniformMatcher.group(1));
            }

            Matcher samplerMatcher = SAMPLER_SLOT_PATTERN.matcher(line);
            while (samplerMatcher.find()) {
                names.add("Sampler" + samplerMatcher.group(1));
            }
        }
    }

    private static int getSamplerSlot(String samplerName, int fallbackSlot) {
        Matcher matcher = SAMPLER_SLOT_PATTERN.matcher(samplerName);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return fallbackSlot;
    }

    private static int getComponentCount(int dataType) {
        return switch (dataType) {
            case 0, 4 -> 1;
            case 1, 5 -> 2;
            case 2, 6 -> 3;
            case 3, 7 -> 4;
            case 8 -> 2;
            case 9 -> 3;
            case 10 -> 4;
            default -> throw new IllegalArgumentException("Unsupported uniform type: " + dataType);
        };
    }

    private static ShaderField.Kind getKind(int dataType) {
        if (dataType <= 3) {
            return ShaderField.Kind.INT;
        }
        if (dataType <= 7) {
            return ShaderField.Kind.FLOAT;
        }
        return ShaderField.Kind.MATRIX;
    }

    private static int getAlignment(ShaderField.Kind kind, int componentCount) {
        return switch (kind) {
            case SAMPLER -> Integer.BYTES;
            case INT, UINT, BOOL, FLOAT -> switch (componentCount) {
                case 1 -> Integer.BYTES;
                case 2 -> Integer.BYTES * 2;
                case 3, 4 -> Integer.BYTES * 4;
                default -> throw new IllegalStateException(
                    "Unsupported component count: " + componentCount);
            };
            case MATRIX -> Integer.BYTES * 4;
        };
    }

    private static int getSize(ShaderField.Kind kind, int componentCount) {
        return switch (kind) {
            case SAMPLER -> Integer.BYTES;
            case INT, UINT, BOOL, FLOAT -> switch (componentCount) {
                case 1 -> Integer.BYTES;
                case 2 -> Integer.BYTES * 2;
                case 3, 4 -> Integer.BYTES * 4;
                default -> throw new IllegalStateException(
                    "Unsupported component count: " + componentCount);
            };
            case MATRIX -> Integer.BYTES * 4 * componentCount;
        };
    }

    private static int align(int value, int alignment) {
        return Math.ceilDiv(value, alignment) * alignment;
    }

    private record UniformType(ShaderField.Kind kind, int componentCount) {
    }

    private record UniformArray(String name, ShaderField.Kind kind, int componentCount,
                                int length) {
    }

    private static String buildKey(String shaderName, VertexFormat vertexFormat,
        VertexFormat.Mode drawMode, String vertexSource, String fragmentSource,
        List<ShaderField> fields) {
        StringBuilder builder = new StringBuilder(shaderName).append('\n')
            .append(vertexFormat)
            .append('\n')
            .append(drawMode)
            .append('\n')
            .append(vertexSource)
            .append('\n')
            .append(fragmentSource)
            .append('\n');
        for (ShaderField field : fields) {
            builder.append(field.name())
                .append(':')
                .append(field.kind())
                .append(':')
                .append(field.componentCount())
                .append(':')
                .append(field.offset())
                .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(builder.toString()
                .getBytes(StandardCharsets.UTF_8));
            return shaderName.replaceAll("[^a-zA-Z0-9._-]", "_")
                + "-"
                + HexFormat.of()
                .formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getShaderDirectory() {
        Path directory = RadianceClient.radianceDir.resolve("temp")
            .resolve("shaders")
            .resolve("overlay");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create shader directory", e);
        }
        return directory;
    }

    private static void writeIfChanged(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(path)) {
                String existing = Files.readString(path);
                if (existing.equals(content)) {
                    return;
                }
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write shader file: " + path, e);
        }
    }
}
