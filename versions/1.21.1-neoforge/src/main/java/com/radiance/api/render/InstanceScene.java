package com.radiance.api.render;

import com.radiance.client.proxy.world.NativeInstancingProxy;
import java.nio.*;
import org.joml.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Owned persistent rigid-instance scene. All methods run on the rendering thread. Buffers are
 * borrowed only until a call returns: native copies input and owns GPU versions until retirement.
 * Model IDs are local to this scene, not Vulkan addresses. Close after source/world invalidation.
 */
public final class InstanceScene implements AutoCloseable {
    public static final int VERTEX_BYTES = 56;

    public static boolean supported() {
        return NativeInstancingProxy.isSupported();
    }

    private final long handle = NativeInstancingProxy.createEngine();
    private boolean closed;

    public InstanceScene() {
        if (handle == 0) throw new IllegalStateException("Instance scene unavailable");
    }

    private long live() {
        if (closed) throw new IllegalStateException("Instance scene closed");
        return handle;
    }

    public int createModel(int meshes) {
        return NativeInstancingProxy.createModel(live(), meshes);
    }

    public void uploadMesh(
            int model,
            int mesh,
            ByteBuffer vertices,
            IntBuffer indices,
            int texture,
            int alpha,
            int material,
            String materialKey) {
        if (!vertices.isDirect()
                || !indices.isDirect()
                || vertices.remaining() % VERTEX_BYTES != 0
                || indices.remaining() % 3 != 0)
            throw new IllegalArgumentException("Incomplete canonical mesh span");
        NativeInstancingProxy.uploadModelMesh(
                live(),
                model,
                mesh,
                MemoryUtil.memAddress(vertices),
                vertices.remaining() / VERTEX_BYTES,
                MemoryUtil.memAddress(indices),
                indices.remaining(),
                texture,
                alpha,
                material,
                materialKey);
    }

    public void finishModel(int model) {
        NativeInstancingProxy.finishModel(live(), model);
    }

    public void deleteModel(int model) {
        NativeInstancingProxy.deleteModel(live(), model);
    }

    public void update(
            long id,
            int model,
            int bias,
            boolean visible,
            InstanceParameters parameters,
            Matrix4fc embedding,
            Matrix3fc normal,
            boolean embedded) {
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer matrices = stack.malloc(100);
            embedding.get(0, matrices);
            normal.get(64, matrices);
            ByteBuffer data = parameters.bytes();
            NativeInstancingProxy.updateInstance(
                    live(),
                    id,
                    model,
                    com.radiance.render.contract.RenderAbi.CANONICAL_INSTANCE_VERSION,
                    bias,
                    visible,
                    MemoryUtil.memAddress(data),
                    data.remaining(),
                    MemoryUtil.memAddress(matrices),
                    MemoryUtil.memAddress(matrices) + 64,
                    embedded);
        }
    }

    public void update(
            long id,
            int model,
            int bias,
            boolean visible,
            InstanceParameters parameters,
            boolean embedded,
            RenderSpace space) {
        update(id, model, bias, visible, parameters, new Matrix4f(), new Matrix3f(), embedded);
        NativeInstancingProxy.bindSpace(live(), id, space.handle());
    }

    public void remove(long id) {
        NativeInstancingProxy.deleteInstance(live(), id);
    }

    public void beginFrame(int x, int y, int z, double ticks) {
        NativeInstancingProxy.beginFrame(live(), x, y, z, ticks);
    }

    public void render() {
        live();
        RenderSpace.flushUpdates();
    }

    public void crumbling(
            LongBuffer instances, LongBuffer positions, IntBuffer progress, IntBuffer textures) {
        int count = instances.remaining();
        if (!instances.isDirect()
                || !positions.isDirect()
                || !progress.isDirect()
                || !textures.isDirect()
                || positions.remaining() != count
                || progress.remaining() != count
                || textures.remaining() != count)
            throw new IllegalArgumentException("Mismatched crumbling input spans");
        NativeInstancingProxy.renderCrumbling(
                live(),
                MemoryUtil.memAddress(instances),
                MemoryUtil.memAddress(positions),
                MemoryUtil.memAddress(progress),
                MemoryUtil.memAddress(textures),
                count);
    }

    @Override
    public void close() {
        if (!closed) {
            NativeInstancingProxy.deleteEngine(handle);
            closed = true;
        }
    }
}
