package com.radiance.compatibility.voxy;

import com.radiance.client.texture.TextureTasks;
import org.lwjgl.system.MemoryUtil;

/** A bounded CPU mirror of the block atlas, captured inside the owned texture-upload task. */
public final class VoxyAtlasSnapshot {
    private static TextureTasks.Owner texture;
    private static int width;
    private static int height;
    private static int[] pixels;

    private VoxyAtlasSnapshot() {}

    public record Snapshot(int width, int height, int[] pixels) {}

    public static synchronized void begin(TextureTasks.Owner owner, int w, int h) {
        if (!VoxyPresence.PRESENT) return;
        if (owner == null)
            throw new IllegalArgumentException("Voxy block atlas has no texture owner");
        if (w <= 0 || h <= 0 || (long) w * h > 64L * 1024 * 1024)
            throw new IllegalArgumentException("Voxy block atlas exceeds CPU snapshot budget");
        texture = owner;
        width = w;
        height = h;
        pixels = new int[Math.multiplyExact(w, h)];
    }

    public static synchronized void upload(
            TextureTasks.Owner owner,
            int level,
            long address,
            int sourceWidth,
            int sourceHeight,
            int channels,
            int skipX,
            int skipY,
            int x,
            int y,
            int w,
            int h) {
        if (pixels == null || !texture.equals(owner) || level != 0) return;
        if (channels != 4
                || address == 0
                || skipX < 0
                || skipY < 0
                || x < 0
                || y < 0
                || w < 0
                || h < 0
                || (long) skipX + w > sourceWidth
                || (long) skipY + h > sourceHeight
                || (long) x + w > width
                || (long) y + h > height)
            throw new IllegalArgumentException("Invalid Voxy block atlas upload region");
        for (int row = 0; row < h; row++) {
            long src = address + ((long) (skipY + row) * sourceWidth + skipX) * 4;
            for (int column = 0; column < w; column++)
                pixels[(y + row) * width + x + column] = MemoryUtil.memGetInt(src + column * 4L);
        }
    }

    public static synchronized Snapshot snapshot() {
        if (pixels == null) throw new IllegalStateException("Voxy block atlas not uploaded");
        return new Snapshot(width, height, pixels.clone());
    }
}
