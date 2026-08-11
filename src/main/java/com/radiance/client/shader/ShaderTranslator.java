package com.radiance.client.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderTranslator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\s*#version\\b.*$");
    private static final Pattern UNIFORM_PATTERN = Pattern.compile(
        "^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+[\\w\\d_]+"
            + "(?:\\s*\\[[^]]*])?\\s+(\\w+)"
            + "(?:\\s*\\[[^]]*])?(?:\\s*=\\s*[^;]+)?\\s*;\\s*$");
    private static final Pattern INPUT_OUTPUT_PATTERN = Pattern.compile(
        "^\\s*(?:(flat|smooth|noperspective|centroid|sample)\\s+)?"
            + "(in|out)\\s+([\\w\\d_]+)\\s+(\\w+)\\s*(\\[\\s*])?\\s*;\\s*$");
    private static final Pattern MAIN_FUNCTION_PATTERN = Pattern.compile(
        "\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");

    private ShaderTranslator() {
    }

    public static Result translate(VertexFormat vertexFormat, String vertexSource,
        String fragmentSource, List<ShaderField> fields) {
        return translate("", vertexFormat, vertexSource, fragmentSource, fields);
    }

    public static Result translate(String shaderName, VertexFormat vertexFormat,
        String vertexSource, String fragmentSource, List<ShaderField> fields) {
        Set<String> uniformNames = fields.stream()
            .filter(field -> !field.isSampler())
            .map(ShaderField::name)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> samplerNames = fields.stream()
            .filter(ShaderField::isSampler)
            .map(ShaderField::name)
            .collect(java.util.stream.Collectors.toSet());

        LinkedHashMap<String, Integer> attributeLocations = new LinkedHashMap<>();
        List<String> attributeNames = vertexFormat.getElementAttributeNames();
        for (int i = 0; i < attributeNames.size(); i++) {
            attributeLocations.put(attributeNames.get(i), i);
        }

        StageResult vertex = rewriteStage(vertexSource, true, attributeLocations,
            new LinkedHashMap<>(), uniformNames, samplerNames);
        StageResult fragment = rewriteStage(fragmentSource, false, new LinkedHashMap<>(),
            vertex.varyingLocations(), uniformNames, samplerNames);

        int uniformBufferSize = fields.stream()
            .mapToInt(field -> field.offset() + field.size())
            .max()
            .orElse(0);
        uniformBufferSize = Math.ceilDiv(uniformBufferSize, 16) * 16;

        return new Result(buildHeader(fields, Stage.VERTEX) + vertex.source(),
            buildHeader(fields, Stage.FRAGMENT) + wrapFragmentCoverage(fragment.source()), uniformBufferSize);
    }

    public static Result translateExternal(String shaderName, VertexFormat vertexFormat,
        String vertexSource, String fragmentSource, List<ShaderField> fields) {
        ExternalResult result = translateExternalStages(shaderName, vertexFormat, vertexSource,
            null, null, fragmentSource, fields);
        return new Result(result.vertexSource(), result.fragmentSource(),
            result.uniformBufferSize());
    }

    public static ExternalResult translateExternalStages(String shaderName,
        VertexFormat vertexFormat, String vertexSource, String tessellationControlSource,
        String tessellationEvaluationSource, String fragmentSource, List<ShaderField> fields) {
        boolean tessellated = tessellationControlSource != null
            || tessellationEvaluationSource != null;
        if ((tessellationControlSource == null) != (tessellationEvaluationSource == null)) {
            throw new IllegalArgumentException(
                "External tessellation shaders require both control and evaluation stages: "
                    + shaderName);
        }
        String finalPositionStage = tessellated ? tessellationEvaluationSource : vertexSource;
        String wrappedPositionStage = wrapExternalPositionMain(shaderName, finalPositionStage);
        if (tessellated) {
            tessellationEvaluationSource = wrappedPositionStage;
        } else {
            vertexSource = wrappedPositionStage;
        }
        fragmentSource = fragmentSource.replaceAll("\\bgl_FragCoord\\b",
            "vec4(gl_FragCoord.x, RadianceTargetHeight - gl_FragCoord.y, gl_FragCoord.z, gl_FragCoord.w)");

        Set<String> uniformNames = fields.stream().filter(field -> !field.isSampler())
            .map(ShaderField::name).collect(java.util.stream.Collectors.toSet());
        Set<String> samplerNames = fields.stream().filter(ShaderField::isSampler)
            .map(ShaderField::name).collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<String, Integer> attributes = new LinkedHashMap<>();
        List<String> attributeNames = vertexFormat.getElementAttributeNames();
        for (int i = 0; i < attributeNames.size(); i++) attributes.put(attributeNames.get(i), i);

        StageResult vertex = rewriteStage(vertexSource, true, attributes,
            new LinkedHashMap<>(), uniformNames, samplerNames);
        StageResult tessControl = null;
        StageResult tessEvaluation = null;
        Map<String, Integer> fragmentInputs = vertex.varyingLocations();
        if (tessellated) {
            tessControl = rewriteStage(tessellationControlSource, false,
                new LinkedHashMap<>(), vertex.varyingLocations(), uniformNames, samplerNames);
            tessEvaluation = rewriteStage(tessellationEvaluationSource, false,
                new LinkedHashMap<>(), tessControl.varyingLocations(), uniformNames, samplerNames);
            fragmentInputs = tessEvaluation.varyingLocations();
        }
        StageResult fragment = rewriteStage(fragmentSource, false, new LinkedHashMap<>(),
            fragmentInputs, uniformNames, samplerNames);
        int uniformSize = fields.stream().mapToInt(field -> field.offset() + field.size())
            .max().orElse(0);
        uniformSize = Math.ceilDiv(uniformSize, 16) * 16;
        String vertexHeader = buildHeader(fields, Stage.VERTEX);
        String fragmentHeader = buildHeader(fields, Stage.FRAGMENT);
        return new ExternalResult(vertexHeader + vertex.source(),
            tessControl == null ? null : buildHeader(fields, Stage.TESS_CONTROL) + tessControl.source(),
            tessEvaluation == null ? null
                : buildHeader(fields, Stage.TESS_EVALUATION) + tessEvaluation.source(),
            fragmentHeader + wrapFragmentCoverage(fragment.source()), uniformSize);
    }

    private static String wrapFragmentCoverage(String source) {
        // Only the default GUI color target uses this push constant. Native draws to
        // user framebuffers set it to zero, preserving their original alpha and MRTs.
        Matcher output = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*0\\s*\\)\\s*out\\s+vec4\\s+(\\w+)\\s*;")
            .matcher(source);
        if (!output.find()) return source; // e.g. depth-only fragment stage
        Matcher main = MAIN_FUNCTION_PATTERN.matcher(source);
        if (!main.find()) throw new IllegalArgumentException("GUI fragment shader has no main");
        return main.replaceFirst("void radianceCoverageMain()") + """

            layout(push_constant) uniform RadianceCoverageState { uint opaqueDraw; } radianceCoverage;
            void main() {
                radianceCoverageMain();
                if (radianceCoverage.opaqueDraw != 0u) %s.a = 1.0;
            }
            """.formatted(output.group(1));
    }

    private static String wrapExternalPositionMain(String shaderName, String source) {
        Matcher main = MAIN_FUNCTION_PATTERN.matcher(source);
        if (!main.find()) {
            throw new IllegalArgumentException("External position shader has no main function: "
                + shaderName);
        }
        return main.replaceFirst("void radianceExternalMain()") + """

            void main() {
                radianceExternalMain();
                gl_Position.y = -gl_Position.y;
                gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
            }
            """;
    }

    private static StageResult rewriteStage(String source, boolean vertexStage,
        Map<String, Integer> inputLocations, Map<String, Integer> varyingLocations,
        Set<String> uniformNames, Set<String> samplerNames) {
        if (vertexStage) {
            source = source.replaceAll("\\bgl_VertexID\\b", "gl_VertexIndex");
        }
        source = source.replaceAll("\\bsampler2D\\b", "RadianceSampler2D");
        StringBuilder builder = new StringBuilder();
        LinkedHashMap<String, Integer> outputLocations = new LinkedHashMap<>();
        int nextOutputLocation = 0;

        for (String line : source.split("\\R", -1)) {
            if (VERSION_PATTERN.matcher(line)
                .matches()) {
                continue;
            }

            if (UNIFORM_PATTERN.matcher(line)
                .matches()) {
                continue;
            }

            Matcher ioMatcher = INPUT_OUTPUT_PATTERN.matcher(line);
            if (ioMatcher.matches()) {
                String interpolation = ioMatcher.group(1);
                String qualifier = ioMatcher.group(2);
                String type = ioMatcher.group(3);
                String name = ioMatcher.group(4);
                String arraySuffix = ioMatcher.group(5) == null ? "" : "[]";
                String interpolationPrefix = interpolation == null ? "" : interpolation + " ";
                if ("in".equals(qualifier)) {
                    Integer location = inputLocations.get(name);
                    if (location != null) {
                        builder.append("layout(location = ")
                            .append(location)
                            .append(") ")
                            .append(interpolationPrefix)
                            .append("in ")
                            .append(type)
                            .append(' ')
                            .append(name)
                            .append(arraySuffix)
                            .append(';')
                            .append('\n');
                        continue;
                    }
                    Integer varyingLocation = varyingLocations.get(name);
                    if (varyingLocation != null) {
                        builder.append("layout(location = ")
                            .append(varyingLocation)
                            .append(") ")
                            .append(interpolationPrefix)
                            .append("in ")
                            .append(type)
                            .append(' ')
                            .append(name)
                            .append(arraySuffix)
                            .append(';')
                            .append('\n');
                        continue;
                    }
                } else if ("out".equals(qualifier)) {
                    Integer location = outputLocations.get(name);
                    if (location == null) {
                        location = nextOutputLocation++;
                        outputLocations.put(name, location);
                    }
                    builder.append("layout(location = ")
                        .append(location)
                        .append(") ")
                        .append(interpolationPrefix)
                        .append("out ")
                        .append(type)
                        .append(' ')
                        .append(name)
                        .append(arraySuffix)
                        .append(';')
                        .append('\n');
                    continue;
                }
            }

            builder.append(line)
                .append('\n');
        }

        return new StageResult(builder.toString(), outputLocations);
    }

    private enum Stage {
        VERTEX,
        TESS_CONTROL,
        TESS_EVALUATION,
        FRAGMENT;

        boolean isFragment() {
            return this == FRAGMENT;
        }
    }

    /**
     * Builds the shared compatibility header for one shader stage.
     *
     * <p>The translated shader keeps the per-sampler framebuffer Y flip by wrapping the sampler in
     * {@code RadianceSampler2D}. Sampling entry points are re-provided as plain functions for the
     * operations whose arguments are ordinary values (coordinates, gradients, lod), and as
     * preprocessor macros for the {@code *Offset} family, whose texel offset must remain a
     * compile-time constant and therefore cannot be passed through a function parameter.</p>
     *
     * <p>Fragment-only entry points (bias variants and {@code textureQueryLod}) are emitted only for
     * the fragment stage: a wrapper body that calls a stage-restricted built-in is rejected by the
     * compiler even when the wrapper is never called.</p>
     */
    private static String buildHeader(List<ShaderField> fields, Stage stage) {
        StringBuilder builder = new StringBuilder();
        builder.append("#version 460\n");
        builder.append("#extension GL_EXT_nonuniform_qualifier : enable\n\n");
        builder.append("layout(set = 0, binding = 0) uniform sampler2D textures[];\n\n");
        builder.append("struct RadianceSampler2D { uint index; bool flipY; };\n");
        builder.append("vec2 radianceSamplerUv(RadianceSampler2D s, vec2 uv) { return s.flipY ? vec2(uv.x, 1.0 - uv.y) : uv; }\n");
        builder.append("vec2 radianceSamplerGradY(RadianceSampler2D s, vec2 g) { return s.flipY ? vec2(g.x, -g.y) : g; }\n");
        builder.append("ivec2 radianceSamplerTexel(RadianceSampler2D s, ivec2 p, int lod) {\n");
        builder.append("    ivec2 size = textureSize(textures[nonuniformEXT(int(s.index))], lod);\n");
        builder.append("    return s.flipY ? ivec2(p.x, size.y - 1 - p.y) : p;\n");
        builder.append("}\n");
        builder.append("vec3 radianceProjFlipY(RadianceSampler2D s, vec3 p) { return s.flipY ? vec3(p.x, p.z - p.y, p.z) : p; }\n");
        builder.append("vec4 radianceProjFlipY(RadianceSampler2D s, vec4 p) { return s.flipY ? vec4(p.x, p.w - p.y, p.z, p.w) : p; }\n\n");

        builder.append("vec4 texture(RadianceSampler2D s, vec2 uv) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, uv)); }\n");
        builder.append("vec4 textureLod(RadianceSampler2D s, vec2 uv, float lod) { return textureLod(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, uv), lod); }\n");
        builder.append("vec4 textureGrad(RadianceSampler2D s, vec2 uv, vec2 dx, vec2 dy) { return textureGrad(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, uv), radianceSamplerGradY(s, dx), radianceSamplerGradY(s, dy)); }\n");
        builder.append("vec4 textureProj(RadianceSampler2D s, vec3 p) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.z)); }\n");
        builder.append("vec4 textureProj(RadianceSampler2D s, vec4 p) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.w)); }\n");
        builder.append("vec4 textureProjLod(RadianceSampler2D s, vec3 p, float lod) { return textureLod(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.z), lod); }\n");
        builder.append("vec4 textureProjLod(RadianceSampler2D s, vec4 p, float lod) { return textureLod(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.w), lod); }\n");
        builder.append("ivec2 textureSize(RadianceSampler2D s, int lod) { return textureSize(textures[nonuniformEXT(int(s.index))], lod); }\n");
        builder.append("int textureQueryLevels(RadianceSampler2D s) { return textureQueryLevels(textures[nonuniformEXT(int(s.index))]); }\n");
        builder.append("vec4 texelFetch(RadianceSampler2D s, ivec2 p, int lod) { return texelFetch(textures[nonuniformEXT(int(s.index))], radianceSamplerTexel(s, p, lod), lod); }\n");

        if (stage.isFragment()) {
            builder.append("vec4 texture(RadianceSampler2D s, vec2 uv, float bias) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, uv), bias); }\n");
            builder.append("vec4 textureProj(RadianceSampler2D s, vec3 p, float bias) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.z), bias); }\n");
            builder.append("vec4 textureProj(RadianceSampler2D s, vec4 p, float bias) { return texture(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, p.xy / p.w), bias); }\n");
            builder.append("vec2 textureQueryLod(RadianceSampler2D s, vec2 uv) { return textureQueryLod(textures[nonuniformEXT(int(s.index))], radianceSamplerUv(s, uv)); }\n");
        }

        builder.append('\n');
        builder.append(offsetMacros());
        builder.append('\n');
        builder.append("layout(std140, set = 1, binding = 0) uniform Uniforms {\n");
        for (ShaderField field : fields) {
            builder.append("    ")
                .append(field.glslType())
                .append(' ')
                .append(field.fieldName());
            if (field.isArray()) {
                builder.append('[').append(field.arrayLength()).append(']');
            }
            builder.append(";\n");
        }
        builder.append("} uniforms;\n\n");
        for (ShaderField field : fields) {
            if (field.isSampler()) {
                builder.append("#define ")
                    .append(field.name())
                    .append(" RadianceSampler2D(uint(uniforms.")
                    .append(field.fieldName())
                    .append(") & 0x7FFFFFFFu, (uint(uniforms.")
                    .append(field.fieldName())
                    .append(") & 0x80000000u) != 0u)\n");
            } else {
                builder.append("#define ")
                    .append(field.name())
                    .append(" (uniforms.")
                    .append(field.fieldName())
                    .append(")\n");
            }
        }
        builder.append('\n');
        return builder.toString();
    }

    /**
     * The {@code *Offset} sampling family. GLSL requires the texel offset to be a compile-time
     * constant, so it cannot be routed through a function parameter; these are macros instead, which
     * keep the caller's constant expression intact while adding the flip compensation.
     *
     * <p>The preprocessor cannot overload a macro by arity, so only the non-bias signatures are
     * covered. A call in an uncovered signature fails loudly at compile time instead of being
     * silently mishandled.</p>
     */
    private static String offsetMacros() {
        StringBuilder builder = new StringBuilder();
        builder.append("#define texelFetchOffset(s, p, lod, o) texelFetch((s), (p) + (o), (lod))\n");
        builder.append("#define textureOffset(s, p, o) ((s).flipY ? textureOffset(textures[nonuniformEXT(int((s).index))], radianceSamplerUv((s), (p)), ivec2((o).x, -(o).y)) : textureOffset(textures[nonuniformEXT(int((s).index))], (p), (o)))\n");
        builder.append("#define textureLodOffset(s, p, lod, o) ((s).flipY ? textureLodOffset(textures[nonuniformEXT(int((s).index))], radianceSamplerUv((s), (p)), (lod), ivec2((o).x, -(o).y)) : textureLodOffset(textures[nonuniformEXT(int((s).index))], (p), (lod), (o)))\n");
        builder.append("#define textureGradOffset(s, p, dx, dy, o) ((s).flipY ? textureGradOffset(textures[nonuniformEXT(int((s).index))], radianceSamplerUv((s), (p)), radianceSamplerGradY((s), (dx)), radianceSamplerGradY((s), (dy)), ivec2((o).x, -(o).y)) : textureGradOffset(textures[nonuniformEXT(int((s).index))], (p), (dx), (dy), (o)))\n");
        builder.append("#define textureProjOffset(s, p, o) ((s).flipY ? textureProjOffset(textures[nonuniformEXT(int((s).index))], radianceProjFlipY((s), (p)), ivec2((o).x, -(o).y)) : textureProjOffset(textures[nonuniformEXT(int((s).index))], (p), (o)))\n");
        builder.append("#define textureProjLodOffset(s, p, lod, o) ((s).flipY ? textureProjLodOffset(textures[nonuniformEXT(int((s).index))], radianceProjFlipY((s), (p)), (lod), ivec2((o).x, -(o).y)) : textureProjLodOffset(textures[nonuniformEXT(int((s).index))], (p), (lod), (o)))\n");
        builder.append("#define textureProjGradOffset(s, p, dx, dy, o) ((s).flipY ? textureProjGradOffset(textures[nonuniformEXT(int((s).index))], radianceProjFlipY((s), (p)), radianceSamplerGradY((s), (dx)), radianceSamplerGradY((s), (dy)), ivec2((o).x, -(o).y)) : textureProjGradOffset(textures[nonuniformEXT(int((s).index))], (p), (dx), (dy), (o)))\n");
        return builder.toString();
    }

    public record Result(String vertexSource, String fragmentSource, int uniformBufferSize) {
    }

    public record ExternalResult(String vertexSource, String tessellationControlSource,
                                 String tessellationEvaluationSource, String fragmentSource,
                                 int uniformBufferSize) {
    }

    private record StageResult(String source, LinkedHashMap<String, Integer> varyingLocations) {
    }
}
