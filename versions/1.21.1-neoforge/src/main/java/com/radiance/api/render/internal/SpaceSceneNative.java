package com.radiance.api.render.internal;

public final class SpaceSceneNative {
    private SpaceSceneNative() {}

    public static native void put(long id, long parent, double[] pose, float[] normal);

    public static native void remove(long id);

    public static native void motion(
            long id,
            long parent,
            double[] before,
            double[] after,
            double start,
            double end,
            double time);

    public static native void sample(long id, double time);

    public static native void lighting(long id, int scene, float sky, double[] matrix);

    public static native void sectionColour(long id, float[] plane, float[] target);
}
