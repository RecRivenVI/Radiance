package com.radiance.compatibility.veil;

import com.radiance.client.proxy.vulkan.VertexArrayProxy;
import com.radiance.client.shader.CustomVertexLayout;
import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;

final class RadianceVertexArrayBuilder implements VertexArrayBuilder {

    private final RadianceVertexArray vertexArray;

    RadianceVertexArrayBuilder(VertexArray vertexArray) {
        this.vertexArray = (RadianceVertexArray) vertexArray;
    }

    @Override
    public VertexArray vertexArray() {
        return vertexArray;
    }

    @Override
    public VertexArrayBuilder defineVertexBuffer(int index, int buffer, int offset, int stride,
        int divisor) {
        if (divisor < 0 || divisor > 1) {
            throw new IllegalArgumentException("Vulkan bridge supports vertex divisors 0 or 1");
        }
        vertexArray.requireLive();
        VertexArrayProxy.defineVertexBuffer(vertexArray.nativeId(), index, buffer, offset, stride,
            divisor == 1);
        return this;
    }

    @Override
    public VertexArrayBuilder setVertexAttribute(int index, int bufferIndex, int size,
        DataType type, boolean normalized, int relativeOffset) {
        return defineAttribute(index, bufferIndex, size, type, normalized, false,
            relativeOffset);
    }

    @Override
    public VertexArrayBuilder setVertexIAttribute(int index, int bufferIndex, int size,
        DataType type, int relativeOffset) {
        return defineAttribute(index, bufferIndex, size, type, false, true, relativeOffset);
    }

    @Override
    public VertexArrayBuilder setVertexLAttribute(int index, int bufferIndex, int size,
        DataType type, int relativeOffset) {
        throw new UnsupportedOperationException("64-bit Veil vertex attributes are not supported");
    }

    private VertexArrayBuilder defineAttribute(int index, int bufferIndex, int size,
        DataType type, boolean normalized, boolean integer, int relativeOffset) {
        vertexArray.requireLive();
        VertexArrayProxy.defineAttribute(vertexArray.nativeId(), index, bufferIndex, size,
            componentType(type), normalized, integer, relativeOffset);
        return this;
    }

    @Override
    public VertexArrayBuilder removeVertexBuffer(int index) {
        vertexArray.requireLive();
        VertexArrayProxy.removeVertexBuffer(vertexArray.nativeId(), index);
        return this;
    }

    @Override
    public VertexArrayBuilder removeAttribute(int index) {
        vertexArray.requireLive();
        VertexArrayProxy.removeAttribute(vertexArray.nativeId(), index);
        return this;
    }

    @Override
    public VertexArrayBuilder clearVertexBuffers() {
        vertexArray.requireLive();
        VertexArrayProxy.clearVertexBuffers(vertexArray.nativeId());
        return this;
    }

    @Override
    public VertexArrayBuilder clearVertexAttributes() {
        vertexArray.requireLive();
        VertexArrayProxy.clearAttributes(vertexArray.nativeId());
        return this;
    }

    static int componentType(DataType type) {
        return switch (type) {
            case BYTE -> CustomVertexLayout.BYTE;
            case UNSIGNED_BYTE -> CustomVertexLayout.UNSIGNED_BYTE;
            case SHORT -> CustomVertexLayout.SHORT;
            case UNSIGNED_SHORT -> CustomVertexLayout.UNSIGNED_SHORT;
            case INT -> CustomVertexLayout.INT;
            case UNSIGNED_INT -> CustomVertexLayout.UNSIGNED_INT;
            case FLOAT -> CustomVertexLayout.FLOAT;
            default -> throw new UnsupportedOperationException(
                "Unsupported Veil vertex component type " + type);
        };
    }
}
