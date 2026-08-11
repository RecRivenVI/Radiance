package com.radiance.compatibility.sable;

import dev.ryanhcode.sable.sublevel.render.staging.StagingBuffer;
import org.lwjgl.system.MemoryUtil;

/** Host allocation whose batches are copied synchronously into Vulkan staging storage. */
public final class RadianceSableStagingBuffer extends StagingBuffer {

    private long pointer;
    private int capacity;
    private int writeOffset;
    private int batchStart = -1;
    private int batchEnd;

    public RadianceSableStagingBuffer(long size) {
        capacity = Math.toIntExact(size);
        if (capacity <= 0) throw new IllegalArgumentException("Sable staging buffer is empty");
        pointer = MemoryUtil.nmemCalloc(1, capacity);
        if (pointer == 0) throw new OutOfMemoryError("Sable staging buffer allocation failed");
    }

    @Override
    public void updateFencedAreas() {
        if (batchStart >= 0) {
            throw new IllegalStateException("Sable staging batch was not copied");
        }
        writeOffset = 0;
    }

    @Override
    public long reserve(long requestedSize) {
        int size = Math.toIntExact(requestedSize);
        if (size < 0) throw new IllegalArgumentException("Negative Sable staging reservation");
        int aligned = Math.ceilDiv(writeOffset, Integer.BYTES) * Integer.BYTES;
        ensureCapacity(Math.addExact(aligned, size));
        if (batchStart < 0) batchStart = aligned;
        batchEnd = aligned + size;
        writeOffset = batchEnd;
        return pointer + aligned;
    }

    @Override
    public void copy(int destinationBuffer, long destinationOffset) {
        if (batchStart < 0) return;
        int size = batchEnd - batchStart;
        int offset = Math.toIntExact(destinationOffset);
        SableBufferBridge.uploadRange(destinationBuffer, pointer + batchStart, size,
            Math.addExact(offset, batchStart));
        batchStart = -1;
        batchEnd = 0;
        writeOffset = 0;
    }

    @Override
    public long getSize() {
        return capacity;
    }

    @Override
    public long getUsedSize() {
        return writeOffset;
    }

    @Override
    public void free() {
        long address = pointer;
        pointer = 0;
        capacity = 0;
        batchStart = -1;
        if (address != 0) MemoryUtil.nmemFree(address);
    }

    private void ensureCapacity(int required) {
        if (required <= capacity) return;
        int next = capacity;
        while (next < required) next = Math.multiplyExact(next, 2);
        long replacement = MemoryUtil.nmemRealloc(pointer, next);
        if (replacement == 0) throw new OutOfMemoryError("Sable staging resize failed");
        MemoryUtil.memSet(replacement + capacity, 0, next - capacity);
        pointer = replacement;
        capacity = next;
    }
}
