package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.CustomVertexLayout;
import com.radiance.client.shader.ShaderField;
import com.radiance.client.shader.ShaderTranslator;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class VeilShaderBridgeTest {

    private static final Pattern INCLUDE = Pattern.compile(
        "(?m)^\\s*#include\\s+([a-z0-9_.-]+):([a-z0-9_./-]+)\\s*$");

    @Test
    void fixedVeilVersionProvidesEveryInterceptedShaderMethod() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> source = Class.forName(
            "foundry.veil.api.client.render.shader.compiler.VeilShaderSource", false, loader);
        Class<?> compiler = Class.forName(
            "foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler", false, loader);
        assertNotNull(compiler.getDeclaredMethod("compile", int.class, source));

        Class<?> program = Class.forName(
            "foundry.veil.impl.client.render.shader.program.ShaderProgramImpl", false, loader);
        assertNotNull(program.getDeclaredMethod("recompile", int.class,
            Class.forName("foundry.veil.api.client.render.shader.ShaderSourceSet", false, loader),
            Class.forName("foundry.veil.api.client.render.shader.compiler.ShaderCompiler", false,
                loader)));
        assertNotNull(program.getDeclaredMethod("setTexture", CharSequence.class,
            int.class, int.class, int.class));

        Class<?> uniform = Class.forName(
            "foundry.veil.impl.client.render.shader.uniform.ShaderUniformImpl", false, loader);
        assertNotNull(uniform.getDeclaredMethod("upload"));
        assertNotNull(uniform.getDeclaredMethod("uploadMatrix", boolean.class));

        Class<?> blockState = Class.forName(
            "foundry.veil.impl.client.render.pipeline.VeilShaderBlockState", false, loader);
        Class<?> block = Class.forName(
            "foundry.veil.api.client.render.shader.block.ShaderBlock", false, loader);
        assertNotNull(blockState.getDeclaredMethod("bind", CharSequence.class, block));
        assertNotNull(blockState.getDeclaredMethod("unbind", block));
    }

    @Test
    void parsesEndSeaUniformsSamplersAndCameraBlock() throws ShaderException {
        String vertex = """
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 2) in vec2 UV0;
            layout(location = 3) in ivec2 UV2;
            layout(std140) uniform VeilCamera {
                mat4 ViewMatrix;
                vec3 CameraPosition;
            } VeilCamera;
            uniform mat4 ProjMat;
            uniform sampler2D Sampler2;
            void main() {
                gl_Position = ProjMat * vec4(Position + VeilCamera.CameraPosition, 1.0);
            }
            """;
        String fragment = """
            uniform sampler2D ShadowDepthSampler;
            uniform float ShadowVolumeSize;
            out vec4 fragColor;
            void main() { fragColor = texture(ShadowDepthSampler, vec2(0.0)); }
            """;

        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("simulated:end_sea",
            vertex, fragment);

        assertEquals(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            metadata.vertexFormat());
        assertTrue(metadata.vertexSource().contains("radiance_VeilCamera_CameraPosition"));
        assertFalse(metadata.vertexSource().contains("uniform VeilCamera"));
        assertTrue(metadata.fields().stream().anyMatch(field ->
            field.name().equals("radiance_VeilCamera_ViewMatrix")));
        assertTrue(metadata.fields().stream().anyMatch(field ->
            field.name().equals("ShadowDepthSampler") && field.isSampler()));
    }

    @Test
    void preservesStd140ArrayShapeInGeneratedShader() throws ShaderException {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("sable:sprites", """
            layout(location = 0) in vec3 Position;
            layout(std140) uniform SableSprites {
                vec4 sableSprites[2 * 32];
            };
            void main() { gl_Position = vec4(Position + sableSprites[3].xyz, 1.0); }
            """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");

        ShaderField array = metadata.fields().stream()
            .filter(field -> field.fieldName().equals("radiance_SableSprites_sableSprites"))
            .findFirst().orElseThrow();
        ShaderTranslator.Result translated = ShaderTranslator.translateExternal(metadata.name(),
            metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
            metadata.fields());

        assertEquals(64, array.arrayLength());
        assertTrue(translated.vertexSource().contains(
            "vec4 radiance_SableSprites_sableSprites[64];"));
        assertTrue(translated.vertexSource().contains(
            "#define radiance_SableSprites_sableSprites"));
    }

    @Test
    void parsesVeilTypeQualifiedUniformArraysUsedByLevititeLightInclude()
        throws ShaderException {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("aeronautics:levitite", """
            layout(location = 0) in vec3 Position;
            uniform float[6] VeilBlockFaceBrightness;
            void main() {
                gl_Position = vec4(Position * VeilBlockFaceBrightness[1], 1.0);
            }
            """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");
        ShaderField brightness = metadata.fields().stream()
            .filter(field -> field.name().equals("VeilBlockFaceBrightness"))
            .findFirst().orElseThrow();
        ShaderTranslator.Result translated = ShaderTranslator.translateExternal(metadata.name(),
            metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
            metadata.fields());

        assertEquals(6, brightness.arrayLength());
        assertTrue(translated.vertexSource().contains("float VeilBlockFaceBrightness[6];"));
        assertFalse(translated.vertexSource().contains("uniform float[6]"));
    }

    @Test
    void preservesBooleanAndUnsignedUniformSemanticsThroughTranslation() throws Exception {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("veil:logical-types", """
            layout(location = 0) in vec3 Position;
            uniform bool Enabled;
            uniform bvec2 Mask;
            uniform uint Flags;
            uniform uvec4 Lanes;
            void main() {
                bool enabledPath = Enabled && Mask.x && ((Flags & 0x80000000u) != 0u);
                uint lane = Lanes.x | 1u;
                gl_Position = vec4(Position + (enabledPath ? vec3(float(lane)) : vec3(0.0)), 1.0);
            }
            """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");

        assertEquals(ShaderField.Kind.BOOL, field(metadata, "Enabled").kind());
        assertEquals(ShaderField.Kind.BOOL, field(metadata, "Mask").kind());
        assertEquals(ShaderField.Kind.UINT, field(metadata, "Flags").kind());
        assertEquals(ShaderField.Kind.UINT, field(metadata, "Lanes").kind());

        ShaderTranslator.Result translated = ShaderTranslator.translateExternal(metadata.name(),
            metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
            metadata.fields());
        assertTrue(translated.vertexSource().contains("bool Enabled;"));
        assertTrue(translated.vertexSource().contains("bvec2 Mask;"));
        assertTrue(translated.vertexSource().contains("uint Flags;"));
        assertTrue(translated.vertexSource().contains("uvec4 Lanes;"));
        assertTrue(translated.vertexSource().contains("Flags & 0x80000000u"));

        Path output = Path.of("build", "test-results", "veil-logical-types");
        Files.createDirectories(output);
        compileStage(output, "logical-types.vert", "vert", translated.vertexSource());
    }

    private static ShaderField field(ExternalShaderMetadata metadata, String name) {
        return metadata.fields().stream().filter(candidate -> candidate.name().equals(name))
            .findFirst().orElseThrow();
    }

    @Test
    void rewritesVertexIdForFullscreenShaders() throws ShaderException {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("veil:fullscreen", """
            void main() { gl_Position = vec4(float(gl_VertexID)); }
            """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");
        ShaderTranslator.Result translated = ShaderTranslator.translateExternal(metadata.name(),
            metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
            metadata.fields());

        assertEquals(DefaultVertexFormat.POSITION, metadata.vertexFormat());
        assertTrue(translated.vertexSource().contains("gl_VertexIndex"));
    }

    @Test
    void matrixPackingPreservesTheSourceByteOrderAndStd140Stride() {
        ByteBuffer source = ByteBuffer.allocate(4 * Float.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        source.putFloat(0, 1.25F);
        source.putFloat(4, 2.5F);
        source.putFloat(8, 3.75F);
        source.putFloat(12, 5.0F);
        ByteBuffer destination = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        ShaderField matrix = new ShaderField("Matrix", "Matrix", ShaderField.Kind.MATRIX,
            2, 0, 32, -1);

        VeilShaderBridge.copyUniformValueForTest(source, destination, matrix);

        assertEquals(1.25F, destination.getFloat(0));
        assertEquals(2.5F, destination.getFloat(4));
        assertEquals(3.75F, destination.getFloat(16));
        assertEquals(5.0F, destination.getFloat(20));
    }

    @Test
    void boundSamplerDoesNotEvaluateAnInvalidFallbackSlot() {
        AtomicInteger fallbackCalls = new AtomicInteger();

        int result = VeilShaderBridge.resolveSamplerTexture(Map.of("Scene", 42), "Scene",
            99, slot -> {
                fallbackCalls.incrementAndGet();
                throw new AssertionError("fallback must stay lazy");
            });

        assertEquals(42, result);
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void implicitSamplerPrefersTheLegacyTextureUnitUsedByVeil() {
        int result = VeilShaderBridge.resolveImplicitSamplerTexture(0,
            slot -> 203, slot -> 9);

        assertEquals(203, result);
    }

    @Test
    void implicitSamplerFallsBackToMinecraftShaderSlotWhenLegacyUnitIsUnbound() {
        int result = VeilShaderBridge.resolveImplicitSamplerTexture(0,
            slot -> -1, slot -> 9);

        assertEquals(9, result);
    }

    @Test
    void externalClipMappingRunsAfterEarlyReturnAndBlockProjection() throws ShaderException {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("veil:block_projection", """
            layout(location = 0) in vec3 Position;
            layout(std140) uniform VeilCamera {
                mat4 ProjMat;
            } VeilCamera;
            void main() {
                gl_Position = VeilCamera.ProjMat * vec4(Position, 1.0);
                if (Position.x < 0.0) return;
                gl_Position.x += 1.0;
            }
            """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");

        ShaderTranslator.Result translated = ShaderTranslator.translateExternal(metadata.name(),
            metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
            metadata.fields());

        assertTrue(translated.vertexSource().contains("void radianceExternalMain()"));
        assertTrue(translated.vertexSource().contains("if (Position.x < 0.0) return;"));
        assertTrue(translated.vertexSource().contains("radianceExternalMain();"));
        assertTrue(translated.vertexSource().contains("gl_Position.y = -gl_Position.y;"));
        assertTrue(translated.vertexSource().contains(
            "gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;"));
        assertTrue(translated.vertexSource().indexOf("radianceExternalMain();")
            < translated.vertexSource().indexOf("gl_Position.y = -gl_Position.y;"));
    }

    @Test
    void levititeUsesFourStageLocationsAndTransformsOnlyEvaluationOutput()
        throws ShaderException {
        ExternalShaderMetadata metadata = VeilShaderBridge.analyze("aeronautics:levitite",
            """
                layout(location = 0) in vec3 Position;
                out vec2 texCoord0;
                void main() { gl_Position=vec4(Position,1.0); texCoord0=Position.xy; }
                """, """
                layout(vertices = 4) out;
                in vec2 texCoord0[];
                out vec2 texCoord0_out[];
                void main(void) {
                    gl_out[gl_InvocationID].gl_Position=gl_in[gl_InvocationID].gl_Position;
                    texCoord0_out[gl_InvocationID]=texCoord0[gl_InvocationID];
                }
                """, """
                layout(quads) in;
                in vec2 texCoord0_out[];
                out vec2 texCoord0;
                uniform mat4 ProjMat;
                void main(void) {
                    gl_Position=ProjMat*gl_in[0].gl_Position;
                    texCoord0=texCoord0_out[0];
                }
                """, """
                uniform sampler2D DiffuseDepthSampler;
                uniform vec2 ScreenSize;
                in vec2 texCoord0;
                out vec4 fragColor;
                void main() {
                    fragColor=texture(DiffuseDepthSampler, gl_FragCoord.xy/ScreenSize);
                }
                """);
        ShaderTranslator.ExternalResult translated = ShaderTranslator.translateExternalStages(
            metadata.name(), metadata.vertexFormat(), metadata.vertexSource(),
            metadata.tessellationControlSource(), metadata.tessellationEvaluationSource(),
            metadata.fragmentSource(), metadata.fields());

        assertEquals(4, metadata.patchControlPoints());
        assertTrue(metadata.usesFragmentCoordinateHeight());
        assertFalse(translated.vertexSource().contains("gl_Position.y = -gl_Position.y"));
        assertTrue(translated.tessellationEvaluationSource().contains(
            "gl_Position.y = -gl_Position.y"));
        assertTrue(translated.tessellationControlSource().contains(
            "layout(location = 0) in vec2 texCoord0[];"));
        assertTrue(translated.tessellationEvaluationSource().contains(
            "layout(location = 0) in vec2 texCoord0_out[];"));
        assertTrue(translated.fragmentSource().contains(
            "RadianceTargetHeight - gl_FragCoord.y"));
    }

    @Test
    void actualLevititeResourcesTranslateAndCompileToFourVulkanStages() throws Exception {
        Path dependencyDirectory = Files.walk(
                Path.of("build", "radiance-compatibility-compile"), 2)
            .filter(Files::isDirectory)
            .filter(path -> path.getFileName().toString().contains("_1.3.2+mc1.21.1_4.3.2"))
            .findFirst().orElseThrow();
        Path aeronautics = Files.list(dependencyDirectory)
            .filter(path -> path.getFileName().toString().matches(
                ".*aeronautics-neoforge-1\\.21\\.1-1\\.3\\.2\\.jar"))
            .findFirst().orElseThrow();
        Path veil = Files.list(dependencyDirectory)
            .filter(path -> path.getFileName().toString().equals(
                "veil-neoforge-1.21.1-4.3.2.jar"))
            .findFirst().orElseThrow();

        try (ZipFile aeroResources = new ZipFile(aeronautics.toFile());
             ZipFile veilResources = new ZipFile(veil.toFile())) {
            Map<String, ZipFile> resources = Map.of(
                "aeronautics", aeroResources,
                "veil", veilResources);
            String base = "assets/aeronautics/pinwheel/shaders/program/levitite/levitite";
            String vertex = expandIncludes(readEntry(aeroResources, base + ".vsh"),
                resources, new HashSet<>());
            String control = expandIncludes(readEntry(aeroResources, base + ".tcsh"),
                resources, new HashSet<>());
            String evaluation = expandIncludes(readEntry(aeroResources, base + ".tesh"),
                resources, new HashSet<>());
            String fragment = expandIncludes(readEntry(aeroResources, base + ".fsh"),
                resources, new HashSet<>());

            ExternalShaderMetadata metadata = VeilShaderBridge.analyze(
                "aeronautics:levitite/levitite", vertex, control, evaluation, fragment);
            ShaderTranslator.ExternalResult translated =
                ShaderTranslator.translateExternalStages(metadata.name(),
                    metadata.vertexFormat(), metadata.vertexSource(),
                    metadata.tessellationControlSource(),
                    metadata.tessellationEvaluationSource(), metadata.fragmentSource(),
                    metadata.fields());
            Path output = Path.of("build", "test-results", "levitite-processed");
            Files.createDirectories(output);
            compileStage(output, "levitite.vert", "vert", translated.vertexSource());
            compileStage(output, "levitite.tesc", "tesc",
                translated.tessellationControlSource());
            compileStage(output, "levitite.tese", "tese",
                translated.tessellationEvaluationSource());
            compileStage(output, "levitite.frag", "frag", translated.fragmentSource());
        }
    }

    @Test
    void actualSableFancyIncludeSelectsTheTwoBindingCustomLayout() throws Exception {
        Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false,
            getClass().getClassLoader());
        Path archivePath = Path.of(sable.getProtectionDomain().getCodeSource()
            .getLocation().toURI());
        try (ZipFile archive = new ZipFile(archivePath.toFile())) {
            String include = readEntry(archive,
                "assets/sable/pinwheel/shaders/include/fancy_sublevel_vertex.glsl")
                .replace("SABLE_TEXTURE_CACHE_SIZE", "32");
            ExternalShaderMetadata metadata = VeilShaderBridge.analyze(
                "sable:dynamic_sublevel/test", include + """

                    void main() {
                        _sable_unpack();
                        gl_Position = vec4(Position, 1.0);
                    }
                    """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }");
            ShaderTranslator.Result translated = ShaderTranslator.translateExternal(
                metadata.name(), metadata.vertexFormat(), metadata.vertexSource(),
                metadata.fragmentSource(), metadata.fields());

            assertEquals(CustomVertexLayout.SABLE_FANCY, metadata.customVertexLayout());
            assertTrue(translated.vertexSource().contains("gl_VertexIndex"));
            assertTrue(translated.vertexSource().contains(
                "vec4 radiance_SableSprites_sableSprites[64];"));
        }
    }

    private static String expandIncludes(String source, Map<String, ZipFile> resources,
        Set<String> stack) throws Exception {
        Matcher matcher = INCLUDE.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String namespace = matcher.group(1);
            String include = matcher.group(2);
            String key = namespace + ':' + include;
            if (!stack.add(key)) {
                throw new IllegalStateException("Recursive shader include: " + key);
            }
            ZipFile archive = resources.get(namespace);
            if (archive == null) {
                throw new IllegalStateException("Missing shader include namespace: " + namespace);
            }
            String path = "assets/" + namespace + "/pinwheel/shaders/include/"
                + include + ".glsl";
            String replacement = expandIncludes(readEntry(archive, path), resources, stack);
            stack.remove(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String readEntry(ZipFile archive, String path) throws Exception {
        ZipEntry entry = archive.getEntry(path);
        if (entry == null) {
            throw new IllegalStateException("Missing shader resource: " + path);
        }
        try (var input = archive.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void compileStage(Path output, String fileName, String stage, String source)
        throws Exception {
        Path sourceFile = output.resolve(fileName);
        Path spirvFile = output.resolve(fileName + ".spv");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("glslangValidator", "-V", "--target-env",
            "vulkan1.2", "-S", stage, "-o", spirvFile.toString(), sourceFile.toString())
            .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail(fileName + " failed glslang validation:\n" + log);
        }
        assertTrue(Files.size(spirvFile) > 0, fileName + " produced empty SPIR-V");
    }
}
