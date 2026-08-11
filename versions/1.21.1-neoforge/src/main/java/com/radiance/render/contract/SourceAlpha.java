package com.radiance.render.contract;

/** Discard stage is independent of the surviving surface's opacity model. */
public final class SourceAlpha {
    private SourceAlpha() {}

    public static int encode(int mode, int rule) {
        if (mode < 0
                || mode > RenderAbi.ALPHA_ORDERED_OPAQUE
                || rule < 0
                || rule > RenderAbi.SOURCE_ALPHA_RULE_MASK)
            throw new IllegalArgumentException("Invalid alpha descriptor");
        return mode | (rule << RenderAbi.SOURCE_ALPHA_RULE_SHIFT);
    }

    public static int mode(int descriptor) {
        return descriptor & RenderAbi.PACKED_ALPHA_MASK;
    }

    public static boolean compact(int descriptor) {
        return descriptor >= 0 && descriptor <= 255 && mode(descriptor) <= RenderAbi.PBR_ALPHA_MAX;
    }

    public static int withMode(int descriptor, int mode) {
        return (descriptor & ~RenderAbi.PACKED_ALPHA_MASK) | mode;
    }
}
