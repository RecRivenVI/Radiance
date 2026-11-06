package com.radiance.audit;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.logging.LogUtils;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.vulkan.UiPathTracingProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/** Drives the real world, source, and view lifecycles while Native compares both layouts. */
final class SceneStoreProbe {
    private static final Logger LOG = LogUtils.getLogger();
    private static final BlockPos CHEST = new BlockPos(2, 71, 3);
    private static final BlockPos EDIT = new BlockPos(5, 71, 3);
    private static final List<String> SCENES =
            List.of(
                    "static",
                    "move-turn",
                    "place-break",
                    "chest",
                    "section-reload",
                    "packs",
                    "dimension",
                    "auxiliary",
                    "sable");
    // Native emits successful verification totals in batches of 60 frames.
    private static final int OBSERVATION_FRAMES = 780;
    private static final boolean AUXILIARY_ONLY =
            "scene-store-auxiliary".equals(System.getProperty("radiance.audit.worldMaterials134"));
    private static final List<Map<String, Object>> completed = new ArrayList<>();
    private static int scene = AUXILIARY_ONLY ? SCENES.indexOf("auxiliary") : 0;
    private static int frames, events, step;
    private static boolean setup, observing;
    private static CompletableFuture<Void> reload;

    private SceneStoreProbe() {}

    static boolean poll(Minecraft mc, Map<String, Object> result)
            throws ReflectiveOperationException {
        if (!setup) {
            setup = true;
            DrawingCensusProbe.command(mc, "gamerule doDaylightCycle false");
            DrawingCensusProbe.command(mc, "time set noon");
            DrawingCensusProbe.command(mc, "fill -6 70 -6 7 70 8 stone");
            DrawingCensusProbe.command(mc, "setblock 2 71 3 chest");
            DrawingCensusProbe.command(mc, "tp @a 2.5 71 0.5 0 8");
        }
        if ((scene == 0 || AUXILIARY_ONLY)
                && !(mc.level.getBlockEntity(CHEST) instanceof ChestBlockEntity)) return false;
        String name = SCENES.get(scene);
        if (!observing) {
            observing = true;
            LOG.info("SCENE_STORE begin {} at={}", name, System.currentTimeMillis());
        }
        boolean ready =
                switch (name) {
                    case "static" -> true;
                    case "move-turn" -> move(mc);
                    case "place-break" -> edit(mc);
                    case "chest" -> chest(mc);
                    case "section-reload" -> sections(mc);
                    case "packs" -> packs(mc);
                    case "dimension" -> dimension(mc);
                    case "auxiliary" -> auxiliary(mc);
                    case "sable" -> sable(mc);
                    default -> throw new IllegalStateException(name);
                };
        if (!ready || ++frames < OBSERVATION_FRAMES) return false;
        LOG.info(
                "SCENE_STORE end {} events={} frames={} at={}",
                name,
                events,
                frames,
                System.currentTimeMillis());
        completed.add(Map.of("scene", name, "clientFrames", frames, "events", events));
        result.put("scenes", completed);
        frames = events = step = 0;
        observing = false;
        return AUXILIARY_ONLY || ++scene == SCENES.size();
    }

    private static boolean move(Minecraft mc) {
        double angle = frames * 0.025;
        mc.player.setPos(2.5 + Math.sin(angle), 71, 0.5);
        mc.player.setYRot((float) (20 * Math.sin(angle)));
        return true;
    }

    private static boolean edit(Minecraft mc) {
        if (step == 0 && events < 4 && frames >= events * 150) {
            DrawingCensusProbe.command(
                    mc, "setblock 5 71 3 " + (events % 2 == 0 ? "chest" : "air"));
            step = 1;
        }
        if (step == 1) {
            if (!mc.level.getBlockState(EDIT).is(events % 2 == 0 ? Blocks.CHEST : Blocks.AIR))
                return false;
            events++;
            step = 0;
        }
        return true;
    }

    private static boolean chest(Minecraft mc) {
        if (step == 0) {
            var server = mc.getSingleplayerServer();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.openMenu((ChestBlockEntity) player.level().getBlockEntity(CHEST));
                    });
            step = 1;
        }
        if (step == 1) {
            if (!(mc.screen instanceof ContainerScreen)) return false;
            events++;
            step = 2;
        }
        if (step == 2 && frames == 90) {
            mc.player.closeContainer();
            step = 3;
        }
        if (step == 3 && !(mc.screen instanceof ContainerScreen)) {
            events++;
            step = 4;
        }
        return step != 3;
    }

    private static boolean sections(Minecraft mc) {
        boolean loaded = mc.level.getChunkSource().getChunk(0, 0, ChunkStatus.FULL, false) != null;
        if (step == 0) {
            if (!loaded) throw new IllegalStateException("Origin section absent before unload");
            DrawingCensusProbe.command(mc, "tp @a 1024 71 0 0 8");
            step = 1;
        }
        if (step == 1 && mc.player.getX() > 900 && !loaded) {
            events++;
            DrawingCensusProbe.command(mc, "tp @a 2.5 71 0.5 0 8");
            step = 2;
        }
        if (step == 2 && mc.level.getBlockEntity(CHEST) instanceof ChestBlockEntity) {
            events++;
            step = 3;
        }
        return step == 3;
    }

    private static boolean packs(Minecraft mc) {
        if (reload != null) {
            if (!reload.isDone()) return false;
            reload.join();
            reload = null;
            events++;
        }
        if (events < 5 && frames >= events * 120) {
            reload = mc.reloadResourcePacks();
            return false;
        }
        return true;
    }

    private static boolean dimension(Minecraft mc) {
        if (step == 0) {
            DrawingCensusProbe.command(mc, "execute in minecraft:the_end run tp @a 0 80 0 0 0");
            step = 1;
        }
        if (step == 1 && mc.level.dimension() == Level.END) {
            if (++events < 30) return false;
            DrawingCensusProbe.command(
                    mc, "execute in minecraft:overworld run tp @a 2.5 71 0.5 0 8");
            step = 2;
        }
        if (step == 2
                && mc.level.dimension() == Level.OVERWORLD
                && mc.level.getBlockEntity(CHEST) instanceof ChestBlockEntity) {
            events = 2;
            step = 3;
        }
        return step == 3;
    }

    private static boolean auxiliary(Minecraft mc) {
        if (step == 0) {
            mc.setScreen(new AuxiliaryView());
            step = 1;
            events++;
        }
        if (frames + 1 == OBSERVATION_FRAMES) mc.setScreen(null);
        return true;
    }

    private static final class AuxiliaryView extends Screen {
        private static final int SIZE = 256;
        private static final ResourceLocation TARGET =
                ResourceLocation.fromNamespaceAndPath("radiance", "audit/scene_store_auxiliary");
        private static final String[] GROUPS = {"default"};
        private static final int[] VERTEX_COUNTS = {6};
        private static final int[] FACES = {0};
        private final long scene = UiPathTracingProxy.allocateScene();
        private final long[] views = {scene};
        private DynamicTexture output;
        private ByteBuffer triangles;
        private int frame;

        AuxiliaryView() {
            super(Component.literal("Scene store auxiliary view"));
        }

        @Override
        protected void init() {
            if (triangles != null) return;
            try (var allocator = new ByteBufferBuilder(1024)) {
                var quad = new PBRVertexConsumer(allocator, RenderType.solid());
                for (int i = 0; i < 4; i++) {
                    quad.addVertex((i == 0 || i == 3) ? -1 : 1, i < 2 ? -1 : 1, -3)
                            .setColor(255, 255, 255, 255)
                            .setUv(0.5f, 0.5f)
                            .setLight(0)
                            .setOverlay(0)
                            .setNormal(0, 0, 1);
                }
                try (var mesh = quad.endNullable()) {
                    int stride = mesh.vertexBuffer().remaining() / 4;
                    triangles = MemoryUtil.memAlloc(6 * stride);
                    for (int corner : new int[] {0, 1, 2, 2, 3, 0})
                        triangles.put(mesh.vertexBuffer().slice(corner * stride, stride));
                    triangles.flip();
                }
            }
            output = new DynamicTexture(new NativeImage(SIZE, SIZE, true));
            minecraft.getTextureManager().register(TARGET, output);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.flush();
            var owner = TextureProxy.TASKS.owner(output.getId());
            UiPathTracingProxy.beginFrame(views);
            boolean submitted = false;
            try (var stack = MemoryStack.stackPush()) {
                var matrices = stack.mallocFloat(48);
                var identity = new Matrix4f();
                identity.get(0, matrices);
                identity.get(16, matrices);
                new Matrix4f()
                        .perspective((float) Math.toRadians(70), 1, 0.1f, 32)
                        .get(32, matrices);
                submitted =
                        TextureProxy.TASKS.use(
                                owner,
                                () ->
                                        UiPathTracingProxy.trace(
                                                scene,
                                                MemoryUtil.memAddress(triangles),
                                                2,
                                                MemoryUtil.memAddress(matrices),
                                                owner.id(),
                                                SIZE,
                                                SIZE,
                                                frame++,
                                                GROUPS,
                                                VERTEX_COUNTS,
                                                FACES));
            } finally {
                UiPathTracingProxy.endFrame(submitted);
            }
            if (!submitted)
                throw new IllegalStateException("Auxiliary view output ownership changed");
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void removed() {
            UiPathTracingProxy.releaseScenes();
            minecraft.getTextureManager().release(TARGET);
            MemoryUtil.memFree(triangles);
        }
    }

    private static boolean sable(Minecraft mc) throws ReflectiveOperationException {
        if (step == 2) return true;
        if (step == 0) {
            var server = mc.getSingleplayerServer();
            reload = new CompletableFuture<>();
            server.execute(
                    () -> {
                        try {
                            var boundsType =
                                    Class.forName(
                                            "dev.ryanhcode.sable.companion.math.BoundingBox3i");
                            var bounds =
                                    boundsType
                                            .getConstructor(
                                                    int.class, int.class, int.class, int.class,
                                                    int.class, int.class)
                                            .newInstance(2, 71, 3, 2, 71, 3);
                            var helper =
                                    Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
                            var assemble =
                                    java.util.Arrays.stream(helper.getMethods())
                                            .filter(
                                                    method ->
                                                            method.getName()
                                                                            .equals(
                                                                                    "assembleBlocks")
                                                                    && method.getParameterCount()
                                                                            == 4)
                                            .findFirst()
                                            .orElseThrow();
                            assemble.invoke(
                                    null, server.overworld(), CHEST, List.of(CHEST), bounds);
                            reload.complete(null);
                        } catch (ReflectiveOperationException failure) {
                            reload.completeExceptionally(failure);
                        }
                    });
            step = 1;
        }
        if (!reload.isDone()) return false;
        reload.join();
        var type = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        var getter =
                java.util.Arrays.stream(type.getMethods())
                        .filter(
                                method ->
                                        method.getName().equals("getContainer")
                                                && method.getParameterCount() == 1
                                                && method.getParameterTypes()[0].isInstance(
                                                        mc.level))
                        .findFirst()
                        .orElseThrow();
        var container = getter.invoke(null, mc.level);
        var levels = (List<?>) type.getMethod("getAllSubLevels").invoke(container);
        if (levels.isEmpty() || mc.level.getBlockEntity(CHEST) instanceof ChestBlockEntity)
            return false;
        events = levels.size();
        step = 2;
        return true;
    }
}
