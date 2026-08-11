package com.radiance.audit;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

/** One opt-in, no-world raster probe using the original Simulated segment producer. */
public final class SpringMenuProbe {
    private static final boolean ENABLED = "1".equals(ExperimentAccess.getenv("RADIANCE_SPRING_MENU_PROBE"));
    private static final boolean RENDERDOC_CAPTURE = "1".equals(
        ExperimentAccess.getenv("RADIANCE_SPRING_RENDERDOC_CAPTURE"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final int WIDTH = 256, HEIGHT = 192;
    private static long ready;
    private static boolean done;
    private static volatile int selectedLightmap = -1;
    private static volatile boolean springDraw;
    private static volatile int drawOrdinal = -1;
    private static volatile int bodyTexture = -1;
    private static volatile int boundLightmap = -1;
    private static final java.util.concurrent.atomic.AtomicInteger NATIVE_DRAWS =
        new java.util.concurrent.atomic.AtomicInteger();

    private SpringMenuProbe() {}

    /** Called by the Audit-only RenderSystem getter mixin during an original spring draw. */
    public static int lightmap(int slot) {
        return springDraw && slot == 2 ? selectedLightmap : -1;
    }

    public static int drawOrdinal() { return springDraw ? drawOrdinal : -1; }
    public static int nativeDrawRecorded() { return NATIVE_DRAWS.incrementAndGet(); }

    public static void rememberTextures(int body, int light) {
        if (bodyTexture >= 0 && bodyTexture != body)
            throw new IllegalStateException("Spring body texture changed between paired draws");
        if (boundLightmap >= 0 && boundLightmap != light)
            throw new IllegalStateException("Spring lightmap changed between paired draws");
        if (light != selectedLightmap)
            throw new IllegalStateException("Spring draw did not bind the fixture lightmap");
        bodyTexture = body;
        boundLightmap = light;
    }

    public static void retainMesh(MeshData mesh, int ordinal) {
        try {
            Path output = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("radiance-audit/spring-menu");
            Files.createDirectories(output);
            var draw = mesh.drawState();
            ByteBuffer vertex = mesh.vertexBuffer().duplicate();
            byte[] vertices = new byte[vertex.remaining()];
            vertex.get(vertices);
            if (draw.vertexCount() != 32 || draw.format().getVertexSize() != 32
                || vertices.length != 1024 || draw.indexCount() != 48
                || draw.mode() != VertexFormat.Mode.QUADS)
                throw new IllegalStateException("Spring MeshData differs from 32B source contract");
            Files.write(output.resolve("vertices-" + ordinal + ".bin"), vertices);
            ByteBuffer index = mesh.indexBuffer();
            if (index != null) {
                byte[] indices = new byte[index.remaining()];
                index.duplicate().get(indices);
                Files.write(output.resolve("indices-" + ordinal + ".bin"), indices);
            } else {
                // BufferProxy.buildIndexBuffer uses this exact QUADS expansion for type 0.
                ByteBuffer expected = ByteBuffer.allocate(48 * Short.BYTES)
                    .order(ByteOrder.nativeOrder());
                for (int i = 0; i < 32; i += 4)
                    for (int corner : new int[] {0, 1, 2, 2, 3, 0})
                        expected.putShort((short) (i + corner));
                Files.write(output.resolve("generated-indices-" + ordinal + ".bin"),
                    expected.array());
            }
            Files.writeString(output.resolve("mesh-" + ordinal + ".json"),
                "{\"vertices\":32,\"stride\":32,\"indices\":48,\"indexBytes\":"
                    + draw.indexType().bytes + ",\"source\":\""
                    + (index == null ? "native-generated-QUADS-012230" : "MeshData") + "\"}");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot retain exact spring MeshData", failure);
        }
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || done || mc.level != null || !(mc.screen instanceof TitleScreen)
            || mc.getOverlay() != null) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Spring menu probe requires isolated game directory");
        long window = mc.getWindow().getWindow();
        if (mc.getWindow().getWidth() <= 0 || mc.getWindow().getHeight() <= 0
            || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) != 0
            || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == 0) return;
        if (ready == 0) {
            ready = System.nanoTime();
            LOG.info("SPRING_MENU title-ready pid={} framebuffer={}x{} focused={} iconified=false",
                ProcessHandle.current().pid(), mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0);
        }
        if (System.nanoTime() - ready < 4_000_000_000L) return;
        done = true;
        run(mc);
        LOG.info("SPRING_MENU normal-stop-requested");
        mc.stop();
    }

    private static void run(Minecraft mc) {
        boolean vulkan = net.neoforged.fml.ModList.get().isLoaded("radiance");
        Path output = mc.gameDirectory.toPath().resolve("radiance-audit/spring-menu");
        TextureTarget target = null;
        DynamicTexture lightmap = null;
        ByteBuffer colorPixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        ByteBuffer depthPixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        var saved = new GlStateBackup();
        RenderSystem.backupGlState(saved);
        var previousShader = RenderSystem.getShader();
        var previousColor = RenderSystem.getShaderColor().clone();
        var previousFog = RenderSystem.getShaderFogColor().clone();
        Vector3f[] previousLights = currentVeilLights();
        float previousFogStart = RenderSystem.getShaderFogStart();
        float previousFogEnd = RenderSystem.getShaderFogEnd();
        int previousLightmap = RenderSystem.getShaderTexture(2);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        AutoCloseable scope = null;
        try {
            Files.createDirectories(output);
            if (RENDERDOC_CAPTURE) {
                if (!vulkan) throw new IllegalStateException("RenderDoc spring capture requires Vulkan");
                String status = NativeDiagnostics.renderDocStatus();
                LOG.info("SPRING_MENU renderdoc-status={}", status);
                if (!status.startsWith("ready-api-"))
                    throw new IllegalStateException("RenderDoc capture API unavailable: " + status);
                if (!NativeDiagnostics.startRenderDoc(output.resolve("spring-menu-vk")))
                    throw new IllegalStateException("RenderDoc did not begin the spring FBO capture");
                LOG.info("SPRING_MENU renderdoc-started template={}",
                    output.resolve("spring-menu-vk").toAbsolutePath());
            }
            if (vulkan) scope = enterVulkanGuiScope();
            NativeImage white = new NativeImage(16, 16, false);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                white.setPixelRGBA(x, y, 0xFFFFFFFF);
            try { lightmap = new DynamicTexture(white); }
            catch (RuntimeException | Error failure) { white.close(); throw failure; }
            lightmap.setFilter(true, false);
            selectedLightmap = lightmap.getId();
            target = new TextureTarget(WIDTH, HEIGHT, true, false);
            target.setClearColor(0, 0, 0, 0);
            target.clear(false);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 0, 12, -20, 20),
                VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(1000);
            RenderSystem.setShaderFogEnd(1001);
            RenderSystem.setShaderFogColor(0, 0, 0, 0);
            RenderSystem.setShaderLights(new Vector3f(0.3F, 0.9F, 0.4F).normalize(),
                new Vector3f(-0.6F, 0.5F, 0.7F).normalize());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            target.bindWrite(true);

            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            buffers.endBatch();
            // Genuine vanilla block-model producer supplies floor and anchors into the same FBO.
            for (int x = 0; x < 16; x++) block(mc, buffers, Blocks.STONE, x, 0, 0);
            buffers.endBatch();
            modelView.translate(0, 0, -0.2F);
            RenderSystem.applyModelViewMatrix();
            for (int x : new int[] {4, 8, 12}) {
                block(mc, buffers, Blocks.IRON_BLOCK, x, 1, 0);
                block(mc, buffers, Blocks.IRON_BLOCK, x, 8, 0);
            }
            buffers.endBatch();

            ResourceLocation body = ResourceLocation.fromNamespaceAndPath("simulated",
                "textures/block/spring/spring.png");
            RenderType springLayer = (RenderType) Class.forName(
                "dev.simulated_team.simulated.index.SimRenderTypes")
                .getMethod("spring", ResourceLocation.class).invoke(null, body);
            Class<?> producerClass = Class.forName(
                "dev.simulated_team.simulated.content.blocks.spring.SpringRenderer");
            Object producer = producerClass.getConstructor(
                net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context.class)
                .newInstance((Object) null);
            Method segment = producerClass.getDeclaredMethod("renderSegment", PoseStack.class,
                Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class,
                Vector3dc.class, Vector3dc.class, boolean.class, float.class, float.class,
                int.class, int.class, VertexConsumer.class, float.class, float.class);
            segment.setAccessible(true);
            int draw = 0;
            for (int x : new int[] {4, 8, 12}) {
                modelView.identity().translate(x + 0.5F, 2, 0.2F + draw * 0.1F);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(1, draw == 1 ? 0.8F : 1, 1, 1);
                RenderSystem.setShaderFogStart(draw == 1 ? 9 : 1000);
                RenderSystem.setShaderFogEnd(draw == 1 ? 14 : 1001);
                PoseStack pose = new PoseStack();
                if (draw == 2) pose.scale(-1, 1, 1);
                VertexConsumer vertices = buffers.getBuffer(springLayer);
                emitSegmentPair(segment, producer, pose, vertices, draw != 0,
                    draw == 0 ? 0x00000000 : 0x80FF2020);
                inspectPendingSpringMesh(buffers, springLayer, draw);
                drawOrdinal = draw;
                springDraw = true;
                try { buffers.endBatch(springLayer); }
                finally { springDraw = false; drawOrdinal = -1; }
                LOG.info("SPRING_MENU spring-draw ordinal={} x={} mirrored={} stressAlpha={} body={} lightmap={}",
                    draw, x, draw == 2, draw == 0 ? 0 : 128, body, selectedLightmap);
                draw++;
            }
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            if (vulkan && NATIVE_DRAWS.get() != 3)
                throw new IllegalStateException("Expected three spring ShaderProxy calls, got "
                    + NATIVE_DRAWS.get());
            target.bindRead();
            readPixels(vulkan, colorPixels, 0x1908, 0x1401);
            readPixels(vulkan, depthPixels, 0x1902, 0x1406);
            if (RENDERDOC_CAPTURE) {
                String capture = NativeDiagnostics.stopRenderDoc();
                if (capture == null || capture.isBlank())
                    throw new IllegalStateException("RenderDoc did not finish the spring FBO capture");
                Files.writeString(output.resolve("renderdoc-capture.txt"), capture);
                LOG.info("SPRING_MENU renderdoc-ended capture={}", capture);
            }
            if (vulkan) {
                if (bodyTexture < 0 || boundLightmap < 0)
                    throw new IllegalStateException("Spring sampler IDs were not captured");
                retainTexture(output, "body-gpu.rgba", bodyTexture);
                retainTexture(output, "lightmap-gpu.rgba", boundLightmap);
            }
            byte[] rgba = new byte[WIDTH * HEIGHT * 4];
            colorPixels.get(rgba);
            Files.write(output.resolve("color.rgba"), rgba);
            byte[] depth = new byte[WIDTH * HEIGHT * 4];
            depthPixels.get(depth);
            Files.write(output.resolve("depth.f32"), depth);
            try (NativeImage image = new NativeImage(WIDTH, HEIGHT, false)) {
                for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                    int p = (y * WIDTH + x) * 4;
                    int abgr = (rgba[p] & 255) | ((rgba[p + 1] & 255) << 8)
                        | ((rgba[p + 2] & 255) << 16) | ((rgba[p + 3] & 255) << 24);
                    image.setPixelRGBA(x, HEIGHT - y - 1, abgr);
                }
                image.writeToFile(output.resolve("color.png"));
            }
            int[] coverage = new int[3];
            for (int column = 0; column < 3; column++) {
                int center = (4 + column * 4) * 16 + 8;
                for (int y = 34; y < 126; y++) for (int x = center - 6; x < center + 6; x++)
                    if ((rgba[(y * WIDTH + x) * 4 + 3] & 255) > 0) coverage[column]++;
            }
            Files.writeString(output.resolve("manifest.json"),
                "{\"width\":256,\"height\":192,\"vulkan\":" + vulkan
                    + ",\"springDraws\":3,\"indicesPerDraw\":48,\"sampler2\":"
                    + selectedLightmap + ",\"alphaCoverage\":[" + coverage[0] + ','
                    + coverage[1] + ',' + coverage[2] + "]}");
            LOG.info("SPRING_MENU capture-complete backend={} size={}x{} alphaCoverage={}/{}/{}",
                vulkan ? "VK" : "GL", WIDTH, HEIGHT, coverage[0], coverage[1], coverage[2]);
            if (coverage[0] == 0 || coverage[1] == 0 || coverage[2] == 0)
                throw new IllegalStateException("Spring menu FBO has no spring in at least one ROI");
        } catch (Exception failure) {
            throw new IllegalStateException("Spring menu production raster probe failed", failure);
        } finally {
            springDraw = false;
            drawOrdinal = -1;
            selectedLightmap = -1;
            if (target != null) target.destroyBuffers();
            if (lightmap != null) lightmap.close();
            if (scope != null) try { scope.close(); } catch (Exception failure) {
                LOG.error("SPRING_MENU scope close failed", failure);
            }
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.restoreGlState(saved);
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1],
                previousColor[2], previousColor[3]);
            RenderSystem.setShaderFogStart(previousFogStart);
            RenderSystem.setShaderFogEnd(previousFogEnd);
            RenderSystem.setShaderFogColor(previousFog[0], previousFog[1],
                previousFog[2], previousFog[3]);
            if (previousLights != null)
                RenderSystem.setShaderLights(previousLights[0], previousLights[1]);
            RenderSystem.setShaderTexture(2, previousLightmap);
            MemoryUtil.memFree(colorPixels);
            MemoryUtil.memFree(depthPixels);
        }
    }

    private static void retainTexture(Path output, String filename, int textureId) throws Exception {
        ByteBuffer pixels = MemoryUtil.memAlloc(16 * 16 * 4);
        try {
            com.radiance.client.proxy.vulkan.TextureProxy.downloadTexture(textureId,
                0, 16, 16, 4, MemoryUtil.memAddress(pixels));
            byte[] data = new byte[pixels.capacity()];
            pixels.get(data);
            Files.write(output.resolve(filename), data);
            LOG.info("SPRING_MENU texture-readback name={} id={} bytes={}",
                filename, textureId, data.length);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void block(Minecraft mc, MultiBufferSource.BufferSource buffers,
        net.minecraft.world.level.block.Block block, int x, int y, int z) {
        PoseStack pose = new PoseStack();
        pose.translate(x, y, z);
        mc.getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers,
            0x00F000F0, OverlayTexture.NO_OVERLAY);
    }

    private static void emitSegmentPair(Method segment, Object producer, PoseStack pose,
        VertexConsumer vertices, boolean twist, int stress) throws Exception {
        Vector3d direction = new Vector3d(0, 1, 0);
        Vector3d up = new Vector3d(0, 0, -1);
        Vector3d endUp = new Vector3d(up);
        if (twist) endUp.rotateY(Math.toRadians(7.4));
        Vector3d start = new Vector3d(0, 0, 0), end = new Vector3d(0, 6, 0);
        segment.invoke(producer, pose, direction, direction, up, endUp, start, end,
            false, 0.25F, 5.75F, 0x00F000F0, stress, vertices, 8.0F, 16.0F);
        segment.invoke(producer, pose, new Vector3d(direction).negate(),
            new Vector3d(direction).negate(), new Vector3d(up).negate(),
            new Vector3d(endUp).negate(), start, end,
            true, -0.25F, -5.75F, 0x00F000F0, stress, vertices, 8.0F, 16.0F);
    }

    private static void inspectPendingSpringMesh(MultiBufferSource.BufferSource buffers,
        RenderType springLayer, int ordinal) throws Exception {
        var startedField = MultiBufferSource.BufferSource.class.getDeclaredField("startedBuilders");
        startedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var started = (java.util.Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder>)
            startedField.get(buffers);
        var builder = started.get(springLayer);
        if (builder == null) throw new IllegalStateException("Spring BufferSource has no builder");
        var verticesField = com.mojang.blaze3d.vertex.BufferBuilder.class.getDeclaredField("vertices");
        var strideField = com.mojang.blaze3d.vertex.BufferBuilder.class.getDeclaredField("vertexSize");
        verticesField.setAccessible(true);
        strideField.setAccessible(true);
        int vertices = verticesField.getInt(builder), stride = strideField.getInt(builder);
        if (vertices != 32 || stride != 32 || springLayer.mode() != VertexFormat.Mode.QUADS)
            throw new IllegalStateException("Spring raster producer ABI changed: vertices="
                + vertices + " stride=" + stride + " mode=" + springLayer.mode());
        LOG.info("SPRING_MENU source-mesh ordinal={} vertices={} stride={} bytes={} indices={}",
            ordinal, vertices, stride, vertices * stride, vertices / 4 * 6);
    }

    private static AutoCloseable enterVulkanGuiScope() throws Exception {
        Class<?> contract = Class.forName("com.radiance.client.render.RenderCaptureContract");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object kind = Enum.valueOf((Class<Enum>) Class.forName(
            "com.radiance.client.render.RenderCaptureContract$ScopeKind"), "GUI");
        return (AutoCloseable) contract.getMethod("enter", kind.getClass(), String.class)
            .invoke(null, kind, "audit-spring-menu");
    }

    private static Vector3f[] currentVeilLights() {
        try {
            Class<?> render = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
            return new Vector3f[] {
                new Vector3f((Vector3fc) render.getMethod("getLight0Direction").invoke(null)),
                new Vector3f((Vector3fc) render.getMethod("getLight1Direction").invoke(null))
            };
        } catch (ReflectiveOperationException unavailable) {
            LOG.warn("SPRING_MENU could not snapshot Veil light directions", unavailable);
            return null;
        }
    }

    private static void readPixels(boolean vulkan, ByteBuffer target, int format, int type)
        throws Exception {
        target.clear();
        if (vulkan) Class.forName("com.radiance.client.proxy.vulkan.FramebufferProxy")
            .getMethod("readPixels", int.class, int.class, int.class, int.class,
                int.class, int.class, long.class)
            .invoke(null, 0, 0, WIDTH, HEIGHT, format, type, MemoryUtil.memAddress(target));
        else org.lwjgl.opengl.GL11.glReadPixels(0, 0, WIDTH, HEIGHT, format, type, target);
    }
}
