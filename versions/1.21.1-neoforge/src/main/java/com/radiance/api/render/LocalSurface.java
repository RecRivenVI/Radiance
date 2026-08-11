package com.radiance.api.render;

import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import com.radiance.render.contract.RenderAbi;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Uniform source vertex attributes, independent of local geometry and global draw modulation. */
public record LocalSurface(
        int flags,
        int light,
        int overlay,
        UvTransform uv,
        float red,
        float green,
        float blue,
        float alpha) {
    public static final LocalSurface NONE =
            new LocalSurface(0, 0, 0, UvTransform.IDENTITY, 1, 1, 1, 1);

    public LocalSurface {
        Objects.requireNonNull(uv);
        if (!uv.finite()
                || !Float.isFinite(red)
                || !Float.isFinite(green)
                || !Float.isFinite(blue)
                || !Float.isFinite(alpha))
            throw new IllegalArgumentException("Non-finite local surface attributes");
        if ((flags
                        & ~(RenderAbi.APPEARANCE_COLOR_MULTIPLY
                                | RenderAbi.APPEARANCE_OVERRIDE_LIGHT
                                | RenderAbi.APPEARANCE_OVERRIDE_OVERLAY
                                | RenderAbi.APPEARANCE_UV_AFFINE))
                != 0) throw new IllegalArgumentException("Unsupported local surface attributes");
    }

    public static LocalSurface uniform(int light, int overlay, int color, UvTransform uv) {
        return new LocalSurface(
                RenderAbi.APPEARANCE_COLOR_MULTIPLY
                        | RenderAbi.APPEARANCE_OVERRIDE_LIGHT
                        | RenderAbi.APPEARANCE_OVERRIDE_OVERLAY
                        | RenderAbi.APPEARANCE_UV_AFFINE,
                light,
                overlay,
                uv,
                ((color >>> 16) & 255) / 255F,
                ((color >>> 8) & 255) / 255F,
                (color & 255) / 255F,
                ((color >>> 24) & 255) / 255F);
    }

    public void write(ByteBuffer data, int at) {
        data.putInt(at, flags).putInt(at + 4, light).putInt(at + 8, overlay).putInt(at + 12, 0);
        data.putFloat(at + 16, uv.uu())
                .putFloat(at + 20, uv.uv())
                .putFloat(at + 24, uv.uOffset())
                .putFloat(at + 28, uv.vu())
                .putFloat(at + 32, uv.vv())
                .putFloat(at + 36, uv.vOffset())
                .putFloat(at + 40, red)
                .putFloat(at + 44, green)
                .putFloat(at + 48, blue)
                .putFloat(at + 52, alpha);
    }

    public boolean matches(ByteBuffer data, int at) {
        return flags == data.getInt(at)
                && light == data.getInt(at + 4)
                && overlay == data.getInt(at + 8)
                && bits(uv.uu()) == data.getInt(at + 16)
                && bits(uv.uv()) == data.getInt(at + 20)
                && bits(uv.uOffset()) == data.getInt(at + 24)
                && bits(uv.vu()) == data.getInt(at + 28)
                && bits(uv.vv()) == data.getInt(at + 32)
                && bits(uv.vOffset()) == data.getInt(at + 36)
                && bits(red) == data.getInt(at + 40)
                && bits(green) == data.getInt(at + 44)
                && bits(blue) == data.getInt(at + 48)
                && bits(alpha) == data.getInt(at + 52);
    }

    private static int bits(float value) {
        return Float.floatToRawIntBits(value);
    }
}
