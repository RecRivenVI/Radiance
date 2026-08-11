package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderRegistryUniformArrayTest {

    private static final String VERTEX = """
        uniform float[6] VeilBlockFaceBrightness;
        in vec3 Position;
        void main() {
            gl_Position = vec4(Position * VeilBlockFaceBrightness[2], 1.0);
        }
        """;

    @Test
    void typeQualifiedFloatArrayProducesOneStd140FieldAndValidGlslDeclaration() throws Exception {
        List<Uniform> uniforms = brightnessUniforms(6);
        try {
            List<ShaderField> fields = ShaderRegistry.buildFieldsForTest(uniforms, List.of(),
                VERTEX, "out vec4 fragColor; void main(){fragColor=vec4(1.0);}");
            ShaderField brightness = fields.getFirst();
            ShaderTranslator.Result translated = ShaderTranslator.translate("sable:dynamic",
                DefaultVertexFormat.POSITION, VERTEX,
                "out vec4 fragColor; void main(){fragColor=vec4(1.0);}", fields);

            assertEquals(1, fields.size());
            assertEquals("VeilBlockFaceBrightness", brightness.name());
            assertEquals(6, brightness.arrayLength());
            assertEquals(96, brightness.size());
            assertTrue(translated.vertexSource().contains("float VeilBlockFaceBrightness[6];"));
            assertFalse(translated.vertexSource().contains("uniform float[6]"));
            Path output = Path.of("build", "test-results", "veil-vanilla-array");
            Files.createDirectories(output);
            Path sourceFile = output.resolve("brightness.vert");
            Path spirvFile = output.resolve("brightness.vert.spv");
            Files.writeString(sourceFile, translated.vertexSource(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("glslangValidator", "-V", "--target-env",
                "vulkan1.2", "-S", "vert", "-o", spirvFile.toString(), sourceFile.toString())
                .redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                fail("Processed vanilla brightness array failed offline Vulkan compilation:\n"
                    + log);
            }
            assertTrue(Files.size(spirvFile) > 0);
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    @Test
    void oneElementArrayRetainsItsGlslArrayShape() {
        List<Uniform> uniforms = brightnessUniforms(1);
        String vertex = VERTEX.replace("float[6]", "float[1]").replace("Brightness[2]",
            "Brightness[0]");
        try {
            List<ShaderField> fields = ShaderRegistry.buildFieldsForTest(uniforms, List.of(),
                vertex, "");
            ShaderField array = fields.getFirst();
            ShaderTranslator.Result translated = ShaderTranslator.translate("sable:single",
                DefaultVertexFormat.POSITION, vertex,
                "out vec4 fragColor; void main(){fragColor=vec4(1.0);}", fields);
            assertTrue(array.isArray());
            assertEquals(1, array.arrayLength());
            assertEquals(16, array.size());
            assertTrue(translated.vertexSource().contains("float VeilBlockFaceBrightness[1];"));
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    @Test
    void aMissingRequiredElementFailsBeforeNativeRegistration() {
        List<Uniform> uniforms = brightnessUniforms(5);
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ShaderRegistry.buildFieldsForTest(uniforms, List.of(), VERTEX, ""));
            assertTrue(error.getMessage().contains("VeilBlockFaceBrightness[5]"));
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    private static List<Uniform> brightnessUniforms(int length) {
        List<Uniform> uniforms = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            uniforms.add(new CpuUniform("VeilBlockFaceBrightness[" + i + ']'));
        }
        return uniforms;
    }

    private static final class CpuUniform extends Uniform implements IGlUniformExt {
        CpuUniform(String name) {
            super(name, Uniform.UT_FLOAT1, 1, null);
        }

        public int radiance$getDataTypeValue() { return this.getType(); }
        public int radiance$getCountValue() { return this.getCount(); }
        public IntBuffer radiance$getIntDataValue() { return this.getIntBuffer(); }
        public FloatBuffer radiance$getFloatDataValue() { return this.getFloatBuffer(); }
    }
}
