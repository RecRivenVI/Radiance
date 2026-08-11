package com.radiance.client.proxy.vulkan;

import java.util.concurrent.atomic.AtomicLong;

/** Low-level shared UI path-tracing service. Callers retain their own scene id and target texture. */
public final class UiPathTracingProxy {
    private static final AtomicLong NEXT_SCENE = new AtomicLong();

    private UiPathTracingProxy() {}

    public static long allocateScene() {
        long id = NEXT_SCENE.incrementAndGet();
        if (id <= 0) throw new IllegalStateException("UI path-tracing scene id space exhausted");
        return id;
    }

    public static native void releaseScenes();
    public static native void beginFrame(long[] visibleViews);
    public static native void endFrame(boolean commit);
    public static native void emptyView(long scene);

    public static native void trace(long scene, long triangles, int triangleCount, long matrices,
        int target, int physicalWidth, int physicalHeight, int frame,
        String[] groups, int[] vertexCounts, int[] materialFaces);
}
