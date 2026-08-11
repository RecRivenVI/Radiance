package com.radiance.api.render.internal;

public final class RigidSceneNative {
    private RigidSceneNative() {}

    public static native void releaseGeometry(long identity);

    public static native long[] submit(
            int version,
            int stride,
            int bytes,
            long changes,
            int changeCount,
            long groups,
            int groupCount,
            int bindingVersion,
            int bindingBytes,
            long bindings,
            int bindingCount,
            long selected,
            int selectedCount,
            long removed,
            int removedCount);
}
