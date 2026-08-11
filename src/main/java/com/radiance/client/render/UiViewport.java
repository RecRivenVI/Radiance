package com.radiance.client.render;

import org.joml.Matrix4f;

/** A physical pixel rectangle enclosing the content, with a small filter guard.
 * Its projection maps precisely that rectangle to the offscreen target, without changing the view. */
public record UiViewport(int x, int y, int width, int height, int windowWidth, int windowHeight) {
    public static UiViewport enclosing(float minX, float minY, float maxX, float maxY, int width, int height) {
        if (width <= 0 || height <= 0 || !Float.isFinite(minX + minY + maxX + maxY))
            throw new IllegalArgumentException("Invalid UI content viewport");
        if (minX > maxX || minY > maxY) throw new IllegalArgumentException("Reversed UI content bounds");
        // Translation must not squeeze an outgoing scene into a 1-pixel camera.
        // Keep its complete content rectangle; the GUI's existing window/scissor
        // clips the composite. Quantize SIZE, not both moving endpoints separately.
        int left = (int) Math.floor((minX + 1) * width / 2 - 8);
        int top = (int) Math.floor((1 - maxY) * height / 2 - 8);
        int contentWidth = Math.max(64, (int) Math.ceil(((maxX-minX) * width / 2 + 17) / 64) * 64);
        int contentHeight = Math.max(64, (int) Math.ceil(((maxY-minY) * height / 2 + 17) / 64) * 64);
        return new UiViewport(left, top, contentWidth, contentHeight, width, height);
    }
    public Matrix4f cropProjection(Matrix4f projection) {
        float cx = (2f * x + width) / windowWidth - 1;
        float cy = 1 - (2f * y + height) / windowHeight;
        return new Matrix4f().scaling((float) windowWidth / width, (float) windowHeight / height, 1)
            .translate(-cx, -cy, 0).mul(projection);
    }
    public float leftNdc() { return 2f * x / windowWidth - 1; }
    public float rightNdc() { return 2f * (x+width) / windowWidth - 1; }
    public float topNdc() { return 1 - 2f * y / windowHeight; }
    public float bottomNdc() { return 1 - 2f * (y+height) / windowHeight; }
}
