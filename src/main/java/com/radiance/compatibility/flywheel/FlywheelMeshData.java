package com.radiance.compatibility.flywheel;

import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Objects;
import org.lwjgl.system.MemoryUtil;

/** Lossless canonical model input for the Vulkan instancer; uploaded once per cached mesh. */
public final class FlywheelMeshData implements AutoCloseable {
    // position xyz, normal xyz, uv, color rgba, packed overlay and packed light.
    public static final int STRIDE = 56;
    private ByteBuffer vertices;
    private IntBuffer indices;
    private final int vertexCount;
    private final int indexCount;

    private FlywheelMeshData(int vertexCount, int indexCount) {
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        vertices = MemoryUtil.memCalloc(Math.multiplyExact(vertexCount, STRIDE)).order(ByteOrder.nativeOrder());
        try {
            indices = MemoryUtil.memCallocInt(indexCount);
        } catch (Throwable failure) {
            MemoryUtil.memFree(vertices);
            throw failure;
        }
        for (int i = 0; i < vertexCount; ++i) {
            for (int channel = 0; channel < 4; ++channel) vertices.putFloat(i * STRIDE + 32 + 4 * channel, 1.0f);
        }
    }

    public static FlywheelMeshData capture(Mesh mesh) {
        Objects.requireNonNull(mesh, "mesh");
        int vertices = mesh.vertexCount(), indices = mesh.indexCount();
        if (vertices < 0 || indices < 0 || indices % 3 != 0) {
            throw new IllegalArgumentException("Flywheel mesh must contain a valid triangle index sequence");
        }
        FlywheelMeshData result = new FlywheelMeshData(vertices, indices);
        try {
            mesh.write(result.new VertexView());
            if (indices > 0) mesh.indexSequence().fill(MemoryUtil.memAddress(result.indices), indices);
            for (int i = 0; i < indices; ++i) {
                int index = result.indices.get(i);
                if (Integer.compareUnsigned(index, vertices) >= 0) {
                    throw new IllegalArgumentException("Flywheel mesh index " + Integer.toUnsignedString(index)
                        + " exceeds vertex count " + vertices);
                }
            }
            return result;
        } catch (Throwable failure) {
            result.close();
            throw failure;
        }
    }

    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }
    public long vertexAddress() { ensureOpen(); return MemoryUtil.memAddress(vertices); }
    public long indexAddress() { ensureOpen(); return MemoryUtil.memAddress(indices); }
    public ByteBuffer vertices() { ensureOpen(); return vertices.asReadOnlyBuffer().order(ByteOrder.nativeOrder()); }
    public IntBuffer indices() { ensureOpen(); return indices.asReadOnlyBuffer(); }

    private void ensureOpen() {
        if (vertices == null) throw new IllegalStateException("Flywheel mesh capture is closed");
    }

    @Override
    public void close() {
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
            vertices = null;
            indices = null;
        }
    }

    private final class VertexView implements MutableVertexList {
        private int at(int index, int field) { return Objects.checkIndex(index, vertexCount) * STRIDE + field; }
        private float get(int index, int field) { return vertices.getFloat(at(index, field)); }
        private void set(int index, int field, float value) { vertices.putFloat(at(index, field), value); }
        @Override public int vertexCount() { return vertexCount; }
        @Override public float x(int i) { return get(i, 0); }
        @Override public float y(int i) { return get(i, 4); }
        @Override public float z(int i) { return get(i, 8); }
        @Override public float normalX(int i) { return get(i, 12); }
        @Override public float normalY(int i) { return get(i, 16); }
        @Override public float normalZ(int i) { return get(i, 20); }
        @Override public float u(int i) { return get(i, 24); }
        @Override public float v(int i) { return get(i, 28); }
        @Override public float r(int i) { return get(i, 32); }
        @Override public float g(int i) { return get(i, 36); }
        @Override public float b(int i) { return get(i, 40); }
        @Override public float a(int i) { return get(i, 44); }
        @Override public int overlay(int i) { return vertices.getInt(at(i, 48)); }
        @Override public int light(int i) { return vertices.getInt(at(i, 52)); }
        @Override public void x(int i, float v) { set(i, 0, v); }
        @Override public void y(int i, float v) { set(i, 4, v); }
        @Override public void z(int i, float v) { set(i, 8, v); }
        @Override public void normalX(int i, float v) { set(i, 12, v); }
        @Override public void normalY(int i, float v) { set(i, 16, v); }
        @Override public void normalZ(int i, float v) { set(i, 20, v); }
        @Override public void u(int i, float v) { set(i, 24, v); }
        @Override public void v(int i, float v) { set(i, 28, v); }
        @Override public void r(int i, float v) { set(i, 32, v); }
        @Override public void g(int i, float v) { set(i, 36, v); }
        @Override public void b(int i, float v) { set(i, 40, v); }
        @Override public void a(int i, float v) { set(i, 44, v); }
        @Override public void overlay(int i, int v) { vertices.putInt(at(i, 48), v); }
        @Override public void light(int i, int v) { vertices.putInt(at(i, 52), v); }
    }
}
