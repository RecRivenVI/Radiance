package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderTranslatorTest {

    @Test
    void assignsMatchingLocationsToFlatVaryings() {
        String vertex = """
            #version 150
            in vec3 Position;
            flat out vec4 vertexColor;
            void main() {
                vertexColor = vec4(1.0);
                gl_Position = vec4(Position, 1.0);
            }
            """;
        String fragment = """
            #version 150
            flat in vec4 vertexColor;
            out vec4 fragColor;
            void main() {
                fragColor = vertexColor;
            }
            """;

        ShaderTranslator.Result result = ShaderTranslator.translate(
            DefaultVertexFormat.POSITION, vertex, fragment, List.of());

        assertTrue(result.vertexSource().contains("layout(location = 0) in vec3 Position;"));
        assertTrue(result.vertexSource().contains("layout(location = 0) flat out vec4 vertexColor;"));
        assertTrue(result.fragmentSource().contains("layout(location = 0) flat in vec4 vertexColor;"));
        assertTrue(result.fragmentSource().contains("layout(location = 0) out vec4 fragColor;"));
    }

    @Test
    void emitsStd140ArrayFields() {
        ShaderField array = new ShaderField("Values", "Values", ShaderField.Kind.FLOAT,
            4, 0, 64, -1, 4);

        ShaderTranslator.Result result = ShaderTranslator.translate(
            DefaultVertexFormat.POSITION, """
                layout(location = 0) in vec3 Position;
                void main() { gl_Position = vec4(Position + Values[0].xyz, 1.0); }
                """, "out vec4 fragColor; void main(){ fragColor=vec4(1.0); }",
            List.of(array));

        assertTrue(result.vertexSource().contains("vec4 Values[4];"));
    }

    @Test
    void samplerWrapperPreservesFramebufferOrientationThroughFunctionParameters() {
        ShaderField sampler = new ShaderField("Scene", "SceneIndex",
            ShaderField.Kind.SAMPLER, 1, 0, 4, 0);
        ShaderTranslator.Result result = ShaderTranslator.translate(
            DefaultVertexFormat.POSITION, """
                layout(location = 0) in vec3 Position;
                void main() { gl_Position = vec4(Position, 1.0); }
                """, """
                uniform sampler2D Scene;
                vec4 sampleScene(sampler2D source, vec2 uv) { return texture(source, uv); }
                out vec4 fragColor;
                void main() { fragColor = sampleScene(Scene, vec2(0.25)); }
                """, List.of(sampler));

        assertTrue(result.fragmentSource().contains(
            "struct RadianceSampler2D { uint index; bool flipY; };"));
        assertTrue(result.fragmentSource().contains(
            "vec4 sampleScene(RadianceSampler2D source, vec2 uv)"));
        assertTrue(result.fragmentSource().contains(
            "RadianceSampler2D(uint(uniforms.SceneIndex) & 0x7FFFFFFFu"));
        assertTrue(result.fragmentSource().contains("1.0 - uv.y"));
    }
}
