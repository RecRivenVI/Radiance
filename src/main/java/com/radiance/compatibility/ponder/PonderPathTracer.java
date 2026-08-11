package com.radiance.compatibility.ponder;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Archived experiment (2026-09-23): production Ponder uses its default raster renderer.
 * Kept with the native service and tests for traceability; no active Mixin calls this class. */
public final class PonderPathTracer implements SuperRenderTypeBuffer, AutoCloseable {
    private record Output(DynamicTexture texture, int width, int height) {}
    private static final java.util.Map<Long, Output> outputs = new java.util.HashMap<>();
    public static void beginFrame(long[] visible) {
        var ids = java.util.Arrays.stream(visible).boxed().collect(java.util.stream.Collectors.toSet());
        outputs.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey())) return false;
            Minecraft.getInstance().getTextureManager().release(target(entry.getKey()));
            return true;
        });
        com.radiance.client.proxy.vulkan.UiPathTracingProxy.beginFrame(visible);
    }
    public static void releaseOutputs() {
        for (long id : outputs.keySet()) Minecraft.getInstance().getTextureManager().release(target(id));
        outputs.clear();
    }
    private static ResourceLocation target(long id) {
        return ResourceLocation.fromNamespaceAndPath("radiance", "ui_pt/" + id);
    }
    private static int frame;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(32768, 0);
    private final SuperRenderTypeBuffer fallback;
    private final long scene;
    private final Matrix4f scenePose;

    public PonderPathTracer(SuperRenderTypeBuffer fallback, long scene, Matrix4f pose) {
        this.fallback = fallback;
        this.scene = scene;
        this.scenePose = new Matrix4f(pose);
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        // Catnip terrain, block entities, entities and cuboid outlines emit quads. Other
        // primitives retain their original overlay path until a dedicated conversion exists.
        return type.mode() == VertexFormat.Mode.QUADS ? storage.getBuffer(type) : fallback.getBuffer(type);
    }
    @Override public VertexConsumer getEarlyBuffer(RenderType type) { return getBuffer(type); }
    @Override public VertexConsumer getLateBuffer(RenderType type) { return getBuffer(type); }
    @Override public void draw() {}
    @Override public void draw(RenderType type) {}

    public void render(GuiGraphics graphics) {
        List<MeshData> meshes = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        ByteBuffer triangles = null;
        try {
            int count = 0;
            for (var entry : storage.getLayers().entrySet()) {
                VertexConsumer consumer = entry.getValue();
                if (!(consumer instanceof PBRVertexConsumer pbr))
                    throw new IllegalStateException("Ponder quad capture requires PBR vertices");
                MeshData mesh = pbr.endNullable();
                if (mesh == null) continue;
                meshes.add(mesh);
                if (mesh.drawState().vertexCount() % 4 != 0)
                    throw new IllegalStateException("Incomplete Ponder quad");
                count = Math.addExact(count, mesh.drawState().vertexCount() / 4 * 2);
                groups.add(entry.getKey().name);
                faces.add(com.radiance.client.render.MaterialFaces.capture(entry.getKey()));
                counts.add(mesh.drawState().vertexCount() / 4 * 6);
            }
            if (count == 0) {
                com.radiance.client.proxy.vulkan.UiPathTracingProxy.emptyView(scene);
                return;
            }
            triangles = MemoryUtil.memAlloc(Math.multiplyExact(count, 3 * 128));
            int[] order = {0, 1, 2, 2, 3, 0};
            for (MeshData mesh : meshes) {
                ByteBuffer vertices = mesh.vertexBuffer();
                for (int quad = 0; quad < mesh.drawState().vertexCount(); quad += 4) {
                    for (int corner : order) {
                        int offset = (quad + corner) * 128;
                        triangles.put(vertices.slice(offset, 128));
                    }
                }
            }
            triangles.flip();
            Minecraft mc = Minecraft.getInstance();
            int windowWidth = mc.getWindow().getWidth(), windowHeight = mc.getWindow().getHeight();
            Matrix4f toClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            var point = new org.joml.Vector4f();
            for (int offset = 0; offset < triangles.limit(); offset += 128) {
                point.set(triangles.getFloat(offset), triangles.getFloat(offset+4), triangles.getFloat(offset+8), 1);
                toClip.transform(point);
                if (Math.abs(point.w) < 1e-8f) throw new IllegalStateException("UI geometry crosses the camera plane");
                float x = point.x / point.w, y = point.y / point.w;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            var viewport = com.radiance.client.render.UiViewport.enclosing(minX, minY, maxX, maxY, windowWidth, windowHeight);
            int width = viewport.width(), height = viewport.height();
            Output old = outputs.get(scene);
            if (old == null || old.width() != width || old.height() != height) {
                DynamicTexture texture = new DynamicTexture(new NativeImage(width, height, true));
                mc.getTextureManager().register(target(scene), texture);
                old = new Output(texture, width, height);
                outputs.put(scene, old);
            }
            DynamicTexture output = old.texture();
            graphics.flush();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.FloatBuffer matrices = stack.mallocFloat(48);
                scenePose.get(0, matrices);
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(scenePose).get(16, matrices);
                viewport.cropProjection(RenderSystem.getProjectionMatrix()).get(32, matrices);
                var owner = com.radiance.client.proxy.vulkan.TextureProxy.TASKS.owner(output.getId());
                final ByteBuffer geometry = triangles;
                boolean accepted = com.radiance.client.proxy.vulkan.TextureProxy.TASKS.use(owner, () ->
                    com.radiance.client.proxy.vulkan.PonderProxy.trace(scene, MemoryUtil.memAddress(geometry), geometry.remaining() / (3*128),
                        MemoryUtil.memAddress(matrices), owner.id(), width, height, frame++,
                        groups.toArray(String[]::new), counts.stream().mapToInt(Integer::intValue).toArray(), faces.stream().mapToInt(Integer::intValue).toArray()));
                if (!accepted) throw new IllegalStateException("UI PT output ownership changed during capture");
                if (frame <= 3) LOGGER.info("Ponder world pipeline: scene={}, triangles={}, window={}x{}, viewport=({},{}) {}x{}, compositeNdc=({},{})-({},{})",
                    scene, count, windowWidth, windowHeight, viewport.x(), viewport.y(), width, height,
                    viewport.leftNdc(), viewport.bottomNdc(), viewport.rightNdc(), viewport.topNdc());
            }
            // Draw in NDC: the captured vertices already include Ponder's screen transform.
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            var sorting = RenderSystem.getVertexSorting();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, output.getId());
                BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                quad.addVertex(viewport.leftNdc(), viewport.bottomNdc(), 0).setUv(0, 1);
                quad.addVertex(viewport.rightNdc(), viewport.bottomNdc(), 0).setUv(1, 1);
                quad.addVertex(viewport.rightNdc(), viewport.topNdc(), 0).setUv(1, 0);
                quad.addVertex(viewport.leftNdc(), viewport.topNdc(), 0).setUv(0, 0);
                BufferUploader.drawWithShader(quad.buildOrThrow());
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.enableDepthTest();
            }
        } finally {
            for (MeshData mesh : meshes) mesh.close();
            if (triangles != null) MemoryUtil.memFree(triangles);
        }
    }

    @Override public void close() { storage.close(); }
}
