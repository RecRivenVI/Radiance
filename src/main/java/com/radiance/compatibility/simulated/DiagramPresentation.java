package com.radiance.compatibility.simulated;

import java.nio.ByteBuffer;

/** Coordinate/coverage contract shared by the actual diagram entry and its behavior tests. */
public final class DiagramPresentation {
    private DiagramPresentation() {}

    public static int pixels(int logical, int windowPixels, int guiPixels) {
        if (logical <= 0 || windowPixels <= 0 || guiPixels <= 0)
            throw new IllegalArgumentException("Diagram dimensions must be positive");
        return Math.max(1, Math.toIntExact(Math.round((double) logical * windowPixels / guiPixels)));
    }

    /** Input rows follow GL readback (bottom up); test every covered physical sample. */
    public static boolean occupied(ByteBuffer rgba, int width, int height, int logicalWidth,
        int logicalHeight, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return false;
        int x0 = Math.max(0, (int) Math.floor((double) x * width / logicalWidth));
        int y0 = Math.max(0, (int) Math.floor((double) y * height / logicalHeight));
        int x1 = Math.min(width, (int) Math.ceil(((double) x + w) * width / logicalWidth));
        int y1 = Math.min(height, (int) Math.ceil(((double) y + h) * height / logicalHeight));
        for (int row = y0; row < y1; row++)
            for (int col = x0; col < x1; col++)
                if (rgba.get((row * width + col) * 4 + 3) != 0) return true;
        return false;
    }
}
