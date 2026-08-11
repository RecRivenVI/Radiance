package com.radiance.client.proxy.vulkan;

/**
 * GL framebuffer names backed by owned Vulkan attachments. A name is allocated before
 * storage (as in GL); completeness is computed from the live attachments, never fabricated.
 */
public final class FramebufferProxy {
    public static final int FRAMEBUFFER = 0x8D40;
    public static final int READ_FRAMEBUFFER = 0x8CA8;
    public static final int DRAW_FRAMEBUFFER = 0x8CA9;
    public static final int RENDERBUFFER = 0x8D41;

    private FramebufferProxy() {}

    public static native int createFramebuffer();
    public static native void deleteFramebuffer(int id);
    public static native void bindFramebuffer(int target, int id);
    public static native int boundFramebuffer(int target);
    public static native int[] dimensions(int target);
    public static native int checkStatus(int target);
    public static native void attachTexture(int target, int attachment, int texture, int level);
    public static native void attachRenderbuffer(int target, int attachment, int renderbuffer);
    public static native int createRenderbuffer();
    public static native void deleteRenderbuffer(int id);
    public static native void bindRenderbuffer(int id);
    public static native void renderbufferStorage(int internalFormat, int width, int height, int samples);
    public static native void drawBuffers(int[] attachments);
    public static native void readBuffer(int attachment);
    public static native void clear(float red, float green, float blue, float alpha,
        float depth, int stencil, int mask, int[] drawBuffers);
    /** Tightly packed pixels in OpenGL bottom-left row order. */
    public static native void readPixels(int x, int y, int width, int height,
        int format, int type, long destination);
    public static native void prepareAttachmentTexture(int texture, int levels, int width, int height,
        int internalFormat);
    public static native boolean configureMainTargetAliases(int colorTexture, int depthTexture,
        int width, int height);
    public static native boolean defaultStencilAvailable();
    public static native void blit(int srcX0, int srcY0, int srcX1, int srcY1,
        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);

    public static void requireTexture2D(int target, int level) {
        if (target != 0x0DE1 || level < 0) {
            throw new IllegalArgumentException("Framebuffer texture requires GL_TEXTURE_2D and a nonnegative mip: "
                + target + "/" + level);
        }
    }

    public static void requireRenderbuffer(int target) {
        if (target != RENDERBUFFER) {
            throw new IllegalArgumentException("Invalid renderbuffer target: " + target);
        }
    }
}
