package com.radiance.compatibility.veil;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.VertexArrayProxy;
import foundry.veil.api.client.render.vertex.VertexArray;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Veil VertexArray backed by native Vulkan buffers and an explicit input-layout object. */
public final class RadianceVertexArray extends VertexArray {

    private boolean live = true;

    public RadianceVertexArray() {
        super(VertexArrayProxy.allocate(), RadianceVertexArrayBuilder::new);
    }

    int nativeId() {
        return this.id;
    }

    void requireLive() {
        if (!live) throw new IllegalStateException("Veil VertexArray is closed");
    }

    @Override
    public int getOrCreateBuffer(int index) {
        requireLive();
        if (index < 0) throw new ArrayIndexOutOfBoundsException(index);
        if (!this.buffers.containsKey(index)) {
            int buffer = BufferProxy.allocatePersistentBuffer();
            if (buffer < 0) throw new IllegalStateException("Native buffer allocation failed");
            this.buffers.put(index, buffer);
        }
        return this.buffers.get(index);
    }

    public static void uploadBuffer(int buffer, ByteBuffer source) {
        if (buffer < 0 || source == null || !source.isDirect()) {
            throw new IllegalArgumentException("Veil buffer upload requires a live id and direct data");
        }
        ByteBuffer data = source.slice();
        BufferProxy.initializeBuffer(buffer, data.remaining(),
            com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits
                .VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        BufferProxy.queueUpload(MemoryUtil.memAddress(data), buffer);
    }

    @Override
    public void upload(MeshData mesh, DrawUsage usage) {
        upload(0, mesh, usage);
    }

    @Override
    public void upload(int start, MeshData mesh, DrawUsage usage) {
        requireLive();
        try (mesh) {
            MeshData.DrawState state = mesh.drawState();
            int vertexBuffer = getOrCreateBuffer(VERTEX_BUFFER);
            uploadBuffer(vertexBuffer, mesh.vertexBuffer());
            editFormat().applyFrom(VERTEX_BUFFER, vertexBuffer, start, state.format());
            if (mesh.indexBuffer() == null) uploadIndexBuffer(state);
            else uploadIndexBuffer(mesh.indexBuffer(), IndexType.fromBlaze3D(state.indexType()));
            this.indexCount = state.indexCount();
            this.indexType = IndexType.fromBlaze3D(state.indexType());
            this.drawMode = state.mode();
        }
    }

    @Override
    public void uploadIndexBuffer(MeshData.DrawState state) {
        requireLive();
        if (state.indexType() == VertexFormat.IndexType.SHORT) {
            int buffer = getOrCreateBuffer(ELEMENT_ARRAY_BUFFER);
            int bytes = Math.multiplyExact(state.indexCount(), Short.BYTES);
            BufferProxy.initializeBuffer(buffer, bytes,
                com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits
                    .VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
            BufferProxy.buildIndexBuffer(buffer, Constants.IndexTypes.getValue(state.indexType()),
                Constants.DrawModes.getValue(state.mode()), state.vertexCount(), state.indexCount());
            defineIndexBuffer(buffer, IndexType.SHORT, state.indexCount());
            return;
        }
        throw new UnsupportedOperationException("Sequential Veil INT indices are not implemented");
    }

    @Override
    public void uploadIndexBuffer(ByteBuffer source) {
        uploadIndexBuffer(source, IndexType.least(this.indexCount));
    }

    @Override
    public void uploadIndexBuffer(ByteBuffer source, IndexType type) {
        requireLive();
        ByteBuffer data = source.slice().order(source.order());
        int count = data.remaining() / type.getBytes();
        int buffer = getOrCreateBuffer(ELEMENT_ARRAY_BUFFER);
        if (type == IndexType.BYTE) {
            ByteBuffer converted = convertUnsignedByteIndices(data);
            try {
                uploadIndexData(buffer, converted);
            } finally {
                MemoryUtil.memFree(converted);
            }
            defineIndexBuffer(buffer, IndexType.SHORT, count);
            return;
        }
        uploadIndexData(buffer, data);
        defineIndexBuffer(buffer, type, count);
    }

    static ByteBuffer convertUnsignedByteIndices(ByteBuffer source) {
        ByteBuffer data = source.slice();
        ByteBuffer converted = MemoryUtil.memAlloc(data.remaining() * Short.BYTES);
        while (data.hasRemaining()) {
            converted.putShort((short) Byte.toUnsignedInt(data.get()));
        }
        return converted.flip();
    }

    private static void uploadIndexData(int buffer, ByteBuffer data) {
        BufferProxy.initializeBuffer(buffer, data.remaining(),
            com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits
                .VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        BufferProxy.queueUpload(MemoryUtil.memAddress(data), buffer);
    }

    private void defineIndexBuffer(int buffer, IndexType type, int count) {
        this.indexBuffer = null;
        this.indexType = type;
        this.indexCount = count;
        int nativeType = type == IndexType.SHORT ? 0 : 1;
        VertexArrayProxy.defineIndexBuffer(this.id, buffer, nativeType, count);
    }

    @Override
    public void bind() {
        requireLive();
        VeilVertexArrayBridge.bind(this);
    }

    @Override
    public void draw() {
        VeilVertexArrayBridge.draw(this, 1);
    }

    @Override
    public void drawInstanced(int instances) {
        VeilVertexArrayBridge.draw(this, instances);
    }

    @Override
    public void drawIndirect(long offset, int drawCount, int stride) {
        throw new IllegalStateException(
            "Veil indirect draw requires an explicitly uploaded indirect buffer");
    }

    @Override
    public void free() {
        if (!live) return;
        live = false;
        VeilVertexArrayBridge.unbindIfCurrent(this);
        int[] owned = this.buffers.values().toIntArray();
        this.buffers.clear();
        VertexArrayProxy.release(this.id);
        for (int buffer : owned) BufferProxy.releasePersistentBuffer(buffer);
    }
}
