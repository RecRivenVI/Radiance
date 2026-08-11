package com.radiance.compatibility.veil;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/** Fixed Simulated/Aeronautics 1.3.2 geometry consumers of Veil programs. */
public final class VeilSimulatedShaderAdapter {

    private static final Pattern ATTRIBUTE = Pattern.compile(
        "layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s+"
            + "(\\w+)\\s+(\\w+)\\s*;");
    private static final List<Attribute> BLOCK_INPUTS = List.of(
        new Attribute(0, "vec3", "Position"), new Attribute(1, "vec4", "Color"),
        new Attribute(2, "vec2", "UV0"), new Attribute(3, "ivec2", "UV2"),
        new Attribute(4, "vec3", "Normal"));
    private static final Map<String, Consumer> CONSUMERS = Map.of(
        "simulated:laser/laser", new Consumer(DefaultVertexFormat.POSITION_TEX_COLOR,
            List.of(new Attribute(0, "vec3", "Position"), new Attribute(1, "vec2", "UV"),
                new Attribute(2, "vec4", "Color"))),
        "simulated:laser_pointer/lens", new Consumer(DefaultVertexFormat.BLOCK,
            BLOCK_INPUTS.subList(0, 4)),
        "simulated:redstone_accumulator/diode", new Consumer(DefaultVertexFormat.BLOCK,
            BLOCK_INPUTS.subList(0, 4)),
        "simulated:rope/rope", new Consumer(DefaultVertexFormat.BLOCK, BLOCK_INPUTS),
        "simulated:spring/spring", new Consumer(DefaultVertexFormat.BLOCK, BLOCK_INPUTS),
        "aeronautics:burner_flame", new Consumer(DefaultVertexFormat.POSITION_TEX,
            List.of(new Attribute(0, "vec3", "Position"), new Attribute(1, "vec2", "UV0"))));

    private VeilSimulatedShaderAdapter() {
    }

    public static Object requireDiagramProgram(ResourceLocation name, Object candidate) {
        ShaderProgram program = (ShaderProgram) candidate;
        if ((CONSUMERS.containsKey(name.toString())
            || name.toString().equals("simulated:staff_overlay/staff_overlay"))
            && program == null) {
            throw new IllegalStateException("Diagram shader failed to apply: " + name);
        }
        return program;
    }

    public static final String DIAGRAM_POST = "simulated:contraption_diagram/outline_diagram";

    /** Keep paper-space masks fixed while sampling color/depth/dither at physical resolution. */
    static String adaptDiagramFragment(String name, String source) {
        if (!DIAGRAM_POST.equals(name)) return source;
        int main = source.indexOf("void main(");
        if (main < 0) return source;
        String body = source.substring(main);
        var dimensions = Pattern.compile("textureSize\\s*\\(\\s*DiffuseSampler0\\s*,\\s*0\\s*\\)");
        if (!dimensions.matcher(body).find()) return source;
        return "uniform vec2 RadianceDiagramLogicalSize;\n" + source.substring(0, main)
            + dimensions.matcher(body).replaceAll("ivec2(RadianceDiagramLogicalSize)");
    }

    public static VertexFormat vertexFormat(String name, String source) throws ShaderException {
        Consumer consumer = CONSUMERS.get(name);
        if (consumer == null) {
            return null;
        }
        List<Attribute> attributes = new ArrayList<>();
        Matcher matcher = ATTRIBUTE.matcher(source);
        while (matcher.find()) {
            attributes.add(new Attribute(Integer.parseInt(matcher.group(1)), matcher.group(2),
                matcher.group(3)));
        }
        attributes.sort(java.util.Comparator.comparingInt(Attribute::location));
        if (!attributes.equals(consumer.attributes())) {
            throw new ShaderException("Fixed diagram shader vertex contract changed: " + name,
                attributes.toString());
        }
        // Lens/diode consume BLOCK buffers even though their shaders omit the unused Normal
        // attribute. Spring's Stress attribute occupies the same bytes as BLOCK Color.
        return consumer.format();
    }

    public static void setLightingDefaults(ShaderProgram program, Matrix4fc modelView) {
        if (!CONSUMERS.containsKey(program.getName().toString())) {
            return;
        }
        float[] brightness = null;
        if (Minecraft.getInstance().level != null) {
            brightness = new float[6];
            for (Direction direction : Direction.values()) {
                brightness[direction.get3DDataValue()] =
                    Minecraft.getInstance().level.getShade(direction, true);
            }
        }
        writeLightingDefaults(program, modelView, VeilRenderSystem.getLight0Direction(),
            VeilRenderSystem.getLight1Direction(), brightness);
    }

    static void writeLightingDefaults(ShaderProgram program, Matrix4fc modelView,
        Vector3fc light0, Vector3fc light1, float[] brightness) {
        ShaderUniform uniform = program.getUniform("NormalMat");
        if (uniform != null) uniform.setMatrix(modelView.normal(new Matrix3f()), false);
        uniform = program.getUniform("Light0_Direction");
        if (uniform != null) uniform.setVector(light0);
        uniform = program.getUniform("Light1_Direction");
        if (uniform != null) uniform.setVector(light1);
        uniform = program.getUniform("VeilBlockFaceBrightness");
        if (uniform != null && brightness != null) uniform.setFloats(brightness);
    }

    private record Attribute(int location, String type, String name) {
    }

    private record Consumer(VertexFormat format, List<Attribute> attributes) {
    }
}
