package com.radiance.compatibility.veil;

import com.radiance.compatibility.simulated.SpringDrawContract;

import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.ShaderSourceSet;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import foundry.veil.api.client.render.shader.program.ProgramDefinition;
import io.github.ocelot.glslprocessor.api.GlslParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Only the pinned producer shader can be lowered using the spring's known material contract.
 * Body PNGs remain live resource-pack inputs; shader semantics do not silently inherit it.
 */
public final class SpringShaderSourceGuard {
    private static final ResourceLocation FOG = ResourceLocation.fromNamespaceAndPath("veil", "fog");
    private static final ResourceLocation LIGHT = ResourceLocation.fromNamespaceAndPath("veil", "light");
    private static final Map<String, String> SOURCE_HASHES = Map.of(
        "definition", "B218A7091E93FCCFD4727A95E69D09CF92543804EA35189A0948820DDF2F8B4F",
        "vertex", "6025AE0E0DC9F9BC48E4DF53D1766CBB840F4CDF03989264E7A411A702DB66C2",
        "fragment", "27C73E05F707DC25D79442E89DED3095201235E8E7AC5946254D90C3800A73CD",
        "fog", "C01F8D07915BAD3A3FD0C71714C3E239C232FC242734EF8DC06B073C038FAD96",
        "light", "5ED452DA9EC80007E934C91D77704653C2061FBD539337B77C7C1BBC5F889B23");
    private static final Pattern FUNCTION = Pattern.compile(
        "\\b(?:void|float|int|bool|vec[234]|ivec[234]|mat[234])\\s+\\w+\\s*\\([^;{}]*\\)\\s*\\{");
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^\\s*#\\s*(\\w+)([^\\r\\n]*)$");
    private static final Set<String> VEIL_VERTEX_LIGHT_MACROS = Set.of(
        "#define MINECRAFT_AMBIENT_LIGHT (0.4)",
        "#define MINECRAFT_LIGHT_POWER (0.6)");
    private static final Set<String> VEIL_CAPABILITY_MACROS = Set.of(
        "#define SHADER_FEATURE_COMPUTE 1",
        "#define SHADER_FEATURE_TESSELLATION 1",
        "#define SHADER_FEATURE_VERTEX_ATTRIBUTE64 1",
        "#define SHADER_FEATURE_BINDLESS_TEXTURE 1",
        "#define SHADER_FEATURE_FLOAT64 1",
        "#define SHADER_FEATURE_CUBE_MAP_ARRAY 1",
        "#define SHADER_FEATURE_SHADER_STORAGE 1",
        "#define SHADER_FEATURE_INT64 1",
        "#define SHADER_FEATURE_ATOMIC_COUNTER 1");

    private SpringShaderSourceGuard() {}

    public static void verify(ResourceManager resources, ShaderSourceSet sourceSet,
        ProgramDefinition definition, VeilShaderSource vertex, VeilShaderSource fragment)
        throws IOException, ShaderException {
        if (definition.shaders().size() != 2 || !SpringDrawContract.PROGRAM.equals(vertex.sourceId())
            || !SpringDrawContract.PROGRAM.equals(fragment.sourceId())
            // Veil 4.3.2 expands these two raw imports but publishes an empty
            // includes() set for the compiled stages. Their actual resource
            // bytes and expanded function/interface semantics are checked below.
            || !vertex.includes().isEmpty()
            || !fragment.includes().isEmpty()
            || !vertex.definitionDependencies().isEmpty()
            || !fragment.definitionDependencies().isEmpty()
            || !vertex.uniformBindings().isEmpty()
            || !fragment.uniformBindings().isEmpty()) {
            throw unsupported("stage identity/include/definitions: stages=" + definition.shaders()
                + ", vertexId=" + vertex.sourceId() + ", fragmentId=" + fragment.sourceId()
                + ", vertexIncludes=" + vertex.includes() + ", fragmentIncludes="
                + fragment.includes() + ", vertexDefinitions=" + vertex.definitionDependencies()
                + ", fragmentDefinitions=" + fragment.definitionDependencies()
                + ", vertexBindings=" + vertex.uniformBindings()
                + ", fragmentBindings=" + fragment.uniformBindings());
        }
        for (Int2ObjectMap.Entry<ResourceLocation> entry : definition.shaders().int2ObjectEntrySet()) {
            if (!SpringDrawContract.PROGRAM.equals(entry.getValue()))
                throw unsupported("program stage resource changed: " + entry.getValue());
        }

        byte[] definitionBytes = read(resources,
            sourceSet.getShaderDefinitionLister().idToFile(SpringDrawContract.PROGRAM));
        byte[] vertexBytes = read(resources,
            sourceSet.getTypeConverter(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER)
                .idToFile(SpringDrawContract.PROGRAM));
        byte[] fragmentBytes = read(resources,
            sourceSet.getTypeConverter(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER)
                .idToFile(SpringDrawContract.PROGRAM));
        byte[] fogBytes = read(resources, ShaderManager.INCLUDE_LISTER.idToFile(FOG));
        byte[] lightBytes = read(resources, ShaderManager.INCLUDE_LISTER.idToFile(LIGHT));
        requireHash("definition", definitionBytes);
        requireHash("vertex", vertexBytes);
        requireHash("fragment", fragmentBytes);
        requireHash("fog", fogBytes);
        requireHash("light", lightBytes);

        // Compare all functions after the same GLSL parser normalizes the trusted source.
        // This catches Veil shader modifications even if resource IDs and includes are unchanged.
        String fog = new String(fogBytes, StandardCharsets.UTF_8);
        String light = new String(lightBytes, StandardCharsets.UTF_8);
        compareStage("vertex", canonical(light + "\n" + fog + "\n"
            + withoutIncludes(vertexBytes)), vertex.sourceCode());
        compareStage("fragment", canonical(fog + "\n" + withoutIncludes(fragmentBytes)),
            fragment.sourceCode());
    }

    private static byte[] read(ResourceManager resources, ResourceLocation file) throws IOException {
        try (InputStream stream = resources.getResourceOrThrow(file).open()) {
            return stream.readAllBytes();
        }
    }

    static void requireHash(String name, byte[] bytes) throws ShaderException {
        try {
            String actual = java.util.HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!SOURCE_HASHES.get(name).equals(actual))
                throw unsupported(name + " source changed: sha256=" + actual);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String withoutIncludes(byte[] source) {
        return new String(source, StandardCharsets.UTF_8)
            .replaceAll("(?m)^\\s*#include\\s+[^\\r\\n]+\\r?\\n?", "");
    }

    private static String canonical(String source) throws ShaderException {
        try {
            return GlslParser.preprocessParse(source, new java.util.HashMap<>()).toSourceString();
        } catch (Exception failure) {
            throw unsupported("cannot parse trusted shader source: " + failure);
        }
    }

    static void compareStage(String stage, String trusted, String actual) throws ShaderException {
        Map<String, String> expected = functions(trusted);
        Map<String, String> found = functions(actual);
        if (expected.isEmpty() || !expected.keySet().equals(found.keySet()))
            throw unsupported(stage + " preprocessed functions changed; expected="
                + expected.keySet() + ", actual=" + found.keySet());
        for (Map.Entry<String, String> entry : expected.entrySet())
            if (!entry.getValue().equals(found.get(entry.getKey())))
                throw unsupported(stage + " preprocessed function " + entry.getKey() + ' '
                    + firstDifference(entry.getValue(), found.get(entry.getKey())));
        String originalGlobals = globals(trusted), liveGlobals = globals(actual);
        if (!originalGlobals.equals(liveGlobals))
            throw unsupported(stage + " preprocessed interface "
                + firstDifference(originalGlobals, liveGlobals));
        List<String> originalDirectives = directives(trusted, false);
        List<String> liveDirectives = directives(actual, true, stage);
        if (!originalDirectives.equals(liveDirectives))
            throw unsupported(stage + " preprocessed directives expected=" + originalDirectives
                + ", actual=" + liveDirectives);
    }

    private static String firstDifference(String original, String actual) {
        int index = 0;
        while (index < Math.min(original.length(), actual.length())
            && original.charAt(index) == actual.charAt(index)) index++;
        int start = Math.max(0, index - 32);
        return "differenceAt=" + index + ", expected="
            + original.substring(start, Math.min(original.length(), index + 72))
            + ", actual=" + actual.substring(start, Math.min(actual.length(), index + 72));
    }

    private static Map<String, String> functions(String source) throws ShaderException {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = FUNCTION.matcher(source);
        while (matcher.find()) {
            int depth = 1;
            int end = matcher.end();
            while (end < source.length() && depth > 0) {
                char next = source.charAt(end++);
                if (next == '{') depth++;
                else if (next == '}') depth--;
            }
            if (depth != 0) throw unsupported("unbalanced shader function");
            String signature = matcher.group().replaceAll("\\s+", "");
            String body = source.substring(matcher.end(), end).replaceAll("\\s+", "");
            if (result.put(signature, body) != null)
                throw unsupported("duplicate shader function " + signature);
        }
        return result;
    }

    private static String globals(String source) throws ShaderException {
        StringBuilder declarations = new StringBuilder();
        Matcher matcher = FUNCTION.matcher(source);
        int cursor = 0;
        while (matcher.find(cursor)) {
            declarations.append(source, cursor, matcher.start());
            int depth = 1;
            cursor = matcher.end();
            while (cursor < source.length() && depth > 0) {
                char next = source.charAt(cursor++);
                if (next == '{') depth++;
                else if (next == '}') depth--;
            }
            if (depth != 0) throw unsupported("unbalanced shader function");
        }
        declarations.append(source, cursor, source.length());
        return declarations.toString()
            .replaceAll("(?m)^\\s*#.*$", "")
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//[^\\r\\n]*", "")
            .replaceAll("\\s+", "");
    }

    private static List<String> directives(String source, boolean allowVeilFeature)
        throws ShaderException {
        return directives(source, allowVeilFeature, "");
    }

    private static List<String> directives(String source, boolean allowVeilFeature, String stage)
        throws ShaderException {
        List<String> result = new java.util.ArrayList<>();
        Matcher matcher = DIRECTIVE.matcher(source);
        while (matcher.find()) {
            String kind = matcher.group(1);
            // ShaderTranslator replaces the version with its own #version 460;
            // line mapping has no execution effect. New extensions are not ignored.
            if (kind.equals("version") || kind.equals("line"))
                continue;
            String normalized = ("#" + kind + " " + matcher.group(2).trim())
                .replaceAll("\\s+", " ");
            if (allowVeilFeature && VEIL_CAPABILITY_MACROS.contains(normalized))
                continue;
            if (allowVeilFeature && stage.equals("vertex")
                && VEIL_VERTEX_LIGHT_MACROS.contains(normalized)) continue;
            if (!kind.equals("define"))
                throw unsupported("unexpected spring preprocessor directive: " + normalized);
            result.add(normalized);
        }
        java.util.Collections.sort(result);
        return result;
    }

    private static ShaderException unsupported(String detail) {
        return new ShaderException("Unsupported Simulated spring shader variant", detail);
    }
}
