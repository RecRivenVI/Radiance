package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.api.render.RenderPlatform;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Source texture energy, refreshed with resources rather than measured on every frame. */
public final class CelestialTextureProfiles {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static long epoch = -1;
    private static float sun;
    private static final float[] moon = new float[8];

    private CelestialTextureProfiles() {}

    public static synchronized void write(java.nio.ByteBuffer buffer, int offset, int phase) {
        long current = RenderPlatform.resourceEpoch();
        if (current != epoch) {
            sun = read("textures/environment/sun.png", 1, 1, 0, 0.03f);
            for (int i = 0; i < 8; i++)
                moon[i] = read("textures/environment/moon_phases.png", 4, 2, i, 0.05f);
            epoch = current;
        }
        buffer.putFloat(offset, sun).putFloat(offset + 4, moon[0]);
        buffer.putFloat(offset + 8, moon[0] > 0 ? moon[Math.floorMod(phase, 8)] / moon[0] : 0);
        buffer.putFloat(offset + 12, 0);
    }

    private static float read(String path, int columns, int rows, int tile, float angle) {
        try (var stream =
                        Minecraft.getInstance()
                                .getResourceManager()
                                .open(ResourceLocation.withDefaultNamespace(path));
                var image = NativeImage.read(stream)) {
            int width = image.getWidth() / columns, height = image.getHeight() / rows;
            int startX = (tile % columns) * width, startY = (tile / columns) * height;
            double integral = 0, extent = Math.tan(angle);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = image.getPixelRGBA(startX + x, startY + y);
                    double luminance =
                            (0.2126 * (abgr & 255)
                                            + 0.7152 * ((abgr >>> 8) & 255)
                                            + 0.0722 * ((abgr >>> 16) & 255))
                                    / 255.0;
                    double px = (2.0 * (x + 0.5) / width - 1) * extent;
                    double py = (2.0 * (y + 0.5) / height - 1) * extent;
                    double norm = 1 + px * px + py * py;
                    integral += luminance * (abgr >>> 24) / 255.0 / (norm * norm);
                }
            }
            return (float) (integral * 4 * extent * extent / (width * height));
        } catch (IOException failure) {
            LOGGER.warn("Cannot read celestial texture energy: {}", path, failure);
            return 0;
        }
    }
}
