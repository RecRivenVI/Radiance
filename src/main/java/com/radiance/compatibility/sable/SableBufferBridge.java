package com.radiance.compatibility.sable;

import com.radiance.client.proxy.vulkan.BufferProxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/** CPU mirror and range-upload owner for Sable's persistent fancy-render buffers. */
public final class SableBufferBridge {

    private static final Map<Integer, byte[]> MIRRORS = new HashMap<>();

    private SableBufferBridge() {
    }

    public static synchronized int allocate() {
        int id = BufferProxy.allocatePersistentBuffer();
        if (id < 0) throw new IllegalStateException("Native Sable buffer allocation failed");
        MIRRORS.put(id, new byte[0]);
        return id;
    }

    public static synchronized void initialize(int id, int size, int usage) {
        if (size < 0) throw new IllegalArgumentException("Negative Sable buffer size");
        byte[] previous = require(id);
        byte[] replacement = new byte[size];
        System.arraycopy(previous, 0, replacement, 0, Math.min(previous.length, size));
        BufferProxy.initializeBuffer(id, size, usage);
        if (size > 0) uploadWhole(id, replacement);
        MIRRORS.put(id, replacement);
    }

    public static synchronized void uploadRange(int id, long pointer, int size, int offset) {
        byte[] mirror = require(id);
        if (pointer == 0 || size < 0 || offset < 0 || offset + size > mirror.length) {
            throw new IllegalArgumentException("Invalid Sable range upload");
        }
        if (size == 0) return;
        BufferProxy.queuePersistentUploadRange(pointer, size, id, offset);
        ByteBuffer source = MemoryUtil.memByteBuffer(pointer, size);
        source.get(mirror, offset, size);
    }

    public static synchronized void release(int id) {
        if (MIRRORS.containsKey(id)) {
            BufferProxy.releasePersistentBuffer(id);
            MIRRORS.remove(id);
        }
    }

    static synchronized byte[] snapshotForTest(int id) {
        return require(id).clone();
    }

    private static byte[] require(int id) {
        byte[] mirror = MIRRORS.get(id);
        if (mirror == null) throw new IllegalStateException("Unknown Sable buffer " + id);
        return mirror;
    }

    private static void uploadWhole(int id, byte[] data) {
        ByteBuffer nativeData = MemoryUtil.memAlloc(data.length);
        try {
            nativeData.put(data).flip();
            BufferProxy.queueUpload(MemoryUtil.memAddress(nativeData), id);
        } finally {
            MemoryUtil.memFree(nativeData);
        }
    }
}
