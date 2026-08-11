package com.radiance.compatibility.veil;

import com.radiance.compatibility.simulated.SpringDrawContract;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IExternalShaderProgram;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3fc;

/** Original Simulated spring shader input, lowered into the Vulkan raster vertex binding. */
public final class SpringRasterLowering {
    private static final Pattern ATTRIBUTE = Pattern.compile(
        "layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s+"
            + "(\\w+)\\s+(\\w+)\\s*;");
    private static final List<Input> ORIGINAL_INPUTS = List.of(
        new Input(0, "vec3", "Position"), new Input(1, "vec4", "Color"),
        new Input(2, "vec2", "UV0"), new Input(3, "ivec2", "UV2"),
        new Input(4, "vec3", "Normal"));

    private SpringRasterLowering() {}

    public static boolean isProgram(ResourceLocation name) {
        return SpringDrawContract.PROGRAM.equals(name);
    }

    /** Checks the uploaded MeshData format at the actual VertexBuffer raster draw. */
    public static void requireDraw(ShaderInstance shader, VertexFormat uploadedFormat,
        VertexFormat.Mode mode, int vertexCount, int indexCount) {
        if (!(shader instanceof IExternalShaderProgram external)) return;
        if (!SpringDrawContract.PROGRAM.toString().equals(
            external.radiance$getExternalShader(mode).name())) return;
        requireDraw(SpringDrawContract.PROGRAM, uploadedFormat, mode, vertexCount, indexCount);
    }

    public static void requireDraw(ResourceLocation programName, VertexFormat uploadedFormat,
        VertexFormat.Mode mode, int vertexCount, int indexCount) {
        if (!isProgram(programName)) return;
        if (uploadedFormat == null || mode != VertexFormat.Mode.QUADS
            || !SpringDrawContract.sameBlockBytes(uploadedFormat)
            || vertexCount <= 0 || vertexCount % 4 != 0
            || indexCount != vertexCount / 4 * 6)
            throw new IllegalStateException("Simulated spring raster MeshData is not original 32-byte QUADS: "
                + uploadedFormat + ", mode=" + mode + ", vertices=" + vertexCount
                + ", indices=" + indexCount);
    }

    public static VertexFormat vertexFormat(String name, String source) throws ShaderException {
        if (!SpringDrawContract.PROGRAM.toString().equals(name)) return null;
        List<Input> found = new ArrayList<>();
        Matcher matcher = ATTRIBUTE.matcher(source);
        while (matcher.find()) {
            found.add(new Input(Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3)));
        }
        found.sort(java.util.Comparator.comparingInt(Input::location));
        if (!found.equals(ORIGINAL_INPUTS))
            throw new ShaderException("Simulated spring raster vertex contract changed", found.toString());
        // The producer calls this element Stress; its bytes occupy BLOCK Color's four lanes.
        return DefaultVertexFormat.BLOCK;
    }

    /** Mirrors the live GL inputs of the pinned spring VSH at each raster draw. */
    public static void setLightingDefaults(ShaderProgram program) {
        writeLightingDefaults(program, VeilRenderSystem.getLight0Direction(),
            VeilRenderSystem.getLight1Direction());
    }

    static void writeLightingDefaults(ShaderProgram program,
        Vector3fc light0, Vector3fc light1) {
        ShaderUniform uniform = program.getUniform("Light0_Direction");
        if (uniform != null) uniform.setVector(light0);
        uniform = program.getUniform("Light1_Direction");
        if (uniform != null) uniform.setVector(light1);
    }

    private record Input(int location, String type, String name) {}
}
