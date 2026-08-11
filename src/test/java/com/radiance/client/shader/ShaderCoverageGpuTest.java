package com.radiance.client.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderCoverageGpuTest {
    @TempDir Path temporary;

    @Test
    void translatedFragmentKeepsDiscardAndRgbWhileTrackingActualCoverage() throws Exception {
        Path nativeBuild = Path.of(System.getProperty("radiance.nativeTestBuild",
            "../MCVR/build-radiance-1.21.1-neoforge")).toAbsolutePath();
        Path gpu = nativeBuild.resolve("tests/RelWithDebInfo/mcvr_framebuffer_gpu_test.exe");
        Path compiler = Path.of(System.getenv().getOrDefault("VULKAN_SDK",
            "C:/VulkanSDK/1.4.341.1"), "Bin/glslc.exe");
        assumeTrue(Files.isRegularFile(gpu) && Files.isRegularFile(compiler),
            "Paired native GPU harness and Vulkan shader compiler are required");
        String fragment = """
            #version 150
            const float RadianceTargetHeight = 2.0;
            out vec4 fragColor;
            void main() {
                if (gl_FragCoord.x < 2.0) discard;
                vec4 whole = gl_FragCoord;
                bool same = whole.y == gl_FragCoord.y && gl_FragCoord.xy.y == gl_FragCoord.y
                    && whole.w == gl_FragCoord.w && whole.z == gl_FragCoord.z;
                fragColor = same ? vec4(0.2, 0.3, 0.4, 0.54) : vec4(1,0,0,1);
            }
            """;
        for (boolean external : List.of(false, true)) {
            String vertex = "void main(){ gl_Position=vec4(0.0); }";
            var fields = List.of(new ShaderField("unused", "unused", ShaderField.Kind.FLOAT,
                1, 0, 4, -1));
            var translated = external
                ? ShaderTranslator.translateExternal("coverage", DefaultVertexFormat.POSITION, vertex, fragment, fields)
                : ShaderTranslator.translate(DefaultVertexFormat.POSITION, vertex, fragment, fields);
            Path source = temporary.resolve("coverage-" + external + ".frag");
            Path spirv = temporary.resolve("coverage-" + external + ".spv");
            Files.writeString(source, translated.fragmentSource());
            run(List.of(compiler.toString(), "--target-env=vulkan1.2", "-O", source.toString(), "-o", spirv.toString()));
            Path shaders = nativeBuild.resolve("src/shader/shaders/overlay");
            run(List.of(gpu.toString(), shaders.resolve("clear_vert.spv").toString(),
                shaders.resolve("clear_frag.spv").toString(), shaders.resolve("clear_2_frag.spv").toString(),
                "--translated-coverage", spirv.toString()));
        }
    }

    private static void run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assumeTrue(exit != 77, log);
        assertEquals(0, exit, log);
    }
}
