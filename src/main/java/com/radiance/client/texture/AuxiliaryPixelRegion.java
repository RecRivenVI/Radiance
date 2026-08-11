package com.radiance.client.texture;

import org.lwjgl.system.MemoryUtil;

/** Packs just the requested rectangle, preserving the auxiliary alignment's tile/channel rules. */
final class AuxiliaryPixelRegion {
    private AuxiliaryPixelRegion() {}

    static void copy(long destination, int width, int height, int channels,
        long source, int sourceWidth, int sourceHeight, int sourceChannels,
        int sourceX, int sourceY, int defaultPixel) {
        if (width <= 0 || height <= 0 || channels < 1 || channels > 4 || sourceX < 0 || sourceY < 0)
            throw new IllegalArgumentException("Invalid auxiliary upload rectangle");
        long bytes = Math.multiplyExact(Math.multiplyExact((long) width, height), channels);
        if (source == 0) {
            if (defaultPixel == 0) {
                MemoryUtil.memSet(destination, 0, bytes);
            } else {
                for (long pixel = 0; pixel < bytes; pixel += channels)
                    for (int c = 0; c < channels; c++)
                        MemoryUtil.memPutByte(destination + pixel + c, (byte) (defaultPixel >>> (8 * c)));
            }
            return;
        }
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceChannels < 1 || sourceChannels > 4)
            throw new IllegalArgumentException("Invalid auxiliary source image");
        if (channels == sourceChannels && (long) sourceX + width <= sourceWidth) {
            long rowBytes = (long) width * channels;
            for (int y = 0; y < height; y++) {
                long row = ((long) sourceY + y) % sourceHeight;
                MemoryUtil.memCopy(source + (row * sourceWidth + sourceX) * channels,
                    destination + y * rowBytes, rowBytes);
            }
            return;
        }
        int commonChannels = Math.min(sourceChannels, channels);
        for (int y = 0; y < height; y++) {
            long row = ((long) sourceY + y) % sourceHeight;
            for (int x = 0; x < width; x++) {
                long src = source + (row * sourceWidth + ((long) sourceX + x) % sourceWidth) * sourceChannels;
                long dst = destination + ((long) y * width + x) * channels;
                MemoryUtil.memCopy(src, dst, commonChannels);
                if (channels > commonChannels) MemoryUtil.memSet(dst + commonChannels, 0, channels - commonChannels);
            }
        }
    }
}
