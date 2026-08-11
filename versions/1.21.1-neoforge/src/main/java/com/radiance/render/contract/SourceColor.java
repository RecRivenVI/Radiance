package com.radiance.render.contract;

/** Decodes the unsigned byte written by the source vertex format, without clamping its input. */
public final class SourceColor {
    private SourceColor() {}

    public static float channel(int value) {
        return (value & 255) / 255.0F;
    }

    public static float channel(float value) {
        return channel((int) (value * 255.0F));
    }
}
