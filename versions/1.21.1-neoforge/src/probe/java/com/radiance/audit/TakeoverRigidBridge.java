package com.radiance.audit;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import com.radiance.client.vertex.PBRMaterialContext;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.render.contract.DrawStateSnapshot;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Probe-only independent input adapter. It neither implements nor calls any Flywheel API. */
public final class TakeoverRigidBridge implements AutoCloseable {
    public record Intent(long identity, Matrix4f pose, float red, float green, float blue) {
        public Intent {
            pose = new Matrix4f(pose);
        }
    }

    private final ByteBufferBuilder allocation = new ByteBufferBuilder(1024);
    private final MeshData mesh;
    private final TextureTasks.Owner texture;
    private final long model = com.radiance.api.render.RenderResources.geometryIdentity();
    private final ResourceLocation textureName;
    private final int type = Constants.GeometryTypes.WORLD_SOLID.getValue();
    private long submissions;

    public TakeoverRigidBridge(int epoch) {
        this(epoch, 0);
    }

    public TakeoverRigidBridge(int epoch, float cornerDepth) {
        var image = new NativeImage(1, 1, false);
        image.setPixelRGBA(0, 0, -1);
        textureName = ResourceLocation.fromNamespaceAndPath("radiance", "probe/takeover_" + epoch);
        var dynamic = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(textureName, dynamic);
        texture = java.util.Objects.requireNonNull(TextureProxy.TASKS.owner(dynamic.getId()));
        try (var emission = PBRMaterialContext.pushAlbedoEmission(1)) {
            var output = new PBRVertexConsumer(allocation, RenderType.entitySolid(textureName));
            for (float[] p :
                    new float[][] {{-.45f, -.45f}, {.45f, -.45f}, {.45f, .45f}, {-.45f, .45f}}) {
                output.addVertex(p[0], p[1], p[0] > 0 && p[1] > 0 ? cornerDepth : 0)
                        .setColor(-1)
                        .setUv(p[0] + .5f, p[1] + .5f)
                        .setOverlay(655360)
                        .setLight(15728880)
                        .setNormal(0, 0, 1);
            }
            mesh = output.end();
        }
    }

    public void submit(double x, double y, double z, List<Intent> intents) {
        try (var batch = new com.radiance.api.render.LocalModelBatch(intents.size())) {
            for (var intent : intents) {
                batch.source(this);
                batch.add(
                        (model << 32) | Integer.toUnsignedLong(type),
                        intent.identity() | Long.MIN_VALUE,
                        type,
                        texture,
                        mesh.vertexBuffer(),
                        mesh.drawState().vertexCount(),
                        x,
                        y,
                        z,
                        Constants.RayTracingFlags.WORLD.getValue(),
                        intent.pose(),
                        "default",
                        new DrawStateSnapshot(
                                0, 1, intent.red(), intent.green(), intent.blue(), 1));
            }
            batch.submit();
            submissions += intents.size();
        }
    }

    public long model() {
        return model;
    }

    public long submissions() {
        return submissions;
    }

    @Override
    public void close() {
        com.radiance.api.render.PersistentInstances.removeOwner(this);
        mesh.close();
        allocation.close();
        Minecraft.getInstance().getTextureManager().release(textureName);
    }
}
