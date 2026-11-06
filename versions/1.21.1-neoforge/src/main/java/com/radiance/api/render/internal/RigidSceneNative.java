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

    /** Replaces one retained selection group with {@code count} accepted identities; 0 ends it. */
    public static native void retain(long group, long ids, int count);

    public static native void clearRetained();

    /** Selects every retained member that still has an accepted version; returns that count. */
    public static native int selectRetained();
}
