package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.api.render.RenderPlatform;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;

/** Source atmosphere colors, independent of camera immersion and view-status fog. */
public final class VanillaEnvironment {
    private static long epoch = -1;
    private static Vec3 endColor = new Vec3(.04, .025, .055);

    private VanillaEnvironment() {}

    public static float linear(double value) {
        return (float) (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4));
    }

    public static float[] atmosphere(ClientLevel level, Vec3 camera) {
        int sky = level.effects().skyType().ordinal();
        if (sky == 2) {
            Vec3 color = endAverage();
            return new float[] {(float) color.x, (float) color.y, (float) color.z, 1};
        }
        // Match vanilla's spatial Gaussian sampling, but read only the biome's raw color.
        // FogRenderer's final color already contains immersion, status and weather transforms.
        Vec3 quart = camera.subtract(2, 2, 2).scale(.25);
        Vec3 color =
                CubicSampler.gaussianSampleVec3(
                        quart,
                        (x, y, z) ->
                                Vec3.fromRGB24(
                                        !level.hasChunk(x >> 2, z >> 2)
                                                ? (sky == 1 ? 0xc0d8ff : 0x330808)
                                                : level.getBiomeManager()
                                                        .getNoiseBiomeAtQuart(x, y, z)
                                                        .value()
                                                        .getFogColor()));
        if (sky == 1) {
            return new float[] {
                linear(color.x) / linear(192.0 / 255),
                linear(color.y) / linear(216.0 / 255),
                linear(color.z) / linear(1),
                1
            };
        }
        // The Nether has broad ambient illumination, never a fictitious sun or moon.
        return new float[] {linear(color.x) * .8f, linear(color.y) * .8f, linear(color.z) * .8f, 1};
    }

    private static Vec3 endAverage() {
        long current = RenderPlatform.resourceEpoch();
        if (epoch == current) return endColor;
        try (var stream =
                        Minecraft.getInstance()
                                .getResourceManager()
                                .open(
                                        ResourceLocation.withDefaultNamespace(
                                                "textures/environment/end_sky.png"));
                var image = NativeImage.read(stream)) {
            double r = 0, g = 0, b = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    r += linear((abgr & 255) / 255.0);
                    g += linear(((abgr >>> 8) & 255) / 255.0);
                    b += linear(((abgr >>> 16) & 255) / 255.0);
                }
            }
            endColor =
                    new Vec3(r, g, b).scale(1.0 / (image.getWidth() * (double) image.getHeight()));
        } catch (IOException failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Cannot read End atmosphere color", failure);
        }
        epoch = current;
        return endColor;
    }
}
