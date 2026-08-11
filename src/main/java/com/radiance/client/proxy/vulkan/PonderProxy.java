package com.radiance.client.proxy.vulkan;

/** Compatibility facade; the actual service is reusable by non-Ponder UI scenes. */
public final class PonderProxy {
    private PonderProxy() {}
    public static void releaseScenes() { UiPathTracingProxy.releaseScenes(); }

    public static void trace(long scene, long triangles, int count, long matrices,
        int target, int width, int height, int frame, String[] groups, int[] vertexCounts, int[] materialFaces) {
        UiPathTracingProxy.trace(scene, triangles, count, matrices, target, width, height,
            frame, groups, vertexCounts, materialFaces);
    }
}
