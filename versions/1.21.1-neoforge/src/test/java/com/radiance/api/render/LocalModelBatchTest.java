package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class LocalModelBatchTest {
    @org.junit.jupiter.api.Test
    void changedRecordsAppendAtTheirCurrentPositionWithoutOverwritingTheTail() {
        int stride = com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES;
        var source = java.nio.ByteBuffer.allocateDirect(stride * 4);
        var packed = java.nio.ByteBuffer.allocateDirect(stride * 8);
        for (int i = 0; i < source.capacity(); i++) source.put(i, (byte) (i * 31 + i / stride));
        for (int i = 0; i < packed.capacity(); i++) packed.put(i, (byte) 0x5a);
        packed.limit(stride * 4);
        for (int i = 0; i < 4; i++) LocalModelBatch.appendRecord(source, i * stride, packed);
        assertEquals(stride * 4, packed.position());
        for (int i = 0; i < source.capacity(); i++) assertEquals(source.get(i), packed.get(i));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> LocalModelBatch.appendRecord(source, 0, packed));
        assertEquals(stride * 4, packed.position());
        packed.clear();
        for (int i = source.capacity(); i < packed.capacity(); i++)
            assertEquals((byte) 0x5a, packed.get(i));
    }

    private static void add(LocalModelBatch batch, long id, double x) {
        batch.add(
                7,
                id,
                0,
                new TextureTasks.Owner(3, 1),
                ByteBuffer.allocateDirect(4 * 128),
                4,
                x,
                2,
                3,
                1,
                new Matrix4f(),
                "default");
    }

    @Test
    void concurrentOpenBatchesOwnTheirStagingAndResetRewritesEveryRecord() {
        try (var first = new LocalModelBatch(2);
                var second = new LocalModelBatch(1)) {
            add(first, 10, 1);
            add(second, 20, 9);
            assertEquals(10, first.records().getLong(8));
            assertEquals(20, second.records().getLong(8));
            assertEquals(java.util.List.of("default"), second.groups);
            first.reset();
            assertTrue(first.groups.isEmpty());
            assertEquals(java.util.List.of("default"), second.groups);
            add(first, 30, 4);
            assertEquals(30, first.records().getLong(8));
            assertEquals(4, first.records().getDouble(24));
            assertEquals(20, second.records().getLong(8));
            assertEquals(2 * LocalModelBatch.RECORD_BYTES, first.records().remaining());
            assertEquals(ByteOrder.nativeOrder(), first.records().order());
        }
    }

    @Test
    void closedLeaseCannotMutateReusedStorageAndEmptyBatchHasAnEmptyView() {
        var closed = new LocalModelBatch(1);
        add(closed, 4, 8);
        closed.close();
        closed.close();
        try (var next = new LocalModelBatch(0)) {
            assertEquals(0, next.records().remaining());
            assertTrue(next.groups.isEmpty());
            assertThrows(IllegalStateException.class, closed::reset);
            assertThrows(IllegalStateException.class, closed::records);
            assertThrows(IllegalStateException.class, () -> add(closed, 5, 9));
        }
    }
}
