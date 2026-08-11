package com.radiance.compatibility.sable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.mixins.compatibility.sable.SableSubLevelVertexConsumerAccessor;

public final class SableVertexConsumerCompat {

    private static final String SABLE_SUB_LEVEL_VERTEX_CONSUMER =
        "dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer";
    private static final String UNIVERSAL_MESH_EMITTER =
        "net.createmod.catnip.impl.client.render.model.UniversalMeshEmitter";

    private SableVertexConsumerCompat() {
    }

    public static VertexConsumer bulkDataConsumer(VertexConsumer consumer) {
        if (!SABLE_SUB_LEVEL_VERTEX_CONSUMER.equals(consumer.getClass().getName())) {
            return consumer;
        }

        if (!(consumer instanceof SableSubLevelVertexConsumerAccessor accessor)) {
            return consumer;
        }

        VertexConsumer delegate = accessor.radiance$getDelegate();
        if (delegate != null && UNIVERSAL_MESH_EMITTER.equals(delegate.getClass().getName())) {
            // Sable's wrapper expands bulk vertices through addVertex(), while this Ponder
            // emitter deliberately supports only the bulk path. Bypass just this incompatible
            // wrapper/emitter pair and leave every normal Sable consumer unchanged.
            return delegate;
        }
        return consumer;
    }
}
