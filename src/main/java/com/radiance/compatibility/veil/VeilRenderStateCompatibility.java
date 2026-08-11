package com.radiance.compatibility.veil;

/**
 * Identifies Veil render-state shards whose OpenGL callbacks cannot run under Radiance.
 */
public final class VeilRenderStateCompatibility {

    private static final String DYNAMIC_BUFFER_SHARD =
        "foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferShard";

    private VeilRenderStateCompatibility() {
    }

    public static boolean shouldSkip(Object renderState) {
        return renderState.getClass().getName().equals(DYNAMIC_BUFFER_SHARD);
    }
}
