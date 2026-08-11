package com.radiance.api.render;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

/** Explicit immutable local geometry. Only the source can decide when its contents change. */
public final class LocalModel implements AutoCloseable {
    public static final class Instance
            implements com.radiance.client.vertex.SourceBindings.Owner, AutoCloseable {
        private final com.radiance.client.vertex.SourceBindings bindings =
                new com.radiance.client.vertex.SourceBindings();

        public com.radiance.client.vertex.SourceBindings radiance$bindings() {
            return bindings;
        }

        @Override
        public void close() {
            PersistentInstances.removeOwner(this);
            bindings.retire();
        }
    }

    private record Part(
            long id,
            MeshData mesh,
            int type,
            com.radiance.client.texture.TextureTasks.Owner texture,
            String group,
            com.radiance.render.contract.DrawStateSnapshot state) {}

    private final StorageVertexConsumerProvider storage;
    private final List<Part> parts = new ArrayList<>();
    private final LocalModelBatch batch;
    private boolean closed;

    public LocalModel(Consumer<MultiBufferSource> definition) {
        storage =
                new StorageVertexConsumerProvider(16384) {
                    @Override
                    protected VertexConsumer createQuadConsumer(
                            ByteBufferBuilder allocator,
                            net.minecraft.client.renderer.RenderType layer) {
                        return new PBRVertexConsumer(allocator, layer);
                    }
                };
        try {
            definition.accept(storage);
            storage.endBatch();
            for (var entry : storage.getLayerBatches()) {
                if (!(entry.getValue() instanceof PBRVertexConsumer consumer))
                    throw new IllegalArgumentException(
                            "Local model requires canonical quad geometry");
                MeshData mesh = consumer.endNullable();
                if (mesh == null) continue;
                var layer = entry.getKey();
                var state =
                        com.radiance.adapter.vanilla.VanillaMaterialAdapter.sourceColour(
                                layer, storage.drawState(consumer));
                int type =
                        MaterialFaces.encode(
                                Constants.GeometryTypes.getGeometryType(layer, true).getValue(),
                                state.faces());
                type =
                        com.radiance.render.contract.SourceEmission.encode(
                                type,
                                com.radiance.adapter.vanilla.VanillaEmissionAdapter.policy(
                                                layer,
                                                com.radiance.render.contract.SourceEmission.Role
                                                        .ORDINARY,
                                                false)
                                        .bits());
                var texture =
                        Minecraft.getInstance()
                                .getTextureManager()
                                .getTexture(DrawLayers.texture(layer).orElseThrow())
                                .getId();
                parts.add(
                        new Part(
                                RenderResources.geometryIdentity(),
                                mesh,
                                type,
                                TextureProxy.TASKS.owner(texture),
                                PBRVertexConsumer.normalizeTextLayerName(layer.name),
                                state));
            }
            batch = new LocalModelBatch(parts.size());
        } catch (RuntimeException | Error failure) {
            parts.forEach(p -> p.mesh.close());
            storage.close();
            throw failure;
        }
    }

    public int partCount() {
        return parts.size();
    }

    public void draw(Instance owner, long identity, RenderSpace space, Vector3dc position) {
        if (closed) throw new IllegalStateException("Local model closed");
        batch.reset();
        var matrix = new Matrix4f();
        int ordinal = 0;
        for (var part : parts) {
            long history =
                    com.radiance.client.vertex.SourceBindings.localHistory(
                            owner, identity, ordinal++);
            int vertices = part.mesh.drawState().vertexCount();
            if (batch.selectUnchanged(
                    history,
                    owner,
                    part.id,
                    part.type,
                    part.texture,
                    vertices,
                    position.x(),
                    position.y(),
                    position.z(),
                    Constants.RayTracingFlags.WORLD.getValue(),
                    matrix,
                    part.group,
                    part.state,
                    space)) continue;
            batch.source(owner);
            batch.add(
                    part.id,
                    history,
                    part.type,
                    part.texture,
                    part.mesh.vertexBuffer(),
                    vertices,
                    position.x(),
                    position.y(),
                    position.z(),
                    Constants.RayTracingFlags.WORLD.getValue(),
                    matrix,
                    part.group,
                    part.state,
                    space);
        }
        batch.submit();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        batch.close();
        parts.forEach(p -> p.mesh.close());
        storage.close();
    }
}
