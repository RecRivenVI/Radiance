package com.radiance.render.contract;

/** Producer intent; CPU policy bits never become surface face or GPU vertex flags. */
public final class SourceEmission {
    private SourceEmission() {}

    public enum Role {
        ORDINARY,
        SURFACE_LAYER_OWNER,
        NAME_TAG,
        PRIORITY_OUTLINE
    }

    public enum Reason {
        MATERIAL,
        SOURCE_FULLBRIGHT,
        EYE_OVERLAY,
        NAME_TAG,
        PRIORITY_OUTLINE,
        LEGACY_PROGRAM
    }

    public record Policy(
            Reason reason, boolean forceUnit, boolean eyeOverlay, boolean excludedEyeBase) {
        public int bits() {
            return (forceUnit ? RenderAbi.SOURCE_EMISSION_FORCE : 0)
                    | (eyeOverlay ? RenderAbi.SOURCE_EMISSION_EYE : 0)
                    | (excludedEyeBase ? RenderAbi.SOURCE_EMISSION_NO_EYE_BASE : 0);
        }
    }

    public static int encode(int geometry, int policy) {
        if ((policy & ~RenderAbi.SOURCE_EMISSION_MASK) != 0)
            throw new IllegalArgumentException("Unknown source emission policy");
        return (geometry & 0xFFFFFF) | (policy << RenderAbi.SOURCE_EMISSION_SHIFT);
    }

    public static int decode(int geometry) {
        return (geometry >>> RenderAbi.SOURCE_EMISSION_SHIFT) & RenderAbi.SOURCE_EMISSION_MASK;
    }
}
