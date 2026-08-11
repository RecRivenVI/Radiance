package com.radiance.adapter.vanilla;

import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.Camera;

/** A particle may own model/instance requests rather than a billboard vertex stream. */
public final class VanillaNestedParticleRequests {
    private VanillaNestedParticleRequests() {}

    public interface Source {
        boolean radiance$nested(StorageVertexConsumerProvider output, Camera camera, float partial);
    }
}
