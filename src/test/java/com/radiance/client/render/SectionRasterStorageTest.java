package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class SectionRasterStorageTest {
    @Test void pbrSectionCopyUsesActualBlockLayoutAfterSourceRelease() {
        SectionRasterStorage.Snapshot snapshot;
        var state = new MeshData.DrawState(com.radiance.client.vertex.PBRVertexFormats.PBR_TRIANGLE,
            1, 0, VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT);
        try (var vertices = new ByteBufferBuilder(128)) {
            ByteBuffer raw = MemoryUtil.memByteBuffer(vertices.reserve(128), 128);
            for (int i = 0; i < 128; ++i) raw.put(i, (byte) 0);
            raw.putFloat(0, 2.5f).putFloat(4, -3).putFloat(8, 7);
            raw.putFloat(16, -1).putFloat(20, 0).putFloat(24, 1);
            raw.putFloat(32, 1).putFloat(36, .5f).putFloat(40, 0).putFloat(44, .25f);
            raw.putFloat(56, .125f).putFloat(60, .75f);
            raw.putInt(96, 240).putInt(100, 160);
            try (var mesh = new MeshData(vertices.build(), state)) {
                snapshot = SectionRasterStorage.Snapshot.copy(mesh);
            }
        }
        assertSame(DefaultVertexFormat.BLOCK, snapshot.state().format());
        assertEquals(32, snapshot.vertices().length);
        ByteBuffer block = ByteBuffer.wrap(snapshot.vertices()).order(java.nio.ByteOrder.nativeOrder());
        assertEquals(2.5f, block.getFloat(0));
        assertEquals(-3f, block.getFloat(4));
        assertEquals(7f, block.getFloat(8));
        assertArrayEquals(new byte[]{-1, -128, 0, 64}, java.util.Arrays.copyOfRange(snapshot.vertices(), 12, 16));
        assertEquals(.125f, block.getFloat(16));
        assertEquals(.75f, block.getFloat(20));
        assertEquals(240, block.getShort(24));
        assertEquals(160, block.getShort(26));
        assertArrayEquals(new byte[]{-127, 0, 127, 0}, java.util.Arrays.copyOfRange(snapshot.vertices(), 28, 32));
        assertNull(snapshot.indices());
    }

    @Test void vertexAndSortedIndexCopiesSurviveCompilerStorageRelease() {
        SectionRasterStorage.Snapshot snapshot;
        byte[] expectedVertices;
        byte[] expectedIndices;
        var state = new MeshData.DrawState(DefaultVertexFormat.POSITION, 4, 6,
            VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT);
        try (var vertices = new ByteBufferBuilder(48); var indices = new ByteBufferBuilder(12)) {
            ByteBuffer raw = MemoryUtil.memByteBuffer(vertices.reserve(48), 48);
            for (float value : new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}) raw.putFloat(value);
            try (var mesh = new MeshData(vertices.build(), state)) {
                mesh.sortQuads(indices, VertexSorting.ORTHOGRAPHIC_Z);
                expectedVertices = bytes(mesh.vertexBuffer());
                expectedIndices = bytes(mesh.indexBuffer());
                snapshot = SectionRasterStorage.Snapshot.copy(mesh);
                assertEquals(0, mesh.vertexBuffer().position());
                mesh.vertexBuffer().put(0, (byte) 127);
                mesh.indexBuffer().put(0, (byte) 127);
            }
        }
        assertEquals(state, snapshot.state());
        assertArrayEquals(expectedVertices, snapshot.vertices());
        assertArrayEquals(expectedIndices, snapshot.indices());
        assertEquals(12, snapshot.indices().length);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
