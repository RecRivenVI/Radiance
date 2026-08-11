package com.radiance.compatibility.voxy;

/** Synchronous ownership transfer: native copies every borrowed buffer before returning. */
public final class VoxyNative {
    private VoxyNative() {}

    public static native void begin(long epoch);

    public static native void end(long epoch);

    public static native void models(
            long epoch, long records, int count, long colours, int colourCount);

    public static native boolean section(
            long epoch, long key, long revision, long quads, int quadCount);

    public static native void select(long epoch, long[] keys, long[] roots);

    public static native void retain(long epoch, long[] keys);

    public static native long[] counters(long epoch);

    public static native void nearEmpty(int slot, int x, int y, int z);
}
