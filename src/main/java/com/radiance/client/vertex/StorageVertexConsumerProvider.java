package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.compatibility.simulated.SimulatedVertexCompatibility;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class StorageVertexConsumerProvider extends MultiBufferSource.BufferSource {
    final java.util.List<RigidModelCapture.Draw> rigidDraws = new java.util.ArrayList<>();
    long rigidInstance;
    int rigidOrdinal;
    private boolean rigidEnabled;
    PartState partState;

    static final class PartState {
        final Map<RenderType, PartModelCapture.MaterialBatch> materials = new java.util.IdentityHashMap<>();
        final Map<Object, Integer> occurrences = new java.util.IdentityHashMap<>();
    }

    PartState partState() {
        if (partState == null) partState = new PartState();
        return partState;
    }

    public void enableRigidModels(int instance) {
        rigidEnabled = RigidModelCapture.ENABLED;
        rigidInstance = Integer.toUnsignedLong(instance);
    }
    public java.util.List<RigidModelCapture.Draw> takeRigidModels() {
        var result = new java.util.ArrayList<>(rigidDraws);
        rigidDraws.clear();
        return result;
    }

    protected final Map<RenderType, VertexConsumer> pending = new HashMap<>();
    protected final Map<RenderType, ByteBufferBuilder> allocated = new HashMap<>();

    private int size = 0;
    private final float defaultAlbedoEmission;

    public StorageVertexConsumerProvider(int size) {
        this(size, 0.0F);
    }

    public StorageVertexConsumerProvider(int size, float defaultAlbedoEmission) {
        super(new ByteBufferBuilder(0), new Object2ObjectLinkedOpenHashMap<>());
        this.size = size;
        this.defaultAlbedoEmission = defaultAlbedoEmission;
    }

    private static void assignBufferBuilder(
        Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> builderStorage,
        RenderType layer) {
        builderStorage.put(layer, new ByteBufferBuilder(layer.bufferSize()));
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = new ByteBufferBuilder(size);
            allocated.put(renderLayer, bufferAllocator);

            VertexFormat.Mode drawMode = renderLayer.mode();
            VertexFormat vertexFormat = renderLayer.format();

            if (drawMode == VertexFormat.Mode.QUADS) {
                vertexConsumer = SimulatedVertexCompatibility.createQuadConsumer(
                    bufferAllocator, renderLayer);
                if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                    pbrVertexConsumer.setDefaultAlbedoEmission(defaultAlbedoEmission);
                    if (rigidEnabled && vertexConsumer.getClass() == PBRVertexConsumer.class) {
                        pbrVertexConsumer.rigidOwner = this;
                        pbrVertexConsumer.rigidLayer = renderLayer;
                    }
                }
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public Map<RenderType, VertexConsumer> getLayers() {
        return this.pending;
    }

    @Override
    public void endLastBatch() {
        // Captured geometry is finalized by EntityProxy, never drawn by RenderType.
    }

    @Override
    public void endBatch() {
        // Captured geometry is finalized by EntityProxy, never drawn by RenderType.
    }

    @Override
    public void endBatch(RenderType renderType) {
        // Captured geometry is finalized by EntityProxy, never drawn by RenderType.
    }

    public void close() {
        rigidDraws.clear();
        partState = null;
        for (Map.Entry<RenderType, ByteBufferBuilder> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.pending.clear();
        this.sharedBuffer.close();
    }

}
