package com.radiance.client.vertex;

/**
 * Scoped material information which is known by a high-level renderer but is
 * not encoded in Minecraft's entity {@code RenderType}.
 */
public final class PBRMaterialContext {

    private static final ThreadLocal<Integer> BLOCK_TRANSMISSION_DEPTH =
        ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> ENTITY_TRANSMISSION_DEPTH =
        ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Float> ALBEDO_EMISSION =
        ThreadLocal.withInitial(() -> 0.0F);
    private static final Scope NO_OP = () -> { };

    private PBRMaterialContext() {
    }

    public static Scope pushBlockTransmission(boolean enabled) {
        return pushDepth(BLOCK_TRANSMISSION_DEPTH, enabled);
    }

    public static Scope pushEntityTransmission(boolean enabled) {
        return pushDepth(ENTITY_TRANSMISSION_DEPTH, enabled);
    }

    /**
     * Adds a high-level semantic emission floor to vertices produced inside the scope.
     * Nested scopes keep the strongest requested emission so a child renderer cannot
     * accidentally weaken an already-emissive parent material.
     */
    public static Scope pushAlbedoEmission(float emission) {
        float clampedEmission = Math.max(0.0F, Math.min(1.0F, emission));
        if (clampedEmission == 0.0F) {
            return NO_OP;
        }
        float previousEmission = ALBEDO_EMISSION.get();
        ALBEDO_EMISSION.set(Math.max(previousEmission, clampedEmission));
        return new Scope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (previousEmission == 0.0F) {
                    ALBEDO_EMISSION.remove();
                } else {
                    ALBEDO_EMISSION.set(previousEmission);
                }
            }
        };
    }

    private static Scope pushDepth(ThreadLocal<Integer> depth, boolean enabled) {
        if (!enabled) {
            return NO_OP;
        }
        int previousDepth = depth.get();
        depth.set(previousDepth + 1);
        return new Scope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (previousDepth == 0) {
                    depth.remove();
                } else {
                    depth.set(previousDepth);
                }
            }
        };
    }

    static boolean blockTransmissionActive() {
        return BLOCK_TRANSMISSION_DEPTH.get() > 0;
    }

    static boolean entityTransmissionActive() {
        return ENTITY_TRANSMISSION_DEPTH.get() > 0;
    }

    static float albedoEmission() {
        return ALBEDO_EMISSION.get();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
