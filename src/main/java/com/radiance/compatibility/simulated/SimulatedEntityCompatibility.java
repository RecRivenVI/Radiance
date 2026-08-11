package com.radiance.compatibility.simulated;

import net.minecraft.client.renderer.RenderType;

/**
 * Simulated entity-layer rules for the 1.21.1 renderer API.
 */
public final class SimulatedEntityCompatibility {

    private static final String ROPE_LAYER = "simulated:rope";

    private SimulatedEntityCompatibility() {
    }

    public static boolean isCameraRelativeLayer(RenderType renderType) {
        return ROPE_LAYER.equals(renderType.name);
    }

    public static int cameraRelativeIdentity(Object owner) {
        return System.identityHashCode(owner) ^ ROPE_LAYER.hashCode();
    }
}
