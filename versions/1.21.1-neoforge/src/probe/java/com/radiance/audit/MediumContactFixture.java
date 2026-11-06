package com.radiance.audit;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.api.render.LocalModelBatch;
import com.radiance.api.render.PersistentInstances;
import com.radiance.api.render.RenderResources;
import com.radiance.api.render.RenderSpace;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import com.radiance.client.vertex.PBRMaterialContext;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.render.contract.DrawStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** Small persistent moving dielectric inputs, used only by the medium fixture. */
final class MediumContactFixture {
    private static boolean registered;
    private static String scene = "";
    private static long selected;
    private static Model single, joined;

    private MediumContactFixture() {}

    static void select(String value) {
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(MediumContactFixture::render);
        }
        if (!scene.equals(value)) {
            scene = value;
            selected = System.nanoTime();
        }
    }

    static void close() {
        scene = "";
        if (single != null) {
            single.close();
            single = null;
        }
        if (joined != null) {
            joined.close();
            joined = null;
        }
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || !scene.startsWith("dynamic-")) return;
        if (single == null) single = new Model(false);
        if (joined == null) joined = new Model(true);
        Model model = scene.equals("dynamic-internal-contact") ? joined : single;
        double x = 28;
        if (scene.equals("dynamic-approach"))
            x = 29 + Math.min(2, (System.nanoTime() - selected) / 1e9);
        else if (scene.equals("dynamic-separate"))
            x = 31 - Math.min(.75, (System.nanoTime() - selected) / 1e9);
        else if (scene.equals("dynamic-real-gap")) x = 30.99;
        model.submit(x, scene.equals("dynamic-isolated") ? 67 : 65, 10);
    }

    private static final class Model implements AutoCloseable {
        final ByteBufferBuilder allocation = new ByteBufferBuilder(8192);
        final ResourceLocation name;
        final TextureTasks.Owner texture;
        final MeshData mesh;
        final RenderSpace space = new RenderSpace();
        final long identity = RenderResources.geometryIdentity();
        final int type = Constants.GeometryTypes.WORLD_TRANSPARENT.getValue();

        Model(boolean joined) {
            name = ResourceLocation.fromNamespaceAndPath("radiance", "probe/contact_" + identity);
            var image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0x80ffffff);
            var dynamic = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(name, dynamic);
            texture = java.util.Objects.requireNonNull(TextureProxy.TASKS.owner(dynamic.getId()));
            try (var transmission = PBRMaterialContext.pushEntityTransmission(true)) {
                var out = new PBRVertexConsumer(allocation, RenderType.entityTranslucent(name));
                cube(out, 0, 0xffff8080);
                if (joined) cube(out, 1, 0xff8080ff);
                mesh = out.end();
                if ((mesh.vertexBuffer().order(java.nio.ByteOrder.nativeOrder()).getInt(124)
                                & com.radiance.render.contract.RenderAbi.PACKED_ALPHA_MASK)
                        != com.radiance.render.contract.RenderAbi.ALPHA_TRANSMISSION)
                    throw new AssertionError(
                            "Moving dielectric fixture lost optical source identity");
            }
        }

        static void cube(PBRVertexConsumer out, float x, int colour) {
            int[][] faces = {
                {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
            };
            float[][] normals = {
                {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}
            };
            for (int f = 0; f < 6; f++)
                for (int i = 0; i < 4; i++) {
                    int p = faces[f][i];
                    float[] n = normals[f];
                    out.addVertex(x + (p & 1), ((p >> 1) & 1), ((p >> 2) & 1))
                            .setColor(colour)
                            .setUv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0)
                            .setOverlay(655360)
                            .setLight(0)
                            .setNormal(n[0], n[1], n[2]);
                }
        }

        void submit(double x, double y, double z) {
            space.pose(new Matrix4d().translation(x, y, z), new Matrix3f());
            try (var batch = new LocalModelBatch(1)) {
                batch.source(this);
                batch.add(
                        (identity << 32) | Integer.toUnsignedLong(type),
                        identity | Long.MIN_VALUE,
                        type,
                        texture,
                        mesh.vertexBuffer(),
                        mesh.drawState().vertexCount(),
                        0,
                        0,
                        0,
                        Constants.RayTracingFlags.WORLD.getValue(),
                        new Matrix4f(),
                        "default",
                        new DrawStateSnapshot(0, 1, 1, 1, 1, 1),
                        space);
                batch.submit();
            }
        }

        public void close() {
            PersistentInstances.removeOwner(this);
            mesh.close();
            allocation.close();
            space.close();
            Minecraft.getInstance().getTextureManager().release(name);
        }
    }
}
