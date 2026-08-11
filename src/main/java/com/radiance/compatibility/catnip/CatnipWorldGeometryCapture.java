package com.radiance.compatibility.catnip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.client.vertex.StorageVertexConsumerProvider;

import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;

/** Captures Catnip's original cuboids and textured faces without rebuilding their geometry. */
public class CatnipWorldGeometryCapture implements SuperRenderTypeBuffer, AutoCloseable {
    private final Object owner;
    private final String contentPrefix;
    // Preserve the original primitives and select emission at the capture boundary.
    private final StorageVertexConsumerProvider storage;
    private boolean closed;

    public CatnipWorldGeometryCapture(Object owner, String contentPrefix, float emission) {
        this.owner = owner;
        this.contentPrefix = contentPrefix;
        this.storage = new StorageVertexConsumerProvider(16384, emission);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return storage.getBuffer(type);
    }

    @Override
    public VertexConsumer getEarlyBuffer(RenderType type) {
        return getBuffer(type);
    }

    @Override
    public VertexConsumer getLateBuffer(RenderType type) {
        return getBuffer(type);
    }

    @Override
    public void draw() {
        // Submission is deferred until the complete outline has been captured.
    }

    @Override
    public void draw(RenderType type) {
        // Keep all original layers together until submit().
    }

    public void submit() {
        if (closed) return;
        closed = true;
        WorldMeshSink.submitCaptured(storage, owner, contentPrefix);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        storage.close();
    }
}

