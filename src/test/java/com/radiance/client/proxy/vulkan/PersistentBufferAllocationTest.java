package com.radiance.client.proxy.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.junit.jupiter.api.Test;

class PersistentBufferAllocationTest {

    @Test
    void allocatesDistinctVertexAndIndexNames() {
        AtomicInteger next = new AtomicInteger(41);

        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.allocatePersistentBufferPair(
            next::getAndIncrement, ignored -> {
            });

        assertEquals(41, handle.vertexId);
        assertEquals(42, handle.indexId);
    }

    @Test
    void releasesTheFirstNameWhenTheSecondAllocationFails() {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> released = new ArrayList<>();

        assertThrows(IllegalStateException.class,
            () -> BufferProxy.allocatePersistentBufferPair(() -> {
                if (calls.getAndIncrement() == 0) {
                    return 73;
                }
                throw new IllegalStateException("index allocation failed");
            }, released::add));

        assertEquals(List.of(73), released);
    }

    @Test
    void pairReleaseAttemptsBothResourcesAndBecomesIdempotent() {
        List<Integer> released = new ArrayList<>();
        BufferProxy.VertexIndexBufferHandle handle =
            new BufferProxy.VertexIndexBufferHandle(17, 19, 23, 4);

        BufferProxy.releasePersistentPair(handle, released::add);
        BufferProxy.releasePersistentPair(handle, released::add);

        assertEquals(List.of(17, 19, 23), released);
        assertEquals(-1, handle.vertexId);
        assertEquals(-1, handle.indexId);
        assertEquals(-1, handle.patchIndexId);
        assertEquals(0, handle.patchIndexCount);
    }

    @Test
    void convertsSortedShortQuadTrianglesToFourPatchControlPoints() {
        ByteBuffer triangles = MemoryUtil.memAlloc(6 * Short.BYTES);
        triangles.putShort((short) 0).putShort((short) 1).putShort((short) 2)
            .putShort((short) 2).putShort((short) 3).putShort((short) 0).flip();
        ByteBuffer patches = BufferProxy.buildPatchIndices(triangles,
            VertexFormat.IndexType.SHORT, 6, 4);
        try {
            assertEquals(0, Short.toUnsignedInt(patches.getShort()));
            assertEquals(1, Short.toUnsignedInt(patches.getShort()));
            assertEquals(2, Short.toUnsignedInt(patches.getShort()));
            assertEquals(3, Short.toUnsignedInt(patches.getShort()));
        } finally {
            MemoryUtil.memFree(patches);
            MemoryUtil.memFree(triangles);
        }
    }

    @Test
    void convertsSortedIntQuadsWithoutLosingQuadOrder() {
        ByteBuffer triangles = MemoryUtil.memAlloc(12 * Integer.BYTES);
        for (int value : new int[]{4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0}) {
            triangles.putInt(value);
        }
        triangles.flip();
        ByteBuffer patches = BufferProxy.buildPatchIndices(triangles,
            VertexFormat.IndexType.INT, 12, 8);
        try {
            int[] actual = new int[8];
            patches.asIntBuffer().get(actual);
            assertEquals(List.of(4, 5, 6, 7, 0, 1, 2, 3),
                java.util.Arrays.stream(actual).boxed().toList());
        } finally {
            MemoryUtil.memFree(patches);
            MemoryUtil.memFree(triangles);
        }
    }
}
