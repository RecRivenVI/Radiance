package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** A BufferSource-compatible router for vanilla renderers that require the concrete type. */
public final class StorageRoutingBufferSource extends MultiBufferSource.BufferSource
    implements AutoCloseable {

    private final Function<RenderType, VertexConsumer> route;

    public StorageRoutingBufferSource(
        Function<RenderType, StorageVertexConsumerProvider> route) {
        this(renderType -> route.apply(renderType).getBuffer(renderType), true);
    }

    private StorageRoutingBufferSource(Function<RenderType, VertexConsumer> route,
        boolean directRoute) {
        super(new ByteBufferBuilder(0), new Object2ObjectLinkedOpenHashMap<>());
        this.route = route;
    }

    public static StorageRoutingBufferSource ofConsumers(
        Function<RenderType, VertexConsumer> route) {
        return new StorageRoutingBufferSource(route, true);
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return route.apply(renderType);
    }

    @Override
    public void endLastBatch() {
    }

    @Override
    public void endBatch() {
    }

    @Override
    public void endBatch(RenderType renderType) {
    }

    @Override
    public void close() {
        this.sharedBuffer.close();
    }
}
