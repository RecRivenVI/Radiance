package com.radiance.render.contract;

/** Scalar state at a source draw boundary, independent of mutable renderer globals. */
public record DrawStateSnapshot(
        int faces,
        float lineWidth,
        float red,
        float green,
        float blue,
        float alpha,
        UvTransform uvTransform) {
    public static final DrawStateSnapshot IDENTITY = new DrawStateSnapshot(0, 1, 1, 1, 1, 1);

    public DrawStateSnapshot(
            int faces, float lineWidth, float red, float green, float blue, float alpha) {
        this(faces, lineWidth, red, green, blue, alpha, UvTransform.IDENTITY);
    }

    public DrawStateSnapshot {
        java.util.Objects.requireNonNull(uvTransform);
    }

    public DrawStateSnapshot withoutUvTransform() {
        return uvTransform.equals(UvTransform.IDENTITY)
                ? this
                : new DrawStateSnapshot(faces, lineWidth, red, green, blue, alpha);
    }

    /** The source shader evaluates TextureMat * vec4(UV0, 0, 1), without a perspective divide. */
    public record UvTransform(
            float uu, float uv, float uOffset, float vu, float vv, float vOffset) {
        public static final UvTransform IDENTITY = new UvTransform(1, 0, 0, 0, 1, 0);

        public float u(float u, float v) {
            return uu * u + uv * v + uOffset;
        }

        public float v(float u, float v) {
            return vu * u + vv * v + vOffset;
        }

        public boolean finite() {
            return Float.isFinite(uu)
                    && Float.isFinite(uv)
                    && Float.isFinite(uOffset)
                    && Float.isFinite(vu)
                    && Float.isFinite(vv)
                    && Float.isFinite(vOffset);
        }
    }

    public void writeColour(java.nio.ByteBuffer output, int offset) {
        if (!finiteColour()) throw new IllegalArgumentException("Non-finite draw colour");
        output.putFloat(offset, red)
                .putFloat(offset + 4, green)
                .putFloat(offset + 8, blue)
                .putFloat(offset + 12, alpha);
    }

    public void writeUv(java.nio.ByteBuffer output, int at) {
        if (!uvTransform.finite())
            throw new IllegalArgumentException("Non-finite source UV transform");
        output.putFloat(at, uvTransform.uu())
                .putFloat(at + 4, uvTransform.uv())
                .putFloat(at + 8, uvTransform.uOffset())
                .putFloat(at + 12, uvTransform.vu())
                .putFloat(at + 16, uvTransform.vv())
                .putFloat(at + 20, uvTransform.vOffset());
    }

    public boolean finiteColour() {
        return Float.isFinite(red)
                && Float.isFinite(green)
                && Float.isFinite(blue)
                && Float.isFinite(alpha);
    }

    public boolean identityColour() {
        return red == 1 && green == 1 && blue == 1 && alpha == 1;
    }
}
