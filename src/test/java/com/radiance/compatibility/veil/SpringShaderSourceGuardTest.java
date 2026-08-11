package com.radiance.compatibility.veil;

import com.radiance.compatibility.simulated.SpringDrawContract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import foundry.veil.api.client.render.shader.program.ProgramDefinition;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import java.io.ByteArrayInputStream;
import io.github.ocelot.glslprocessor.api.GlslParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.lang.reflect.Proxy;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.resources.ResourceLocation;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class SpringShaderSourceGuardTest {
    @Test
    void liveGuardReadsActualVeilResourcePathsAndRejectsSourceOrProcessorVariants()
        throws Exception {
        ResourceLocation program = SpringDrawContract.PROGRAM;
        ResourceLocation fogId = ResourceLocation.fromNamespaceAndPath("veil", "fog");
        ResourceLocation lightId = ResourceLocation.fromNamespaceAndPath("veil", "light");
        byte[] definition = resource("simulated/pinwheel/shaders/program/spring/spring.json");
        byte[] vertexBytes = resource("simulated/pinwheel/shaders/program/spring/spring.vsh");
        byte[] fragmentBytes = resource("simulated/pinwheel/shaders/program/spring/spring.fsh");
        byte[] fogBytes = resource("veil/pinwheel/shaders/include/fog.glsl");
        byte[] lightBytes = resource("veil/pinwheel/shaders/include/light.glsl");
        Map<ResourceLocation, byte[]> files = new java.util.HashMap<>();
        var sourceSet = ShaderManager.PROGRAM_SET;
        files.put(sourceSet.getShaderDefinitionLister().idToFile(program), definition);
        files.put(sourceSet.getTypeConverter(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER)
            .idToFile(program), vertexBytes);
        ResourceLocation fragmentFile = sourceSet
            .getTypeConverter(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER).idToFile(program);
        files.put(fragmentFile, fragmentBytes);
        files.put(ShaderManager.INCLUDE_LISTER.idToFile(fogId), fogBytes);
        files.put(ShaderManager.INCLUDE_LISTER.idToFile(lightId), lightBytes);
        PackResources pack = (PackResources) Proxy.newProxyInstance(
            PackResources.class.getClassLoader(), new Class<?>[] {PackResources.class},
            (proxy, method, args) -> null);
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(
            ResourceManager.class.getClassLoader(), new Class<?>[] {ResourceManager.class},
            (proxy, method, args) -> {
                byte[] bytes = files.get(args[0]);
                if (bytes == null) throw new IOException("Unexpected source lookup " + args[0]);
                Resource value = new Resource(pack, () -> new ByteArrayInputStream(bytes),
                    ResourceMetadata.EMPTY_SUPPLIER);
                return method.getName().equals("getResource") ? Optional.of(value) : value;
            });
        var shaders = new Int2ObjectArrayMap<ResourceLocation>();
        shaders.put(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER, program);
        shaders.put(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER, program);
        ProgramDefinition definitionModel = new ProgramDefinition(program, null, null, null,
            program, null, new String[0], Map.of(), Map.of(), shaders,
            new foundry.veil.api.client.render.shader.ShaderFeature[0], null);
        String trustedVertex = canonical(text(lightBytes) + text(fogBytes)
            + withoutIncludes(vertexBytes));
        String trustedFragment = canonical(text(fogBytes) + withoutIncludes(fragmentBytes));
        String actualSource = System.getenv("RADIANCE_SPRING_CAPTURED_SOURCE_DIR");
        // Optional bounded run evidence: full original Veil-processed strings,
        // not an idealized reconstruction of its compiler output.
        String vertex = actualSource == null ? trustedVertex
            : Files.readString(Path.of(actualSource, "vertex.glsl"));
        String fragment = actualSource == null ? trustedFragment
            : Files.readString(Path.of(actualSource, "fragment.glsl"));
        VeilShaderSource vertexStage = new VeilShaderSource(program, vertex,
            new Object2IntArrayMap<>(), Set.of(), Set.of());
        VeilShaderSource fragmentStage = new VeilShaderSource(program, fragment,
            new Object2IntArrayMap<>(), Set.of(), Set.of());
        assertDoesNotThrow(() -> SpringShaderSourceGuard.verify(manager, sourceSet,
            definitionModel, vertexStage, fragmentStage));
        VeilShaderSource changedMain = new VeilShaderSource(program,
            vertex.replaceFirst("gl_Position\\s*=", "gl_Position = vec4(0.0) +"),
            new Object2IntArrayMap<>(), Set.of(), Set.of());
        assertThrows(ShaderException.class, () -> SpringShaderSourceGuard.verify(manager,
            sourceSet, definitionModel, changedMain, fragmentStage));
        VeilShaderSource unexpectedInclude = new VeilShaderSource(program, vertex,
            new Object2IntArrayMap<>(), Set.of(), Set.of(lightId));
        assertThrows(ShaderException.class, () -> SpringShaderSourceGuard.verify(manager,
            sourceSet, definitionModel, unexpectedInclude, fragmentStage));
        files.put(fragmentFile, new byte[] {1});
        assertThrows(ShaderException.class, () -> SpringShaderSourceGuard.verify(manager,
            sourceSet, definitionModel, vertexStage, fragmentStage));
    }

    @Test
    void actualVeilConvertersResolveThePinnedResourceClosure() {
        var program = SpringDrawContract.PROGRAM;
        assertTrue(ShaderManager.PROGRAM_SET.getShaderDefinitionLister().idToFile(program)
            .toString().endsWith("pinwheel/shaders/program/spring/spring.json"));
        assertTrue(ShaderManager.PROGRAM_SET
            .getTypeConverter(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER).idToFile(program)
            .toString().endsWith("pinwheel/shaders/program/spring/spring.vsh"));
        assertTrue(ShaderManager.PROGRAM_SET
            .getTypeConverter(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER).idToFile(program)
            .toString().endsWith("pinwheel/shaders/program/spring/spring.fsh"));
        assertTrue(ShaderManager.INCLUDE_LISTER.idToFile(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("veil", "light"))
            .toString().endsWith("pinwheel/shaders/include/light.glsl"));
    }

    @Test
    void pinnedResourceClosureAndOriginalPreprocessedFunctionsAreAccepted() throws Exception {
        byte[] definition = resource("simulated/pinwheel/shaders/program/spring/spring.json");
        byte[] vertex = resource("simulated/pinwheel/shaders/program/spring/spring.vsh");
        byte[] fragment = resource("simulated/pinwheel/shaders/program/spring/spring.fsh");
        byte[] fog = resource("veil/pinwheel/shaders/include/fog.glsl");
        byte[] light = resource("veil/pinwheel/shaders/include/light.glsl");
        SpringShaderSourceGuard.requireHash("definition", definition);
        SpringShaderSourceGuard.requireHash("vertex", vertex);
        SpringShaderSourceGuard.requireHash("fragment", fragment);
        SpringShaderSourceGuard.requireHash("fog", fog);
        SpringShaderSourceGuard.requireHash("light", light);
        String trustedVertex = canonical(text(light) + text(fog) + withoutIncludes(vertex));
        String trustedFragment = canonical(text(fog) + withoutIncludes(fragment));
        assertDoesNotThrow(() -> SpringShaderSourceGuard.compareStage("vertex", trustedVertex,
            trustedVertex));
        String veilLightMacros = "#define MINECRAFT_AMBIENT_LIGHT (0.4)\n"
            + "#define MINECRAFT_LIGHT_POWER (0.6)\n";
        assertDoesNotThrow(() -> SpringShaderSourceGuard.compareStage("vertex", trustedVertex,
            veilLightMacros + trustedVertex));
        assertThrows(ShaderException.class, () -> SpringShaderSourceGuard.compareStage(
            "vertex", trustedVertex, veilLightMacros.replace("(0.6)", "(0.7)") + trustedVertex));
        assertDoesNotThrow(() -> SpringShaderSourceGuard.compareStage("fragment", trustedFragment,
            trustedFragment));
        assertTrue(trustedVertex.contains("gl_Position"));
        assertTrue(trustedFragment.contains("discard"));
    }

    @Test
    void sameResourcesAndIncludesDoNotAuthorizeVeilFunctionMutations() throws Exception {
        String vertex = canonical(text(resource("veil/pinwheel/shaders/include/light.glsl"))
            + text(resource("veil/pinwheel/shaders/include/fog.glsl"))
            + withoutIncludes(resource("simulated/pinwheel/shaders/program/spring/spring.vsh")));
        String fragment = canonical(text(resource("veil/pinwheel/shaders/include/fog.glsl"))
            + withoutIncludes(resource("simulated/pinwheel/shaders/program/spring/spring.fsh")));
        // Simulate post-resource Veil modifications: identifiers, includes and original discard
        // declaration remain unchanged, but the executed function body is different.
        String position = vertex.replaceFirst("gl_Position\\s*=", "gl_Position = vec4(0.0) +");
        String uv = vertex.replaceFirst("texCoord0\\s*=", "texCoord0 = vec2(0.0) +");
        String rgb = fragment.replaceFirst("color\\.rgb\\s*=", "color.rgb = vec3(0.0) +");
        String alpha = fragment.replaceFirst("fragColor\\s*=", "fragColor = vec4(0.0) +");
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("vertex", vertex, position));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("vertex", vertex, uv));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment, rgb));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment, alpha));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment,
                fragment.replaceFirst("uniform\\s+sampler2D\\s+Sampler0\\s*;",
                    "layout(binding = 7) uniform sampler2D Sampler0;")));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment,
                fragment.replaceFirst("void\\s+main\\s*\\(",
                    "#define texture(s,u) vec4(0.0)\nvoid main(")));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment,
                "#define SHADER_FEATURE_COMPUTE 2\n" + fragment));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.compareStage("fragment", fragment,
                "#extension GL_ARB_shader_ballot : require\n" + fragment));
        assertThrows(ShaderException.class,
            () -> SpringShaderSourceGuard.requireHash("fragment", new byte[] {1}));
    }

    private static byte[] resource(String path) throws IOException {
        String jarName = path.startsWith("veil/") ? "veil-neoforge-1.21.1-4.3.2.jar"
            : "dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar";
        Path root = Path.of("build/radiance-compatibility-compile");
        Path jarPath;
        try (var paths = Files.walk(root, 3)) {
            jarPath = paths.filter(candidate -> candidate.getFileName().toString().equals(jarName))
                .findFirst().orElseThrow(() -> new IOException("Missing pinned JAR: " + jarName));
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry("assets/" + path);
            if (entry == null) throw new IOException("Missing pinned source asset: " + path);
            try (InputStream stream = jar.getInputStream(entry)) {
                return stream.readAllBytes();
            }
        }
    }

    private static String text(byte[] source) {
        return new String(source, StandardCharsets.UTF_8);
    }

    private static String withoutIncludes(byte[] source) {
        return text(source).replaceAll("(?m)^\\s*#include\\s+[^\\r\\n]+\\r?\\n?", "");
    }

    private static String canonical(String source) throws Exception {
        return GlslParser.preprocessParse(source, new java.util.HashMap<>()).toSourceString();
    }
}
