package com.radiance.api.render.internal;

/** Synchronous CPU descriptions; Native owns programs, attachments, descriptors and retirement. */
public final class ScreenEffectsNative {
    private ScreenEffectsNative() {}

    public static native long generation();

    public static native int registerProgram(String key, byte[] spirv, int inputs);

    public static native void releaseProgram(long generation, int handle);

    public static native boolean submit(long generation, int[] description, float[] parameters);
}
