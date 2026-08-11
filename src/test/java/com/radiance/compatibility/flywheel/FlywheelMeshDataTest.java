package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.*;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class FlywheelMeshDataTest {
    private static Mesh mesh(int[] order) {
        return new Mesh() {
            @Override public int vertexCount() { return 3; }
            @Override public int indexCount() { return order.length; }
            @Override public Vector4fc boundingSphere() { return new Vector4f(0, 0, 0, 1); }
            @Override public IndexSequence indexSequence() {
                return (pointer, count) -> {
                    for (int i = 0; i < count; ++i) MemoryUtil.memPutInt(pointer + i * 4L, order[i]);
                };
            }
            @Override public void write(MutableVertexList vertices) {
                for (int i = 0; i < 3; ++i) {
                    vertices.x(i, i + 0.5f);
                    vertices.y(i, -2.25f);
                    vertices.z(i, 7.0f);
                    vertices.normalX(i, 0.1234567f);
                    vertices.u(i, 0.625f);
                    vertices.a(i, 0.3f);
                    vertices.overlay(i, 0x00120034);
                    vertices.light(i, 0x00F00080);
                }
            }
        };
    }

    @Test void keepsFullPrecisionVerticesAndActualMeshIndices() {
        try (var data = FlywheelMeshData.capture(mesh(new int[]{2, 0, 1}))) {
            var vertices = data.vertices();
            assertEquals(3 * 56, vertices.remaining());
            assertEquals(0.1234567f, vertices.getFloat(12));
            assertEquals(0.625f, vertices.getFloat(24));
            assertEquals(1.0f, vertices.getFloat(32));
            assertEquals(0.3f, vertices.getFloat(44));
            assertEquals(0x00120034, vertices.getInt(48));
            assertEquals(0x00F00080, vertices.getInt(52));
            assertEquals(2.5f, vertices.getFloat(2 * 56));
            assertArrayEquals(new int[]{2, 0, 1}, new int[]{data.indices().get(0), data.indices().get(1), data.indices().get(2)});
        }
    }

    @Test void rejectsInvalidIndicesAndUseAfterClose() {
        assertThrows(IllegalArgumentException.class, () -> FlywheelMeshData.capture(mesh(new int[]{0, 1, -1})));
        assertThrows(IllegalArgumentException.class, () -> FlywheelMeshData.capture(mesh(new int[]{0, 1, 3})));
        assertThrows(IllegalArgumentException.class, () -> FlywheelMeshData.capture(mesh(new int[]{0, 1})));
        var data = FlywheelMeshData.capture(mesh(new int[]{0, 1, 2}));
        data.close();
        data.close();
        assertThrows(IllegalStateException.class, data::vertices);
        assertThrows(IllegalStateException.class, data::indices);
    }
}
