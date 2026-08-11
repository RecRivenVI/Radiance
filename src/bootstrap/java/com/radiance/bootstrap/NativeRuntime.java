package com.radiance.bootstrap;

import java.nio.ByteBuffer;

/**
 * Stable JNI surface shared by the SERVICE bootstrap and the GAME module.
 */
public final class NativeRuntime {
    private NativeRuntime() {}

    public static native void initialize(String runtimeDirectory, String[] glfwCandidates, long window);

    public static native void uploadTexture(int key, int width, int height, ByteBuffer rgba, boolean linear,
            boolean clampToEdge);

    public static native void renderFrame(ByteBuffer vertices, ByteBuffer batches, int batchCount,
            int canvasWidth, int canvasHeight, int backgroundAbgr, int framebufferWidth,
            int framebufferHeight, float opacity, boolean gameFrame, boolean paintBackground);

    public static native void handoff(long expectedWindow);

    public static native void bindGameNatives(Class<?> type, String[] names, String[] descriptors,
            String[] symbols);

    public static native long[] identities();

    public static native byte[] captureCanvas();

    public static native void releaseLoading();

    public static native void closeBeforeGame();
}
