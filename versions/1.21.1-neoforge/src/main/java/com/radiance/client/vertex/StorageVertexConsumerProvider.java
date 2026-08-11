package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.compatibility.simulated.SimulatedVertexCompatibility;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.LinkedHashMap;
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

    public boolean hasCapturedRigidModels() {
        return !rigidDraws.isEmpty();
    }

    protected final Map<RenderType, VertexConsumer> pending = new LinkedHashMap<>();
    protected final java.util.List<ByteBufferBuilder> allocated = new java.util.ArrayList<>();
    private final java.util.List<Map.Entry<RenderType, VertexConsumer>> completedBatches =
            new java.util.ArrayList<>();
    private java.util.IdentityHashMap<
                    VertexConsumer, com.radiance.render.contract.DrawStateSnapshot>
            drawStates;

    private int size = 0;
    private final float defaultAlbedoEmission;
    private com.radiance.render.contract.SourceEmission.Role surfaceRole =
            com.radiance.render.contract.SourceEmission.Role.ORDINARY;

    public StorageVertexConsumerProvider surfaceRole(
            com.radiance.render.contract.SourceEmission.Role role) {
        if (hasBatches())
            throw new IllegalStateException("Source role must be declared before geometry");
        surfaceRole = java.util.Objects.requireNonNull(role);
        return this;
    }

    public com.radiance.render.contract.SourceEmission.Role surfaceRole() {
        return surfaceRole;
    }

    private com.radiance.render.contract.LineContract.SourceKey lineSource =
            com.radiance.client.render.LineSources.ENTITY;
    private com.radiance.render.contract.LineContract.WidthPolicy lineWidthPolicy =
            com.radiance.render.contract.LineContract.WidthPolicy.ORDINARY;

    public StorageVertexConsumerProvider lineSource(
            com.radiance.render.contract.LineContract.SourceKey source,
            com.radiance.render.contract.LineContract.WidthPolicy widthPolicy) {
        var previousSource = lineSource;
        lineSource = source;
        lineWidthPolicy = widthPolicy;
        for (VertexConsumer consumer : pending.values()) {
            if (consumer instanceof CapturedLineVertexConsumer line) {
                if (line.source.equals(previousSource)) line.source = source;
                line.widthPolicy = widthPolicy;
            }
        }
        return this;
    }

    public void debugLines(com.radiance.render.contract.LineContract.SourceKey source) {
        lineWidthPolicy = com.radiance.render.contract.LineContract.WidthPolicy.DEBUG;
        if (lineSource.equals(com.radiance.client.render.LineSources.ENTITY)) lineSource = source;
        for (var entry : getLayerBatches()) {
            if (entry.getValue() instanceof CapturedLineVertexConsumer line) {
                if (line.source.equals(com.radiance.client.render.LineSources.ENTITY))
                    line.source = source;
                line.widthPolicy = lineWidthPolicy;
                line.emission = 1;
            }
        }
    }

    public StorageVertexConsumerProvider(int size) {
        this(size, 0.0F);
    }

    public StorageVertexConsumerProvider(int size, float defaultAlbedoEmission) {
        // getBuffer owns the captured batches; the inherited fixed-buffer table stays empty.
        super(new ByteBufferBuilder(0), new Object2ObjectLinkedOpenHashMap<>(0));
        this.size = size;
        this.defaultAlbedoEmission = defaultAlbedoEmission;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        if (closed) throw new IllegalStateException("Captured provider is closed");
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer instanceof CapturedLineVertexConsumer line
                && (!line.source.equals(com.radiance.client.render.LineSources.current(lineSource))
                        || !renderLayer.canConsolidateConsecutiveGeometry())) {
            seal(renderLayer);
            vertexConsumer = null;
        }
        if (vertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = new ByteBufferBuilder(size);
            allocated.add(bufferAllocator);

            VertexFormat.Mode drawMode = renderLayer.mode();
            VertexFormat vertexFormat = renderLayer.format();

            if (drawMode == VertexFormat.Mode.QUADS) {
                vertexConsumer = createQuadConsumer(bufferAllocator, renderLayer);
                if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                    if (pbrVertexConsumer.acceptsInheritedAlbedoEmission())
                        pbrVertexConsumer.setDefaultAlbedoEmission(defaultAlbedoEmission);
                    if (rigidEnabled
                            && vertexConsumer.getClass() == PBRVertexConsumer.class
                            && pbrVertexConsumer.allowsRigidCapture()) {
                        pbrVertexConsumer.rigidOwner = this;
                        pbrVertexConsumer.rigidLayer = renderLayer;
                    }
                }
            } else if (CapturedLineVertexConsumer.supports(drawMode, vertexFormat)) {
                var lines = new CapturedLineVertexConsumer(bufferAllocator, drawMode, vertexFormat);
                lines.source = com.radiance.client.render.LineSources.current(lineSource);
                lines.widthPolicy = lineWidthPolicy;
                lines.emission = defaultAlbedoEmission;
                vertexConsumer = lines;
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        lastLayer = renderLayer;
        return vertexConsumer;
    }

    private RenderType lastLayer;

    protected VertexConsumer createQuadConsumer(
            ByteBufferBuilder allocator, RenderType renderLayer) {
        return SimulatedVertexCompatibility.createQuadConsumer(allocator, renderLayer);
    }

    public java.util.Collection<Map.Entry<RenderType, VertexConsumer>> getLayerBatches() {
        if (completedBatches.isEmpty()) return pending.entrySet();
        var result = new java.util.ArrayList<>(completedBatches);
        result.addAll(pending.entrySet());
        return result;
    }

    protected com.radiance.render.contract.DrawStateSnapshot captureDrawState(RenderType layer) {
        return com.radiance.client.render.MaterialFaces.captureSnapshot(layer);
    }

    public com.radiance.render.contract.DrawStateSnapshot drawState(VertexConsumer consumer) {
        return drawStates == null ? null : drawStates.get(consumer);
    }

    private void seal(RenderType layer) {
        VertexConsumer consumer = pending.get(layer);
        if (consumer == null) return;
        // Capture before moving ownership: failed callbacks leave the live batch closable.
        var state = captureDrawState(layer);
        if (drawStates == null) drawStates = new java.util.IdentityHashMap<>();
        drawStates.put(consumer, state);
        completedBatches.add(Map.entry(layer, consumer));
        pending.remove(layer);
        if (lastLayer == layer) lastLayer = null;
    }

    public boolean hasBatches() {
        return !pending.isEmpty() || !completedBatches.isEmpty();
    }

    @Override
    public void endLastBatch() {
        if (lastLayer != null) seal(lastLayer);
    }

    @Override
    public void endBatch() {
        for (var layer : java.util.List.copyOf(pending.keySet())) seal(layer);
    }

    @Override
    public void endBatch(RenderType renderType) {
        seal(renderType);
    }

    private boolean closed;

    public void close() {
        if (closed) return;
        closed = true;
        rigidDraws.clear();
        partState = null;
        for (ByteBufferBuilder allocation : allocated) {
            allocation.close();
        }
        this.pending.clear();
        completedBatches.clear();
        allocated.clear();
        if (drawStates != null) drawStates.clear();
        lastLayer = null;
        this.sharedBuffer.close();
    }
}
