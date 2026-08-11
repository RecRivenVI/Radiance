package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.api.render.SourceHooks;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class StorageVertexConsumerProvider extends MultiBufferSource.BufferSource
        implements com.radiance.client.texture.FontAtlasLifecycle.BatchOwner {
    private Map<RenderType, net.minecraft.client.renderer.texture.AbstractTexture> fontPages;

    public record ProceduralSource(String family, String producer, boolean direct) {}

    private ProceduralSource proceduralSource;

    public StorageVertexConsumerProvider procedural(String family, Class<?> producer) {
        proceduralSource =
                new ProceduralSource(
                        family,
                        producer.getName(),
                        com.radiance.mixin_related.SourceCompatibility.producer(producer)
                                && com.radiance.mixin_related.SourceCompatibility.compatible(
                                        com.radiance.mixin_related.SourceCompatibility.Source
                                                .STATIC_STATE));
        return this;
    }

    public ProceduralSource proceduralSource() {
        return proceduralSource;
    }

    private boolean particleModels;
    double particleX, particleY, particleZ;

    public StorageVertexConsumerProvider particleModels(net.minecraft.world.phys.Vec3 camera) {
        particleModels = true;
        particleX = camera.x;
        particleY = camera.y;
        particleZ = camera.z;
        return this;
    }

    float particleEmission() {
        return defaultAlbedoEmission;
    }

    final java.util.List<RigidModelCapture.Draw> rigidDraws = new java.util.ArrayList<>();
    long rigidInstance;
    public Object sourceOwner;
    private boolean declaredLocalModels;

    enum Domain {
        WORLD,
        FIRST_PERSON,
        NESTED_PARTICLE
    }

    Domain instanceDomain = Domain.WORLD;
    int instanceCoordinates;

    public void firstPerson(Object source) {
        sourceOwner = source;
        instanceDomain = Domain.FIRST_PERSON;
        instanceCoordinates = com.radiance.render.contract.RenderAbi.COORDINATE_CAMERA;
        declaredParts(((net.minecraft.world.entity.Entity) source).getId(), "vanilla-first-person");
    }

    public void nestedParticle(Object source, int identity) {
        sourceOwner = source;
        instanceDomain = Domain.NESTED_PARTICLE;
        instanceCoordinates = com.radiance.render.contract.RenderAbi.COORDINATE_CAMERA_SHIFT;
        declaredParts(identity, "vanilla-nested-particle");
    }

    public com.radiance.api.render.RenderSpace.Placement modelPlacement;
    int rigidOrdinal;
    private boolean rigidEnabled;
    boolean partCaptureEnabled;
    boolean declaredPartsEnabled;
    String partProducer;
    long partHistoryDomain;
    boolean takeoverItemFrame;
    private boolean takeoverStaticState;

    public void takeoverItemFrame(boolean value) {
        takeoverItemFrame = value;
    }

    public void takeoverStaticState(boolean value) {
        takeoverStaticState = value;
    }

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

    public void declaredParts(int instance, String producer) {
        rigidEnabled = partCaptureEnabled = declaredPartsEnabled = true;
        rigidInstance = Integer.toUnsignedLong(instance);
        partProducer = producer;
        size = 0;
        takeoverStaticState = true;
    }

    public boolean rigidBakedEnabled() {
        return rigidEnabled;
    }

    public void originalModel(Runnable render) {
        boolean rigid = rigidEnabled, part = partCaptureEnabled, declared = declaredPartsEnabled;
        rigidEnabled = partCaptureEnabled = declaredPartsEnabled = false;
        try {
            render.run();
        } finally {
            rigidEnabled = rigid;
            partCaptureEnabled = part;
            declaredPartsEnabled = declared;
        }
    }

    /** Source scope only; geometry eligibility is separately owned by the actual model baker. */
    public void enablePartModels(Object owner, Object renderer, boolean blockEntity) {
        sourceOwner = owner;
        if (blockEntity) {
            // Declared third-party templates are opt-in at their real producer. Enabling this
            // consumer does not qualify arbitrary renderer/model callbacks for vanilla capture.
            long identity = PartModelCapture.blockOwner(owner);
            if (identity >= 0) {
                declaredLocalModels = true;
                rigidInstance = identity;
            }
        }
        if (!PartModelCapture.ENABLED
                || renderer == null
                || !renderer.getClass().getName().startsWith("net.minecraft.client.renderer.")
                || surfaceRole != com.radiance.render.contract.SourceEmission.Role.ORDINARY
                || (!blockEntity && !rigidEnabled)) return;
        if (!com.radiance.mixin_related.SourceCompatibility.producer(renderer.getClass())) return;
        if (renderer
                        instanceof
                        net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> living
                && !com.radiance.mixin_related.SourceCompatibility.producer(
                        living.getModel().getClass())) return;
        if (blockEntity) {
            long identity = PartModelCapture.blockOwner(owner);
            if (identity < 0) return;
            rigidInstance = identity;
            partHistoryDomain = 1L << 62;
        }
        partCaptureEnabled = true;
        partProducer = renderer.getClass().getName();
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
        if (renderLayer.name.startsWith("text")) {
            if (fontPages == null) fontPages = new java.util.HashMap<>();
            com.radiance.client.texture.FontAtlasLifecycle.capture(this, fontPages, renderLayer);
        }
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
                    pbrVertexConsumer.rigidLayer = renderLayer;
                    if (particleModels) {
                        pbrVertexConsumer.particleOwner = this;
                    }
                    if (pbrVertexConsumer.acceptsInheritedAlbedoEmission())
                        pbrVertexConsumer.setDefaultAlbedoEmission(defaultAlbedoEmission);
                    if ((rigidEnabled || partCaptureEnabled || declaredLocalModels)
                            && vertexConsumer.getClass() == PBRVertexConsumer.class
                            && pbrVertexConsumer.allowsRigidCapture()) {
                        pbrVertexConsumer.rigidOwner = this;
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
        VertexConsumer specialized = SourceHooks.quadConsumer(allocator, renderLayer);
        return specialized != null
                ? specialized
                : PBRVertexConsumer.dynamic(
                        allocator,
                        renderLayer,
                        Boolean.getBoolean("radiance.compactVertices")
                                && !com.radiance.client.render.RasterPreviewScope.active());
    }

    public java.util.Collection<Map.Entry<RenderType, VertexConsumer>> getLayerBatches() {
        if (completedBatches.isEmpty()) return pending.entrySet();
        var result = new java.util.ArrayList<>(completedBatches);
        result.addAll(pending.entrySet());
        return result;
    }

    protected com.radiance.render.contract.DrawStateSnapshot captureDrawState(RenderType layer) {
        if (takeoverStaticState) {
            var state = com.radiance.client.render.StaticDrawStates.capture(layer);
            if (state != null) return state;
        }
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

    public void radiance$discardFontBatches(
            java.util.Set<net.minecraft.client.renderer.texture.AbstractTexture> pages) {
        if (fontPages == null) return;
        var layers = new java.util.HashSet<RenderType>();
        fontPages.forEach(
                (layer, page) -> {
                    if (pages.contains(page)) layers.add(layer);
                });
        pending.keySet().removeAll(layers);
        completedBatches.removeIf(entry -> layers.contains(entry.getKey()));
        rigidDraws.removeIf(draw -> layers.contains(draw.layer()));
        if (layers.contains(lastLayer)) lastLayer = null;
        for (var layer : layers)
            com.radiance.client.texture.FontAtlasLifecycle.release(this, fontPages, layer);
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (fontPages != null)
            for (var layer : java.util.List.copyOf(fontPages.keySet()))
                com.radiance.client.texture.FontAtlasLifecycle.release(this, fontPages, layer);
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
