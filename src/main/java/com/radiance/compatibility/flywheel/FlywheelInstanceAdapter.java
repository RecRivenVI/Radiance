package com.radiance.compatibility.flywheel;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.Layout;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Exact adapters for the eight affine instance shaders in the supported mod set. */
public enum FlywheelInstanceAdapter {
    TRANSFORMED(0, "flywheel:instance/transformed.vert", 76,
        fields("color", 0, "overlay", 4, "light", 8, "pose", 12)),
    POSED(1, "flywheel:instance/posed.vert", 112,
        fields("color", 0, "overlay", 4, "light", 8, "pose", 12, "normal", 76)),
    ORIENTED(2, "flywheel:instance/oriented.vert", 52,
        fields("color", 0, "overlay", 4, "light", 8, "position", 12, "pivot", 24,
            "rotation", 36)),
    SHADOW(3, "flywheel:instance/shadow.vert", 36,
        fields("pos", 0, "entityPosXZ", 12, "size", 20, "alpha", 28, "radius", 32)),
    ROTATING(4, "create:instance/rotating.vert", 52,
        fields("color", 0, "light", 4, "overlay", 8, "rotation", 12, "pos", 28,
            "speed", 40, "offset", 44, "axis", 48)),
    SCROLLING(5, "create:instance/scrolling.vert", 72,
        fields("color", 0, "light", 4, "overlay", 8, "pos", 12, "rotation", 24,
            "speed", 40, "diff", 48, "scale", 56, "offset", 64)),
    SCROLLING_TRANSFORMED(6, "create:instance/scrolling_transformed.vert", 108,
        fields("pose", 0, "color", 64, "light", 68, "overlay", 72, "speed", 76,
            "diff", 84, "scale", 92, "offset", 100)),
    FLUID(7, "create:instance/fluid.vert", 88,
        fields("pose", 0, "color", 64, "light", 68, "overlay", 72, "progress", 76,
            "vScale", 80, "v0", 84));

    private final int nativeId;
    private final String shader;
    private final int byteSize;
    private final Map<String, Integer> offsets;

    FlywheelInstanceAdapter(int nativeId, String shader, int byteSize,
        Map<String, Integer> offsets) {
        this.nativeId = nativeId;
        this.shader = shader;
        this.byteSize = byteSize;
        this.offsets = offsets;
    }

    public int nativeId() {
        return nativeId;
    }

    public static FlywheelInstanceAdapter require(InstanceType<?> type) {
        ResourceLocation shaderId = type.vertexShader();
        String shader = shaderId == null ? "" : shaderId.toString();
        for (FlywheelInstanceAdapter adapter : values()) {
            if (adapter.shader.equals(shader)) {
                adapter.validate(type.layout());
                return adapter;
            }
        }
        throw new UnsupportedOperationException("Unsupported Flywheel instance shader " + shader);
    }

    private void validate(Layout layout) {
        if (layout.byteSize() != byteSize) {
            throw new IllegalArgumentException("Flywheel layout size for " + shader + " is "
                + layout.byteSize() + ", expected " + byteSize);
        }
        for (Map.Entry<String, Integer> expected : offsets.entrySet()) {
            Layout.Element actual = layout.asMap().get(expected.getKey());
            if (actual == null || actual.byteOffset() != expected.getValue()) {
                throw new IllegalArgumentException("Flywheel layout field " + expected.getKey()
                    + " for " + shader + " does not match offset " + expected.getValue());
            }
        }
        if (layout.asMap().size() != offsets.size()) {
            throw new IllegalArgumentException("Flywheel layout for " + shader
                + " has unexpected fields " + layout.asMap().keySet());
        }
    }

    private static Map<String, Integer> fields(Object... entries) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], (Integer) entries[i + 1]);
        }
        return Map.copyOf(result);
    }
}
