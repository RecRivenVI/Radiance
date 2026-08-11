package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * End-to-end sampler compatibility verification.
 *
 * <p>Runs the real {@link ShaderTranslator} on shaders that exercise each sampling entry point and
 * then compiles the translated stages with the shaderc toolchain (glslc, the same shaderc used by
 * the native runtime) targeting {@code vulkan1.4} — the exact target environment configured in
 * {@code vk::Shader::compileGlslToSpv}.</p>
 *
 * <p>When no shaderc/glslang CLI is available the compile assertions are skipped, but the
 * stage-aware header structure assertions still run.</p>
 */
class ShaderSamplerCompatTest {

    private static final ShaderField SAMPLER = new ShaderField("Sampler0", "Sampler0Index",
        ShaderField.Kind.SAMPLER, 1, 0, Integer.BYTES, 0);

    private static final List<ShaderField> FIELDS = List.of(SAMPLER);

    private enum Op {
        TEXTURE("texture(Sampler0, uv)"),
        TEXTURE_LOD("textureLod(Sampler0, uv, 0.0)"),
        TEXTURE_GRAD("textureGrad(Sampler0, uv, vec2(0.1), vec2(0.1))"),
        TEXTURE_PROJ3("textureProj(Sampler0, vec3(uv, 1.0))"),
        TEXTURE_PROJ4("textureProj(Sampler0, vec4(uv, 0.0, 1.0))"),
        TEXTURE_PROJ_LOD("textureProjLod(Sampler0, vec3(uv, 1.0), 0.0)"),
        TEXTURE_SIZE("vec4(vec2(textureSize(Sampler0, 0)), 0.0, 1.0)"),
        TEXTURE_QUERY_LEVELS("vec4(float(textureQueryLevels(Sampler0)), 0.0, 0.0, 1.0)"),
        TEXEL_FETCH("texelFetch(Sampler0, ivec2(0), 0)"),
        TEXEL_FETCH_OFFSET("texelFetchOffset(Sampler0, ivec2(0), 0, ivec2(1, 1))"),
        TEXTURE_OFFSET("textureOffset(Sampler0, uv, O)"),
        TEXTURE_LOD_OFFSET("textureLodOffset(Sampler0, uv, 0.0, O)"),
        TEXTURE_GRAD_OFFSET("textureGradOffset(Sampler0, uv, vec2(0.1), vec2(0.1), O)"),
        TEXTURE_PROJ_OFFSET("textureProjOffset(Sampler0, vec3(uv, 1.0), ivec2(1, 1))"),
        TEXTURE_PROJ_OFFSET4("textureProjOffset(Sampler0, vec4(uv, 0.0, 1.0), ivec2(1, 1))"),
        TEXTURE_PROJ_LOD_OFFSET(
            "textureProjLodOffset(Sampler0, vec3(uv, 1.0), 0.0, ivec2(1, 1))"),
        TEXTURE_PROJ_GRAD_OFFSET(
            "textureProjGradOffset(Sampler0, vec3(uv, 1.0), vec2(0.1), vec2(0.1), ivec2(1, 1))"),
        TEXTURE_BIAS("texture(Sampler0, uv, 1.0)"),
        TEXTURE_PROJ_BIAS("textureProj(Sampler0, vec3(uv, 1.0), 1.0)"),
        TEXTURE_QUERY_LOD("vec4(textureQueryLod(Sampler0, uv), 0.0, 0.0)");

        private final String expression;

        Op(String expression) {
            this.expression = expression;
        }

        boolean fragmentOnly() {
            return this == TEXTURE_BIAS || this == TEXTURE_PROJ_BIAS || this == TEXTURE_QUERY_LOD;
        }
    }

    @Test
    void translatedSamplingEntryPointsCompileForEveryStage() throws IOException {
        Path tool = findCompiler();
        Assumptions.assumeTrue(tool != null,
            "No shaderc/glslang CLI found; skipping translated-shader compilation");

        Path dir = Files.createTempDirectory("radiance-sampler-compat");
        StringBuilder failures = new StringBuilder();
        int compiled = 0;

        for (Op op : Op.values()) {
            if (!op.fragmentOnly()) {
                String source = translateVertex(op.expression);
                if (!compile(tool, dir, "vert_" + op.name().toLowerCase(Locale.ROOT), "vert", source,
                    failures)) {
                    continue;
                }
                compiled++;
            }
            String source = translateFragment(op.expression);
            if (compile(tool, dir, "frag_" + op.name().toLowerCase(Locale.ROOT), "frag", source,
                failures)) {
                compiled++;
            }
        }

        assertTrue(failures.length() == 0, "Translated shaders failed to compile:\n" + failures);
        assertTrue(compiled >= Op.values().length, "Expected every operation to compile at least once");
    }

    @Test
    void fragmentOnlyEntryPointsDoNotLeakIntoOtherStages() {
        ShaderTranslator.Result result = ShaderTranslator.translate("sampler-stage",
            DefaultVertexFormat.POSITION, translateVertexSource("texture(Sampler0, uv)"),
            translateFragmentSource("texture(Sampler0, uv, 1.0)"), FIELDS);

        assertTrue(result.fragmentSource().contains(
            "vec4 texture(RadianceSampler2D s, vec2 uv, float bias)"),
            "Fragment header must provide the bias overload");
        assertTrue(result.fragmentSource().contains(
            "vec2 textureQueryLod(RadianceSampler2D s, vec2 uv)"),
            "Fragment header must provide textureQueryLod");
        assertFalse(result.vertexSource().contains("vec2 textureQueryLod("),
            "Vertex header must not declare fragment-only textureQueryLod");
        assertFalse(result.vertexSource().contains("float bias)"),
            "Vertex header must not declare fragment-only bias overloads");
    }

    @Test
    void gatherIsNotSilentlyAliased() {
        // textureGather needs neighborhood/component permutation and a constant component argument;
        // it is intentionally not wrapped this round. Verify the header does not pretend to support
        // it (which would otherwise silently misinterpret the result on flipped textures).
        ShaderTranslator.Result result = ShaderTranslator.translate("sampler-gather",
            DefaultVertexFormat.POSITION, translateVertexSource("texture(Sampler0, uv)"),
            translateFragmentSource("texture(Sampler0, uv)"), FIELDS);
        assertFalse(result.fragmentSource().contains("textureGather"),
            "textureGather must remain an explicit, diagnosed limitation");
    }

    private static String translateVertex(String expression) {
        return ShaderTranslator.translate("sampler-vertex", DefaultVertexFormat.POSITION,
            translateVertexSource(expression), translateFragmentSource("texture(Sampler0, uv)"),
            FIELDS).vertexSource();
    }

    private static String translateFragment(String expression) {
        return ShaderTranslator.translate("sampler-fragment", DefaultVertexFormat.POSITION,
            translateVertexSource("texture(Sampler0, uv)"), translateFragmentSource(expression),
            FIELDS).fragmentSource();
    }

    private static String translateVertexSource(String expression) {
        return """
            #version 150
            in vec3 Position;
            out vec2 uv;
            const ivec2 O = ivec2(1, 1);
            void main() {
                uv = Position.xy;
                gl_Position = %s;
            }
            """.formatted(expression);
    }

    private static String translateFragmentSource(String expression) {
        return """
            #version 150
            uniform sampler2D Sampler0;
            in vec2 uv;
            out vec4 fragColor;
            const ivec2 O = ivec2(1, 1);
            void main() {
                fragColor = %s;
            }
            """.formatted(expression);
    }

    private static boolean compile(Path tool, Path dir, String name, String stage, String source,
        StringBuilder failures) {
        try {
            Path file = dir.resolve(name + "." + stage);
            Files.writeString(file, source, StandardCharsets.UTF_8);
            Path output = dir.resolve(name + "." + stage + ".spv");
            List<String> command = new ArrayList<>();
            command.add(tool.toString());
            if (tool.getFileName().toString().contains("glslc")) {
                command.add("--target-env=vulkan1.4");
                command.add("-O");
                command.add(file.toString());
                command.add("-o");
                command.add(output.toString());
            } else {
                command.add("-V");
                command.add("--target-env");
                command.add("vulkan1.4");
                command.add("-S");
                command.add(stage);
                command.add(file.toString());
                command.add("-o");
                command.add(output.toString());
            }
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                failures.append("[").append(name).append("] exit=").append(exit).append('\n')
                    .append(log).append('\n');
                return false;
            }
            return true;
        } catch (IOException | InterruptedException exception) {
            failures.append("[").append(name).append("] ").append(exception).append('\n');
            return false;
        }
    }

    private static Path findCompiler() {
        List<Path> candidates = new ArrayList<>();
        String sdk = System.getenv("VULKAN_SDK");
        if (sdk != null && !sdk.isBlank()) {
            candidates.add(Path.of(sdk, "Bin", "glslc.exe"));
            candidates.add(Path.of(sdk, "bin", "glslc"));
            candidates.add(Path.of(sdk, "Bin", "glslangValidator.exe"));
            candidates.add(Path.of(sdk, "bin", "glslangValidator"));
        }
        candidates.add(Path.of("C:\\VulkanSDK\\1.4.341.1\\Bin\\glslc.exe"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        for (String onPath : List.of("glslc", "glslangValidator")) {
            try {
                Process process = new ProcessBuilder("where", onPath).redirectErrorStream(true)
                    .start();
                String out = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
                if (process.waitFor() == 0 && !out.isEmpty()) {
                    String first = out.lines().findFirst().orElse("").trim();
                    if (!first.isEmpty()) {
                        return new File(first).toPath();
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }
        return null;
    }
}
