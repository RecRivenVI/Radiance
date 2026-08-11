package com.radiance.render.contract;

/** Meaning of source vertex fields; source adapters own Minecraft and mod APIs. */
public final class SurfaceMaterial {
    private SurfaceMaterial() {}

    public enum ColorSemantics {
        MULTIPLY,
        SURFACE_MIX
    }

    public enum NormalSemantics {
        GEOMETRIC_BRDF,
        VERTEX_BRDF
    }

    /** Immutable meaning of producer vertex fields for one captured material. */
    public record MaterialPolicy(
            int alphaMode,
            ColorSemantics colorSemantics,
            boolean allowRigidCapture,
            boolean allowTransmissionOverride,
            boolean allowVertexEmissionFloor,
            NormalSemantics normalSemantics) {
        public MaterialPolicy {
            java.util.Objects.requireNonNull(colorSemantics);
            java.util.Objects.requireNonNull(normalSemantics);
        }
    }

    public static MaterialPolicy ordinaryPolicy(int alphaMode) {
        return new MaterialPolicy(
                alphaMode,
                ColorSemantics.MULTIPLY,
                true,
                true,
                true,
                NormalSemantics.GEOMETRIC_BRDF);
    }
}
