package com.radiance.api.render;

import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.lwjgl.system.MemoryUtil;

/** Bounded texture-layer/plane programs. All image dependencies retain source generations. */
public final class LayeredWorldMaterial implements AutoCloseable {
    public static final int BYTES = 112;
    private final RenderResources.Texture identity =
            RenderResources.rgba8(
                    1, 1, 1, RenderResources.Sampling.NEAREST, RenderResources.Sampling.NEAREST);
    private final ByteBuffer data = MemoryUtil.memCalloc(BYTES).order(ByteOrder.nativeOrder());
    private byte[] accepted;
    private List<TextureTasks.Owner> dependencies = List.of();
    private List<TextureTasks.Owner> acceptedDependencies = List.of();
    private boolean closed;

    public LayeredWorldMaterial() {
        data.putInt(0, -1).putInt(4, -1).putInt(8, -1).putInt(12, -1);
        data.putInt(24, -1);
        uv(1, 1, 0, 0, 0, 0);
    }

    public TextureTasks.Owner owner() {
        requireLive();
        return identity.owner();
    }

    public LayeredWorldMaterial images(
            TextureTasks.Owner base,
            TextureTasks.Owner detail,
            TextureTasks.Owner depth,
            TextureTasks.Owner strength,
            TextureTasks.Owner palette) {
        requireLive();
        TextureTasks.Owner[] inputs = {base, detail, depth, strength, palette};
        java.util.ArrayList<TextureTasks.Owner> all = new java.util.ArrayList<>();
        all.add(identity.owner());
        for (int i = 0; i < inputs.length; i++) {
            data.putInt(i < 4 ? i * 4 : 24, inputs[i] == null ? -1 : inputs[i].id());
            if (inputs[i] != null) all.add(inputs[i]);
        }
        dependencies = List.copyOf(all);
        return this;
    }

    /**
     * Projection 1 is view-direction layers; 2 is world-plane layers with a depth/strength mask.
     */
    public LayeredWorldMaterial program(
            int projection, int layers, boolean additive, boolean emitter) {
        requireLive();
        if (projection < 1 || projection > 2 || layers < 1 || layers > 128)
            throw new IllegalArgumentException("Invalid layered material program");
        data.putInt(16, projection)
                .putInt(20, layers)
                .putInt(28, (additive ? 1 : 0) | (emitter ? 2 : 0));
        return this;
    }

    /** UV scale/offset and velocity per Minecraft day, independently of geometry updates. */
    public LayeredWorldMaterial uv(
            float u, float v, float offsetU, float offsetV, float speedU, float speedV) {
        requireLive();
        four(32, u, v, offsetU, offsetV);
        if (!Float.isFinite(speedU) || !Float.isFinite(speedV))
            throw new IllegalArgumentException("Non-finite layered material velocity");
        // zw retain the horizontal origin residual of a plane program.
        data.putFloat(48, speedU).putFloat(52, speedV);
        return this;
    }

    public LayeredWorldMaterial depthMask(float minimumY, float span, float fade, float exponent) {
        requireLive();
        four(64, minimumY, span, fade, exponent);
        return this;
    }

    /** Horizontal plane array, with explicit total emission shared across its layers. */
    public LayeredWorldMaterial planes(
            double x,
            float y,
            double z,
            float radius,
            float step,
            float totalEmission,
            float alpha,
            float textureBias) {
        requireLive();
        if (totalEmission < 0 || alpha < 0 || alpha > 1 || radius < 0)
            throw new IllegalArgumentException("Invalid plane material energy or extent");
        if (!Double.isFinite(x) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite layered plane origin");
        four(80, (float) x, y, (float) z, radius);
        data.putFloat(56, (float) (x - (float) x)).putFloat(60, (float) (z - (float) z));
        four(96, step, totalEmission, alpha, textureBias);
        return this;
    }

    public boolean publish() {
        requireLive();
        if (dependencies.isEmpty())
            throw new IllegalStateException("Material images are not declared");
        byte[] current = new byte[BYTES];
        data.get(0, current);
        if (java.util.Arrays.equals(accepted, current) && acceptedDependencies.equals(dependencies))
            return true;
        boolean live =
                TextureProxy.TASKS.useAll(
                        dependencies,
                        () ->
                                TextureProxy.setWorldMaterial(
                                        identity.id(), MemoryUtil.memAddress(data)));
        if (live) {
            accepted = current;
            acceptedDependencies = dependencies;
        }
        return live;
    }

    /**
     * Generic HSV colour operation after source tint and texture sampling. An optional scalar
     * vertex field modulates the saturation/value powers by exp(-field). glintUV names field texels
     * (magnitude, subtraction weight in adjacent R32F texels); glintTexture holds
     * COLOUR_FIELD_MATERIAL_BIT | owner ID, without enabling glint. Geometric normals only; this
     * cache union cannot also carry vertex BRDF normals or an overlay.
     */
    public LayeredWorldMaterial colourField(
            TextureTasks.Owner base,
            TextureTasks.Owner field,
            float hue,
            float saturationPower,
            float valuePower,
            float subtractHueA,
            float subtractHueB,
            float subtractPower,
            float subtractWeight) {
        if (saturationPower <= 0 || valuePower <= 0 || subtractPower <= 0 || subtractWeight < 0)
            throw new IllegalArgumentException("Invalid colour operation");
        images(base, field, null, null, null);
        data.putInt(16, 3).putInt(20, 1).putInt(28, 0);
        four(32, hue, saturationPower, valuePower, subtractWeight);
        four(48, subtractHueA, subtractHueB, subtractPower, 0);
        four(96, 0, 0, 1, 0);
        return this;
    }

    /**
     * Four-by-four virtual colour cells on each original quad, without subdividing its geometry.
     */
    public LayeredWorldMaterial quadColourField() {
        requireLive();
        if (data.getInt(16) != 3 || data.getInt(4) < 0)
            throw new IllegalStateException("A quad field requires a scalar image colour program");
        data.putInt(16, 4);
        return this;
    }

    private void four(int offset, float x, float y, float z, float w) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w))
            throw new IllegalArgumentException("Non-finite layered material parameter");
        data.putFloat(offset, x)
                .putFloat(offset + 4, y)
                .putFloat(offset + 8, z)
                .putFloat(offset + 12, w);
    }

    private void requireLive() {
        if (closed) throw new IllegalStateException("Material is retired");
    }

    public void close() {
        if (!closed) {
            closed = true;
            try {
                identity.close();
            } finally {
                MemoryUtil.memFree(data);
            }
        }
    }
}
