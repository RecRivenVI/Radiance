package com.radiance.api.render.internal;

/** Fixed compute ABI; all CPU inputs are copied before returning. */
public final class ComputedSurfaceNative {
    private ComputedSurfaceNative() {}

    public static native long program(long generation, byte[] spirv);

    public static native long data(long generation, long address, int bytes);

    public static native void release(long generation, long handle);

    public static native boolean submit(
            long world,
            long frame,
            long generation,
            long stage,
            int stageKind,
            long program,
            long source,
            long contacts,
            long parameters,
            int parameterBytes,
            int noise,
            int field,
            int work,
            int vertices,
            int indices,
            int identity,
            double x,
            double y,
            double z);
}
