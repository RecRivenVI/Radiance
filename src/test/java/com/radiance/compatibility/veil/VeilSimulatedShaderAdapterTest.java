package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.ShaderTranslator;
import com.radiance.compatibility.veil.VeilShaderBridge;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class VeilSimulatedShaderAdapterTest {

    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include\\s+(\\S+)\\s*$");
    private static final Path FIXED_JARS = Path.of("build", "radiance-compatibility-compile",
        "6.0.10+mc1.21.1_2.0.5+mc1.21.1_1.3.2+mc1.21.1_4.3.2");
    private static final Map<String, String> JARS = Map.of(
        "simulated", "dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar",
        "aeronautics", "dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.2.jar",
        "veil", "veil-neoforge-1.21.1-4.3.2.jar");
    private static final Map<String, VertexFormat> FORMATS = Map.of(
        "simulated:laser/laser", DefaultVertexFormat.POSITION_TEX_COLOR,
        "simulated:laser_pointer/lens", DefaultVertexFormat.BLOCK,
        "simulated:redstone_accumulator/diode", DefaultVertexFormat.BLOCK,
        "simulated:rope/rope", DefaultVertexFormat.BLOCK,
        "simulated:spring/spring", DefaultVertexFormat.BLOCK,
        "aeronautics:burner_flame", DefaultVertexFormat.POSITION_TEX);

    @Test
    void actualDiagramPostPreservesResourcesAndCompilesWithLogicalPaperCoordinates() throws Exception {
        String name = VeilSimulatedShaderAdapter.DIAGRAM_POST;
        String fragment = expandedProgram(name, "fsh");
        var metadata = VeilShaderBridge.analyze(name, expandedProgram(name, "vsh"), fragment);
        assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("RadianceDiagramLogicalSize")));
        assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("Palette")));
        assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("Dither")));
        var translated = ShaderTranslator.translateExternal(name, metadata.vertexFormat(),
            metadata.vertexSource(), metadata.fragmentSource(), metadata.fields());
        compile(name, "vert", translated.vertexSource());
        compile(name, "frag", translated.fragmentSource());
        assertEquals(fragment, VeilSimulatedShaderAdapter.adaptDiagramFragment("other:post", fragment));
    }

    @Test
    void actualPackagedProgramsKeepConsumerStrideAndCompileToVulkan() throws Exception {
        for (var consumer : FORMATS.entrySet()) {
            String name = consumer.getKey();
            String vertex = expandedProgram(name, "vsh");
            String fragment = expandedProgram(name, "fsh");
            ExternalShaderMetadata metadata = VeilShaderBridge.analyze(name, vertex, fragment);
            assertSame(consumer.getValue(), metadata.vertexFormat(), name);
            ShaderTranslator.Result translated = ShaderTranslator.translateExternal(name,
                metadata.vertexFormat(), metadata.vertexSource(), metadata.fragmentSource(),
                metadata.fields());
            compile(name, "vert", translated.vertexSource());
            compile(name, "frag", translated.fragmentSource());
            if (name.equals("simulated:spring/spring")) {
                assertTrue(translated.vertexSource().contains("overlayColor = Color;"));
                assertTrue(translated.fragmentSource().contains("overlayColor.a"));
                assertTrue(translated.fragmentSource().contains("color.a < 0.1"));
            }
            if (name.equals("aeronautics:burner_flame")) {
                assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("FlameRenderTime")));
                assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("Intensity")));
                assertTrue(metadata.fields().stream().anyMatch(f -> f.name().equals("Palette")));
            }
        }
    }

    @Test
    void changedKnownSignatureFailsAndOtherNamespaceIsNotOverridden() throws Exception {
        String lens = expandedProgram("simulated:laser_pointer/lens", "vsh");
        assertThrows(ShaderException.class, () -> VeilSimulatedShaderAdapter.vertexFormat(
            "simulated:laser_pointer/lens", lens.replace("in ivec2 UV2", "in vec2 UV2")));
        assertNull(VeilSimulatedShaderAdapter.vertexFormat("other:laser_pointer/lens", lens));
        assertEquals(32, VeilSimulatedShaderAdapter.vertexFormat(
            "simulated:laser_pointer/lens", lens).getVertexSize());
    }

    @Test
    void selectionReturnsTheOriginalProgramAndMakesMissingDiagramProgramAnError() {
        ShaderProgram original = program(Map.of());
        ResourceLocation flame = ResourceLocation.parse("aeronautics:burner_flame");
        assertSame(original, VeilSimulatedShaderAdapter.requireDiagramProgram(flame, original));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> VeilSimulatedShaderAdapter.requireDiagramProgram(flame, null));
        assertTrue(error.getMessage().contains(flame.toString()));
        assertNull(VeilSimulatedShaderAdapter.requireDiagramProgram(
            ResourceLocation.parse("veil:core/blit_screen_effect"), null));
    }

    @Test
    void forwardsActualNormalLightingAndSixShadeValuesWithoutDefaultSubstitution() {
        Map<String, Object> values = new HashMap<>();
        Map<String, ShaderUniform> uniforms = new HashMap<>();
        for (String name : Set.of("NormalMat", "Light0_Direction", "Light1_Direction",
            "VeilBlockFaceBrightness")) {
            uniforms.put(name, (ShaderUniform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ShaderUniform.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("setMatrix")) {
                        values.put(name, new Matrix3f((Matrix3fc) args[0]));
                    } else if (method.getName().equals("setVector")) {
                        values.put(name, new float[]{(float) args[0], (float) args[1], (float) args[2]});
                    } else if (method.getName().equals("setFloats")) {
                        values.put(name, ((float[]) args[0]).clone());
                    }
                    return null;
                }));
        }
        Matrix4f modelView = new Matrix4f().rotateY(0.8F).scale(2.0F, 3.0F, 4.0F);
        float[] shades = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
        VeilSimulatedShaderAdapter.writeLightingDefaults(program(uniforms), modelView,
            new Vector3f(0.2F, 0.7F, -0.3F), new Vector3f(-0.4F, 0.1F, 0.9F), shades);
        assertEquals(modelView.normal(new Matrix3f()), values.get("NormalMat"));
        assertArrayEquals(new float[]{0.2F, 0.7F, -0.3F}, (float[]) values.get("Light0_Direction"));
        assertArrayEquals(new float[]{-0.4F, 0.1F, 0.9F}, (float[]) values.get("Light1_Direction"));
        assertArrayEquals(shades, (float[]) values.get("VeilBlockFaceBrightness"));
    }

    private static ShaderProgram program(Map<String, ShaderUniform> uniforms) {
        return (ShaderProgram) Proxy.newProxyInstance(ShaderProgram.class.getClassLoader(),
            new Class<?>[]{ShaderProgram.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUniform")) return uniforms.get(args[0].toString());
                if (method.getName().equals("getName")) return ResourceLocation.parse("simulated:spring/spring");
                return null;
            });
    }

    private static String expandedProgram(String name, String stage) throws Exception {
        String[] parts = name.split(":", 2);
        return expandResource("assets/" + parts[0] + "/pinwheel/shaders/program/"
            + parts[1] + '.' + stage, new HashSet<>());
    }

    // Offline fixture expands the actual packaged includes. Production uses Veil's original
    // compiler/preprocessors; this does not stand in for a runtime resource reload.
    private static String expandResource(String path, Set<String> stack) throws Exception {
        if (!stack.add(path)) throw new IllegalStateException("Recursive shader include " + path);
        String namespace = path.split("/", 3)[1];
        try (ZipFile jar = new ZipFile(FIXED_JARS.resolve(JARS.get(namespace)).toFile())) {
            var entry = jar.getEntry(path);
            if (entry == null) throw new IllegalStateException("Missing fixed packaged shader " + path);
            String source;
            try (var input = jar.getInputStream(entry)) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = INCLUDE.matcher(source);
            StringBuffer expanded = new StringBuffer();
            while (matcher.find()) {
                String[] include = matcher.group(1).split(":", 2);
                String body = expandResource("assets/" + include[0] + "/pinwheel/shaders/include/"
                    + include[1] + ".glsl", stack);
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(body));
            }
            matcher.appendTail(expanded);
            return expanded.toString();
        } finally {
            stack.remove(path);
        }
    }

    private static void compile(String name, String stage, String source) throws Exception {
        Path output = Path.of("build", "test-results", "simulated-shader-consumers");
        Files.createDirectories(output);
        Path sourceFile = output.resolve(name.replace(':', '_').replace('/', '_') + '.' + stage);
        Path spirvFile = output.resolve(sourceFile.getFileName() + ".spv");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("glslangValidator", "-V", "--target-env", "vulkan1.2",
            "-S", stage, "-o", spirvFile.toString(), sourceFile.toString())
            .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) fail(name + ' ' + stage + " failed actual offline compile:\n" + log);
        assertTrue(Files.size(spirvFile) > 0);
    }
}
