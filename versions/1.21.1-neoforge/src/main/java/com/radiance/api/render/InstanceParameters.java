package com.radiance.api.render;

import com.radiance.render.contract.RenderAbi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/**
 * Source-independent rigid state and optional analytical motion. The caller owns the direct CPU
 * buffer until synchronous submission returns; native copies it and never retains its address.
 */
public final class InstanceParameters {
    private final ByteBuffer data;

    public InstanceParameters(ByteBuffer storage) {
        if (!storage.isDirect() || storage.remaining() < RenderAbi.CANONICAL_INSTANCE_BYTES)
            throw new IllegalArgumentException(
                    "Canonical instance needs a complete direct CPU buffer");
        data = storage.slice().order(ByteOrder.nativeOrder());
        data.limit(RenderAbi.CANONICAL_INSTANCE_BYTES);
        for (int i = 0; i < RenderAbi.CANONICAL_INSTANCE_BYTES; i += 4) data.putInt(i, 0);
        for (int i = 0; i < 4; i++) {
            data.putFloat(i * 20, 1);
            data.putFloat(100 + i * 4, 1);
            data.putFloat(116 + i * 4, 1);
        }
        for (int i = 0; i < 3; i++) data.putFloat(64 + i * 16, 1);
        data.putFloat(188, 1);
    }

    public ByteBuffer bytes() {
        return data.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public InstanceParameters pose(Matrix4fc pose, Matrix3fc normal) {
        pose.get(0, data);
        normal.get(64, data);
        return this;
    }

    private void flag(int mask) {
        data.putInt(180, data.getInt(180) | mask);
    }

    public InstanceParameters color(float r, float g, float b, float a) {
        flag(RenderAbi.APPEARANCE_COLOR_MULTIPLY);
        put4(100, r, g, b, a);
        return this;
    }

    public InstanceParameters replaceColor(float r, float g, float b, float a) {
        flag(RenderAbi.APPEARANCE_COLOR_REPLACE);
        put4(116, r, g, b, a);
        return this;
    }

    public InstanceParameters lighting(int packedLight, int packedOverlay) {
        flag(RenderAbi.APPEARANCE_OVERRIDE_LIGHT | RenderAbi.APPEARANCE_OVERRIDE_OVERLAY);
        data.putInt(164, packedOverlay & 65535).putInt(168, (packedOverlay >>> 16) & 65535);
        data.putInt(172, packedLight & 65535).putInt(176, (packedLight >>> 16) & 65535);
        return this;
    }

    public InstanceParameters shadow(
            float width, float depth, float u, float v, float strength, float radius) {
        flag(RenderAbi.APPEARANCE_SHADOW_UV);
        put4(132, width, depth, u, v);
        put4(148, strength, radius, 0, 0);
        return this;
    }

    public InstanceParameters fluid(float progress, float scale, float offset) {
        flag(RenderAbi.APPEARANCE_FLUID_UV);
        data.putFloat(188, progress).putFloat(140, scale).putFloat(144, offset);
        return this;
    }

    public InstanceParameters rotate(Vector3fc axis, Vector3fc pivot, float rate, float phase) {
        data.putInt(192, data.getInt(192) | 1);
        axis.get(196, data);
        pivot.get(216, data);
        data.putFloat(208, rate).putFloat(212, phase);
        return this;
    }

    public InstanceParameters scroll(
            float sx,
            float sy,
            float scaleX,
            float scaleY,
            float phaseX,
            float phaseY,
            float baseX,
            float baseY) {
        flag(RenderAbi.APPEARANCE_UV_OFFSET);
        data.putInt(192, data.getInt(192) | 2);
        data.putFloat(228, sx)
                .putFloat(232, sy)
                .putFloat(236, scaleX)
                .putFloat(240, scaleY)
                .putFloat(244, phaseX)
                .putFloat(248, phaseY)
                .putFloat(252, baseX)
                .putFloat(256, baseY);
        return this;
    }

    private void put4(int at, float x, float y, float z, float w) {
        data.putFloat(at, x).putFloat(at + 4, y).putFloat(at + 8, z).putFloat(at + 12, w);
    }
}
