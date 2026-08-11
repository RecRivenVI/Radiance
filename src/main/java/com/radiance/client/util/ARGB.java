package com.radiance.client.util;

import net.minecraft.util.FastColor;

public final class ARGB {

    private ARGB() {
    }

    public static int alpha(int color) {
        return FastColor.ARGB32.alpha(color);
    }

    public static int red(int color) {
        return FastColor.ARGB32.red(color);
    }

    public static int green(int color) {
        return FastColor.ARGB32.green(color);
    }

    public static int blue(int color) {
        return FastColor.ARGB32.blue(color);
    }

    public static float alphaFloat(int color) {
        return alpha(color) / 255.0F;
    }

    public static float redFloat(int color) {
        return red(color) / 255.0F;
    }

    public static float greenFloat(int color) {
        return green(color) / 255.0F;
    }

    public static float blueFloat(int color) {
        return blue(color) / 255.0F;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return FastColor.ARGB32.color(alpha, red, green, blue);
    }

    public static int color(int alpha, int rgb) {
        return FastColor.ARGB32.color(alpha, rgb);
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return FastColor.ARGB32.colorFromFloat(alpha, red, green, blue);
    }

    public static int multiply(int first, int second) {
        return FastColor.ARGB32.multiply(first, second);
    }
}
