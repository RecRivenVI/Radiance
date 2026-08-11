package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Captures the original generator into immutable textures, rather than rewriting the world map. */
public final class DiagramLightmaps implements AutoCloseable {
    private static final ThreadLocal<DiagramLightmaps> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<DiagramLightmaps> ACTIVE = new ThreadLocal<>();
    private final Map<Float, Image> images = new HashMap<>();
    private float factor;
    private int selected = -1;

    public int selected() { return selected; }

    public static int currentTexture() {
        var active = ACTIVE.get();
        return active == null ? -1 : active.selected;
    }

    public static void generateCurrent(float multiplier, Runnable generator) {
        var active = ACTIVE.get();
        if (active == null) generator.run(); else active.generate(multiplier, generator);
    }

    public void run(Runnable draw) {
        var previous = ACTIVE.get();
        int previousBinding = RenderSystem.getShaderTexture(2);
        selected = -1;
        ACTIVE.set(this);
        SimulatedRenderRecovery cleanup = new SimulatedRenderRecovery();
        cleanup.add(() -> { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); });
        cleanup.add(() -> RenderSystem.setShaderTexture(2, previousBinding));
        cleanup.run(draw);
    }

    public void generate(float multiplier, Runnable originalGenerator) {
        var previous = CAPTURE.get();
        factor = multiplier;
        selected = -1;
        CAPTURE.set(this);
        try {
            originalGenerator.run();
            if (selected < 0) throw new IllegalStateException("Original diagram lightmap did not upload");
            RenderSystem.setShaderTexture(2, selected);
        } finally {
            if (previous == null) CAPTURE.remove(); else CAPTURE.set(previous);
        }
    }

    public static boolean capture(DynamicTexture source) {
        DiagramLightmaps active = CAPTURE.get();
        if (active == null) return false;
        CAPTURE.remove(); // Uploading the new immutable texture must not capture itself.
        try {
            NativeImage pixels = source.getPixels();
            if (pixels == null) throw new IllegalStateException("Closed diagram lightmap source");
            int[] colors = new int[Math.multiplyExact(pixels.getWidth(), pixels.getHeight())];
            for (int y = 0; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++)
                    colors[y * pixels.getWidth() + x] = pixels.getPixelRGBA(x, y);
            Image image = active.images.get(active.factor);
            if (image == null || image.width != pixels.getWidth() || image.height != pixels.getHeight()
                || !Arrays.equals(image.colors, colors)) {
                NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
                copy.copyFrom(pixels);
                DynamicTexture texture;
                try { texture = new DynamicTexture(copy); }
                catch (RuntimeException | Error failure) { copy.close(); throw failure; }
                try { texture.setFilter(true, false); }
                catch (RuntimeException | Error failure) {
                    try { texture.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
                Image replacement = new Image(texture, colors, pixels.getWidth(), pixels.getHeight());
                active.images.put(active.factor, replacement);
                // Native texture release retains already-recorded descriptor/image references.
                if (image != null) image.texture.close();
                image = replacement;
            }
            active.selected = image.texture.getId();
            return true;
        } finally { CAPTURE.set(active); }
    }

    @Override public void close() {
        SimulatedRenderRecovery cleanup = new SimulatedRenderRecovery();
        images.values().forEach(image -> cleanup.add(image.texture::close));
        images.clear();
        selected = -1;
        cleanup.run(() -> {});
    }

    private record Image(DynamicTexture texture, int[] colors, int width, int height) {}
}
