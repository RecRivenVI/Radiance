package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.util.ARGB;
import net.minecraft.Util;

public class MipmapUtil {

    private static final float CUTOUT_ALPHA_COVERAGE_THRESHOLD = 0.5F;
    private static final float[] COLOR_FRACTIONS =
            Util.make(
                    new float[256],
                    list -> {
                        for (int i = 0; i < list.length; i++) {
                            list[i] = (float) Math.pow(i / 255.0F, 2.2);
                        }
                    });

    private MipmapUtil() {}

    // straight forward, need speed up
    public static NativeImage getSpecificMipmapLevelImage(NativeImage original, int targetLevel) {
        if (targetLevel == 0) {
            NativeImage ret =
                    new NativeImage(
                            original.format(), original.getWidth(), original.getHeight(), false);
            ret.copyFrom(original);
            return ret;
        }

        NativeImage currentSource = original;
        boolean bl = hasAlpha(original);

        boolean close = false;

        for (int i = 1; i <= targetLevel; i++) {
            int newWidth = currentSource.getWidth() >> 1;
            int newHeight = currentSource.getHeight() >> 1;

            if (newWidth == 0 || newHeight == 0) {
                break;
            }

            NativeImage nextLevel =
                    new NativeImage(currentSource.format(), newWidth, newHeight, false);

            for (int x = 0; x < newWidth; x++) {
                for (int y = 0; y < newHeight; y++) {
                    nextLevel.setPixelRGBA(
                            x,
                            y,
                            blend(
                                    currentSource.getPixelRGBA(x * 2, y * 2),
                                    currentSource.getPixelRGBA(x * 2 + 1, y * 2),
                                    currentSource.getPixelRGBA(x * 2, y * 2 + 1),
                                    currentSource.getPixelRGBA(x * 2 + 1, y * 2 + 1),
                                    bl));
                }
            }

            if (close) {
                currentSource.close();
            }

            currentSource = nextLevel;
            close = true;
        }

        return currentSource;
    }

    public static NativeImage[] buildMipmapChain(
            NativeImage original, AuxiliaryTextures texture, NativeImage base) {
        int maxLevel = 0;
        int width = original.getWidth();
        int height = original.getHeight();
        while ((width >> 1) > 0 && (height >> 1) > 0) {
            width >>= 1;
            height >>= 1;
            maxLevel++;
        }

        NativeImage[] levels = new NativeImage[maxLevel + 1];
        levels[0] = original;
        boolean hasAlpha = texture == AuxiliaryTextures.FLAG && hasAlpha(original);
        int[] coverage = binaryCoverage(base, original);
        int[] pixels = new int[4];
        int[] weights = {1, 1, 1, 1};

        for (int level = 1; level <= maxLevel; level++) {
            NativeImage previous = levels[level - 1];
            NativeImage next =
                    new NativeImage(
                            previous.format(),
                            previous.getWidth() >> 1,
                            previous.getHeight() >> 1,
                            false);
            int[] nextCoverage =
                    coverage == null ? null : new int[next.getWidth() * next.getHeight()];

            for (int x = 0; x < next.getWidth(); x++) {
                for (int y = 0; y < next.getHeight(); y++) {
                    int a = previous.getPixelRGBA(x * 2, y * 2);
                    int b = previous.getPixelRGBA(x * 2 + 1, y * 2);
                    int c = previous.getPixelRGBA(x * 2, y * 2 + 1);
                    int d = previous.getPixelRGBA(x * 2 + 1, y * 2 + 1);
                    int pixel;
                    if (coverage != null) {
                        pixels[0] = a;
                        pixels[1] = b;
                        pixels[2] = c;
                        pixels[3] = d;
                        int start = y * 2 * previous.getWidth() + x * 2;
                        weights[0] = coverage[start];
                        weights[1] = coverage[start + 1];
                        weights[2] = coverage[start + previous.getWidth()];
                        weights[3] = coverage[start + previous.getWidth() + 1];
                        int total = weights[0] + weights[1] + weights[2] + weights[3];
                        nextCoverage[y * next.getWidth() + x] = total;
                        pixel =
                                total == 0
                                        ? a
                                        : blendCoveredMaterial(pixels, weights, total, texture);
                    } else {
                        pixel =
                                switch (texture) {
                                    case NORMAL -> blendNormalMap(a, b, c, d);
                                    case SPECULAR -> blendSpecularMap(a, b, c, d);
                                    case FLAG -> blend(a, b, c, d, hasAlpha);
                                };
                    }
                    next.setPixelRGBA(x, y, pixel);
                }
            }

            levels[level] = next;
            coverage = nextCoverage;
        }

        return levels;
    }

    static void filterMetalRoughness(NativeImage[] specular, NativeImage normal, NativeImage base) {
        if (specular.length < 2 || !sameSize(specular[0], normal) || !sameSize(specular[0], base))
            return;
        boolean metal = false;
        for (int y = 0; y < base.getHeight(); ++y) {
            for (int x = 0; x < base.getWidth(); ++x) {
                int alpha = base.getPixelRGBA(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) return;
                metal |= alpha == 255 && ((specular[0].getPixelRGBA(x, y) >>> 8) & 255) >= 230;
            }
        }
        if (!metal) return;
        float[] previous = null;
        int previousWidth = normal.getWidth();
        for (int level = 1; level < specular.length; ++level) {
            NativeImage target = specular[level];
            int width = target.getWidth(), height = target.getHeight();
            // Average the authored vectors before renormalization. Keep unnormalized
            // sums so large mip footprints still weight every covered base texel once.
            float[] moments = new float[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int i = (y * width + x) * 4;
                    for (int dy = 0; dy < 2; ++dy) {
                        for (int dx = 0; dx < 2; ++dx) {
                            int sx = x * 2 + dx, sy = y * 2 + dy;
                            if (previous == null) {
                                if ((base.getPixelRGBA(sx, sy) >>> 24) == 0) continue;
                                int pixel = normal.getPixelRGBA(sx, sy);
                                float nx = (pixel & 255) / 127.5F - 1;
                                float ny = ((pixel >>> 8) & 255) / 127.5F - 1;
                                float xySquared = nx * nx + ny * ny;
                                float scale =
                                        xySquared > 1 ? (float) (1 / Math.sqrt(xySquared)) : 1;
                                moments[i] += nx * scale;
                                moments[i + 1] += ny * scale;
                                moments[i + 2] += (float) Math.sqrt(Math.max(0, 1 - xySquared));
                                moments[i + 3]++;
                            } else {
                                int source = (sy * previousWidth + sx) * 4;
                                for (int c = 0; c < 4; ++c) moments[i + c] += previous[source + c];
                            }
                        }
                    }
                }
            }
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int i = (y * width + x) * 4;
                    int pixel = target.getPixelRGBA(x, y);
                    if (((pixel >>> 8) & 255) < 230 || moments[i + 3] < 2) continue;
                    float nx = moments[i], ny = moments[i + 1], nz = moments[i + 2];
                    double length = Math.sqrt(nx * nx + ny * ny + nz * nz) / moments[i + 3];
                    target.setPixelRGBA(x, y, filteredMetalSmoothness(pixel, length));
                }
            }
            previous = moments;
            previousWidth = width;
        }
    }

    private static boolean sameSize(NativeImage first, NativeImage second) {
        return second != null
                && second.format() == NativeImage.Format.RGBA
                && first.getWidth() == second.getWidth()
                && first.getHeight() == second.getHeight();
    }

    static int filteredMetalSmoothness(int pixel, double length) {
        if (length >= 1 - 1e-6) return pixel;
        double roughness = 1 - (pixel & 255) / 255.0;
        double alpha = Math.max(1e-4, roughness * roughness);
        double power = 2 / (alpha * alpha) - 2;
        double unitLength = Math.clamp(length, 0, 1);
        // Toksvig's normal variance, with the GGX to Phong conversion from AGAA.
        double filtered =
                power == 0 ? 0 : power * unitLength / (unitLength + power * (1 - unitLength));
        int smoothness = (int) Math.round(255 * (1 - Math.sqrt(Math.sqrt(2 / (filtered + 2)))));
        return (pixel & ~255) | smoothness;
    }

    private static int[] binaryCoverage(NativeImage base, NativeImage material) {
        if (base == null
                || !base.format().hasAlpha()
                || base.getWidth() != material.getWidth()
                || base.getHeight() != material.getHeight()) return null;
        int width = base.getWidth();
        int height = base.getHeight();
        int[] coverage = null;
        boolean opaque = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int alpha = base.getPixelRGBA(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) return null;
                if (alpha == 0 && coverage == null) {
                    coverage = new int[width * height];
                    java.util.Arrays.fill(coverage, 0, index, 1);
                }
                if (alpha == 255) {
                    opaque = true;
                    if (coverage != null) coverage[index] = 1;
                }
            }
        }
        return opaque ? coverage : null;
    }

    static int blendCoveredMaterial(
            int[] pixels, int[] weights, int total, AuxiliaryTextures texture) {
        int red = weightedChannel(pixels, weights, total, 0, 0, 256);
        if (texture == AuxiliaryTextures.NORMAL) {
            return ARGB.color(
                    weightedChannel(pixels, weights, total, 24, 0, 256),
                    weightedChannel(pixels, weights, total, 16, 0, 256),
                    weightedChannel(pixels, weights, total, 8, 0, 256),
                    red);
        }
        int dielectric = 0;
        int porous = 0;
        long emission = 0;
        for (int i = 0; i < pixels.length; ++i) {
            if (((pixels[i] >>> 8) & 255) < 230) dielectric += weights[i];
            if (((pixels[i] >>> 16) & 255) < 65) porous += weights[i];
            emission += (long) emissionByte(pixels[i]) * weights[i];
        }
        int green =
                dielectric >= total - dielectric
                        ? weightedChannel(pixels, weights, dielectric, 8, 0, 230)
                        : weightedMetal(pixels, weights);
        int blue =
                porous >= total - porous
                        ? weightedChannel(pixels, weights, porous, 16, 0, 65)
                        : weightedChannel(pixels, weights, total - porous, 16, 65, 256);
        return ARGB.color((int) ((emission + total / 2) / total), blue, green, red);
    }

    private static int weightedMetal(int[] pixels, int[] weights) {
        int winner = 230;
        int votes = 0;
        for (int pixel : pixels) {
            int candidate = (pixel >>> 8) & 255;
            if (candidate < 230) continue;
            int count = 0;
            for (int i = 0; i < pixels.length; ++i) {
                if (((pixels[i] >>> 8) & 255) == candidate) count += weights[i];
            }
            if (count > votes) {
                votes = count;
                winner = candidate;
            }
        }
        return winner;
    }

    private static int weightedChannel(
            int[] pixels, int[] weights, int weight, int shift, int min, int max) {
        long sum = 0;
        for (int i = 0; i < pixels.length; ++i) {
            int channel = (pixels[i] >>> shift) & 255;
            if (channel >= min && channel < max) sum += (long) channel * weights[i];
        }
        return (int) ((sum + weight / 2) / weight);
    }

    static int blendNormalMap(int a, int b, int c, int d) {
        // LabPBR stores tangent-space XY, AO and height as independent linear data.
        return ARGB.color(
                averageChannel(a, b, c, d, 24),
                averageChannel(a, b, c, d, 16),
                averageChannel(a, b, c, d, 8),
                averageChannel(a, b, c, d, 0));
    }

    static int blendSpecularMap(int a, int b, int c, int d) {
        // NativeImage packs bytes as ABGR. Alpha 255 means zero emission.
        int emission =
                (emissionByte(a) + emissionByte(b) + emissionByte(c) + emissionByte(d) + 2) / 4;
        int green =
                dominantReflectance(
                        (a >>> 8) & 255, (b >>> 8) & 255, (c >>> 8) & 255, (d >>> 8) & 255);
        int blue =
                dominantMaterialRange(
                        (a >>> 16) & 255, (b >>> 16) & 255, (c >>> 16) & 255, (d >>> 16) & 255, 65);
        return ARGB.color(emission, blue, green, averageChannel(a, b, c, d, 0));
    }

    private static int emissionByte(int pixel) {
        int value = pixel >>> 24;
        return value == 255 ? 0 : value;
    }

    private static int dominantReflectance(int a, int b, int c, int d) {
        if (votesBelow(a, b, c, d, 230) >= 2) return dominantMaterialRange(a, b, c, d, 230);
        // Metal IDs are categorical. Average reflectance only for dielectrics.
        int winner = a;
        int votes = 0;
        for (int i = 0; i < 4; ++i) {
            int candidate =
                    switch (i) {
                        case 0 -> a;
                        case 1 -> b;
                        case 2 -> c;
                        default -> d;
                    };
            if (candidate < 230) continue;
            int count =
                    (a == candidate ? 1 : 0)
                            + (b == candidate ? 1 : 0)
                            + (c == candidate ? 1 : 0)
                            + (d == candidate ? 1 : 0);
            if (count > votes) {
                votes = count;
                winner = candidate;
            }
        }
        return winner;
    }

    private static int dominantMaterialRange(int a, int b, int c, int d, int threshold) {
        int lowerVotes = votesBelow(a, b, c, d, threshold);
        boolean lower = lowerVotes >= 2;
        int sum =
                ((a < threshold) == lower ? a : 0)
                        + ((b < threshold) == lower ? b : 0)
                        + ((c < threshold) == lower ? c : 0)
                        + ((d < threshold) == lower ? d : 0);
        int count = lower ? lowerVotes : 4 - lowerVotes;
        return (sum + count / 2) / count;
    }

    private static int votesBelow(int a, int b, int c, int d, int threshold) {
        return (a < threshold ? 1 : 0)
                + (b < threshold ? 1 : 0)
                + (c < threshold ? 1 : 0)
                + (d < threshold ? 1 : 0);
    }

    private static int averageChannel(int a, int b, int c, int d, int shift) {
        return (((a >>> shift) & 255)
                        + ((b >>> shift) & 255)
                        + ((c >>> shift) & 255)
                        + ((d >>> shift) & 255)
                        + 2)
                / 4;
    }

    public static NativeImage[] getMipmapLevelsImages(NativeImage[] originals, int mipmap) {
        if (mipmap + 1 <= originals.length) {
            return originals;
        } else {
            NativeImage[] nativeImages = new NativeImage[mipmap + 1];
            nativeImages[0] = originals[0];
            boolean bl = hasAlpha(nativeImages[0]);

            for (int i = 1; i <= mipmap; i++) {
                if (i < originals.length) {
                    nativeImages[i] = originals[i];
                } else {
                    NativeImage nativeImage = nativeImages[i - 1];
                    NativeImage nativeImage2 =
                            new NativeImage(
                                    nativeImage.getWidth() >> 1,
                                    nativeImage.getHeight() >> 1,
                                    false);
                    int j = nativeImage2.getWidth();
                    int k = nativeImage2.getHeight();

                    for (int l = 0; l < j; l++) {
                        for (int m = 0; m < k; m++) {
                            nativeImage2.setPixelRGBA(
                                    l,
                                    m,
                                    blend(
                                            nativeImage.getPixelRGBA(l * 2, m * 2),
                                            nativeImage.getPixelRGBA(l * 2 + 1, m * 2),
                                            nativeImage.getPixelRGBA(l * 2, m * 2 + 1),
                                            nativeImage.getPixelRGBA(l * 2 + 1, m * 2 + 1),
                                            bl));
                        }
                    }

                    nativeImages[i] = nativeImage2;
                }
            }

            return nativeImages;
        }
    }

    public static boolean hasAlpha(NativeImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                if (ARGB.alpha(image.getPixelRGBA(i, j)) == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int blend(int one, int two, int three, int four, boolean checkAlpha) {
        float a1 = getColorFraction(one >> 24),
                r1 = getColorFraction(one >> 16),
                g1 = getColorFraction(one >> 8),
                b1 = getColorFraction(one >> 0);
        float a2 = getColorFraction(two >> 24),
                r2 = getColorFraction(two >> 16),
                g2 = getColorFraction(two >> 8),
                b2 = getColorFraction(two >> 0);
        float a3 = getColorFraction(three >> 24),
                r3 = getColorFraction(three >> 16),
                g3 = getColorFraction(three >> 8),
                b3 = getColorFraction(three >> 0);
        float a4 = getColorFraction(four >> 24),
                r4 = getColorFraction(four >> 16),
                g4 = getColorFraction(four >> 8),
                b4 = getColorFraction(four >> 0);

        float totalAlpha = a1 + a2 + a3 + a4;
        float outA, outR, outG, outB;

        if (totalAlpha > 0) {
            outA = totalAlpha / 4.0F;

            outR = (r1 * a1 + r2 * a2 + r3 * a3 + r4 * a4) / totalAlpha;
            outG = (g1 * a1 + g2 * a2 + g3 * a3 + g4 * a4) / totalAlpha;
            outB = (b1 * a1 + b2 * a2 + b3 * a3 + b4 * a4) / totalAlpha;
        } else {
            return 0;
        }

        double gamma = 0.45454545454545453;
        int resA = (int) (Math.pow(outA, gamma) * 255.0);
        int resR = (int) (Math.pow(outR, gamma) * 255.0);
        int resG = (int) (Math.pow(outG, gamma) * 255.0);
        int resB = (int) (Math.pow(outB, gamma) * 255.0);

        if (checkAlpha) {
            float alphaCoverage =
                    (ARGB.alpha(one) + ARGB.alpha(two) + ARGB.alpha(three) + ARGB.alpha(four))
                            / (4.0F * 255.0F);
            resA = alphaCoverage >= CUTOUT_ALPHA_COVERAGE_THRESHOLD ? 255 : 0;
        }

        return ARGB.color(resA, resR, resG, resB);
    }

    public static int getColorComponent(int one, int two, int three, int four, int bits) {
        float f = getColorFraction(one >> bits);
        float g = getColorFraction(two >> bits);
        float h = getColorFraction(three >> bits);
        float i = getColorFraction(four >> bits);
        float j = (float) ((float) Math.pow((f + g + h + i) * 0.25, 0.45454545454545453));
        return (int) (j * 255.0);
    }

    public static float getColorFraction(int value) {
        return COLOR_FRACTIONS[value & 0xFF];
    }
}
