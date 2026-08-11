package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the compiler's UBO offsets, not just the emitted GLSL spelling. */
class ShaderUniformLayoutReflectionTest {
    private static final String VERTEX = """
        layout(location = 0) in vec3 Position;
        void main() { gl_Position = vec4(Position, 1.0); }
        """;
    private static final String FRAGMENT = """
        layout(location = 0) out vec4 fragColor;
        void main() { fragColor = vec4(1.0); }
        """;

    @Test
    void explicitCpuLayoutSurvivesVec3ScalarTailPackingInBothTranslationEntrypoints()
        throws Exception {
        List<ShaderField> fields = List.of(
            new ShaderField("Light0", "Light0", ShaderField.Kind.FLOAT, 3, 0, 16, -1),
            new ShaderField("Scale", "Scale", ShaderField.Kind.FLOAT, 1, 16, 4, -1),
            new ShaderField("Mode", "Mode", ShaderField.Kind.INT, 1, 20, 4, -1),
            new ShaderField("Light1", "Light1", ShaderField.Kind.FLOAT, 3, 32, 16, -1),
            new ShaderField("Sampler0", "Sampler0Index", ShaderField.Kind.SAMPLER, 1, 48, 4, 0),
            new ShaderField("Tint", "Tint", ShaderField.Kind.FLOAT, 4, 64, 16, -1));
        ShaderTranslator.Result vanilla = ShaderTranslator.translate(
            DefaultVertexFormat.POSITION, VERTEX, FRAGMENT, fields);
        ShaderTranslator.Result external = ShaderTranslator.translateExternal(
            "simulated:synthetic", DefaultVertexFormat.POSITION, VERTEX, FRAGMENT, fields);
        for (ShaderTranslator.Result candidate : List.of(vanilla, external)) {
            assertTrue(candidate.vertexSource().contains("layout(offset = 48) uint Sampler0Index;"));
            assertTrue(candidate.fragmentSource().contains("layout(offset = 64) vec4 Tint;"));
            assertOffsets(candidate.vertexSource(), "vert", List.of(0, 16, 20, 32, 48, 64));
            assertOffsets(candidate.fragmentSource(), "frag", List.of(0, 16, 20, 32, 48, 64));
        }
    }

    @Test
    void explicitMatrixAndArrayOffsetsPreserveStd140Stride() throws Exception {
        List<ShaderField> fields = List.of(
            new ShaderField("Pose", "Pose", ShaderField.Kind.MATRIX, 3, 0, 48, -1),
            new ShaderField("Weights", "Weights", ShaderField.Kind.FLOAT, 1,
                48, 32, -1, 2),
            new ShaderField("Light", "Light", ShaderField.Kind.FLOAT, 3, 80, 16, -1),
            new ShaderField("Mode", "Mode", ShaderField.Kind.UINT, 1, 96, 4, -1));
        var translated = ShaderTranslator.translateExternal("matrix-array",
            DefaultVertexFormat.POSITION, VERTEX, FRAGMENT, fields);
        assertOffsets(translated.vertexSource(), "vert", List.of(0, 48, 80, 96), true);
        assertOffsets(translated.fragmentSource(), "frag", List.of(0, 48, 80, 96), true);
    }

    @Test
    void invalidCpuFieldLayoutFailsBeforeCompilation() {
        List<ShaderField> overlap = List.of(
            new ShaderField("Normal", "Normal", ShaderField.Kind.FLOAT, 3, 0, 16, -1),
            new ShaderField("Sampler0", "Sampler0Index", ShaderField.Kind.SAMPLER,
                1, 12, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> ShaderTranslator.translateExternal(
            "bad-overlap", DefaultVertexFormat.POSITION, VERTEX, FRAGMENT, overlap));
        List<ShaderField> unaligned = List.of(
            new ShaderField("Tint", "Tint", ShaderField.Kind.FLOAT, 4, 4, 16, -1));
        assertThrows(IllegalArgumentException.class, () -> ShaderTranslator.translate(
            DefaultVertexFormat.POSITION, VERTEX, FRAGMENT, unaligned));
    }

    private static void assertOffsets(String source, String stage, List<Integer> expected)
        throws IOException, InterruptedException {
        assertOffsets(source, stage, expected, false);
    }

    private static void assertOffsets(String source, String stage, List<Integer> expected,
        boolean requireMatrixAndArrayStride)
        throws IOException, InterruptedException {
        Path directory = Path.of("build", "test-results", "std140-reflection");
        Files.createDirectories(directory);
        String key = Integer.toUnsignedString(source.hashCode(), 16) + '-' + stage;
        Path glsl = directory.resolve(key + '.' + stage);
        Path spirv = directory.resolve(key + ".spv");
        Files.writeString(glsl, source, StandardCharsets.UTF_8);
        Process compiler = new ProcessBuilder("glslangValidator", "-V", "--target-env",
            "vulkan1.4", "-S", stage, "-o", spirv.toString(), glsl.toString())
            .redirectErrorStream(true).start();
        String log = new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, compiler.waitFor(), log);
        ByteBuffer words = ByteBuffer.wrap(Files.readAllBytes(spirv)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x07230203, words.getInt());
        words.position(20); // five-word SPIR-V header
        Map<Integer, Map<Integer, Integer>> offsets = new HashMap<>();
        boolean matrixStride16 = false, arrayStride16 = false;
        while (words.hasRemaining()) {
            int start = words.position();
            int instruction = words.getInt();
            int length = instruction >>> 16;
            int opcode = instruction & 0xffff;
            assertTrue(length > 0 && start + length * 4 <= words.limit());
            if (opcode == 72 && length >= 5) { // OpMemberDecorate
                int typeId = words.getInt();
                int member = words.getInt();
                int decoration = words.getInt();
                if (decoration == 35) { // Offset
                    offsets.computeIfAbsent(typeId, ignored -> new HashMap<>())
                        .put(member, words.getInt());
                } else if (decoration == 7) { // MatrixStride
                    matrixStride16 |= words.getInt() == 16;
                }
            } else if (opcode == 71 && length >= 4) { // OpDecorate
                words.getInt(); // target type
                arrayStride16 |= words.getInt() == 6 && words.getInt() == 16;
            }
            words.position(start + length * 4);
        }
        assertTrue(offsets.values().stream().anyMatch(found ->
            found.size() == expected.size()
                && expected.equals(java.util.stream.IntStream.range(0, expected.size())
                    .mapToObj(found::get).toList())), "No Uniforms struct has CPU offsets " + expected
                        + "; reflected=" + offsets);
        if (requireMatrixAndArrayStride) {
            assertTrue(matrixStride16, "mat3 did not retain 16-byte column stride");
            assertTrue(arrayStride16, "std140 scalar array did not retain 16-byte stride");
        }
    }
}
