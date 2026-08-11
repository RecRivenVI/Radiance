package com.radiance.client.proxy.world;

/**
 * Internal JNI transport for canonical shared-model/BLAS instance scenes. CPU inputs are copied
 * synchronously.
 */
public final class NativeInstancingProxy {
    private NativeInstancingProxy() {}

    public static native boolean isSupported();

    public static native long createEngine();

    public static native int createModel(long engine, int meshCount);

    public static native void uploadModelMesh(
            long engine,
            int model,
            int meshIndex,
            long vertices,
            int vertexCount,
            long indices,
            int indexCount,
            int textureId,
            int alphaMode,
            int materialFlags,
            String materialKey);

    public static native void finishModel(long engine, int model);

    /** The model must have no instances left; in-flight frames keep its resources until retired. */
    public static native void deleteModel(long engine, int model);

    public static native void updateInstance(
            long engine,
            long instance,
            int model,
            int inputVersion,
            int bias,
            boolean visible,
            long instanceData,
            int instanceDataSize,
            long embeddingPose,
            long embeddingNormal,
            boolean embedded);

    public static native void deleteInstance(long engine, long instance);

    public static native void bindSpace(long engine, long instance, long space);

    public static native void beginFrame(
            long engine, int originX, int originY, int originZ, double renderTicks);

    public static native void renderCrumbling(
            long engine,
            long instances,
            long blockPositions,
            long progress,
            long textures,
            int count);

    public static native void deleteEngine(long engine);
}
