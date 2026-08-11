package com.radiance.audit;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.system.MemoryUtil;

/**
 * Opt-in isolated original-producer fixture, also runnable without Radiance for the GL reference.
 */
public final class DiagramParityProbe {
    private static final boolean ENABLED =
            "1".equals(ExperimentAccess.getenv("RADIANCE_DIAGRAM_PARITY"));
    private static final boolean SPRING =
            "1".equals(ExperimentAccess.getenv("RADIANCE_DIAGRAM_SPRING"));
    private static final boolean SPRING_SHORT =
            SPRING && "1".equals(ExperimentAccess.getenv("RADIANCE_DIAGRAM_SPRING_SHORT"));
    private static final boolean SPRING_WORLD_ONLY =
            SPRING_SHORT
                    && "1".equals(ExperimentAccess.getenv("RADIANCE_DIAGRAM_SPRING_WORLD_ONLY"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long start;
    private static int step;
    private static volatile UUID fixture;
    private static volatile Throwable serverFailure;
    private static Object diagram;
    private static Object root;
    private static boolean cleaned;
    private static boolean shortCameraSet;
    private static boolean shortWorldCaptured;

    public static void poll(Minecraft mc) {
        if (!ENABLED || mc.level == null || mc.player == null) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Diagram fixture requires isolated marker");
        if (start == 0) {
            verifyPublishedSorting();
            start = System.nanoTime();
            mc.options.pauseOnLostFocus = false;
            LOG.info("DIAGRAM_PARITY world-ready");
        }
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        try {
            if (serverFailure != null)
                throw new IllegalStateException("Diagram fixture assembly failed", serverFailure);
            if (step == 0 && seconds >= 3) {
                step++;
                mc.getSingleplayerServer()
                        .execute(
                                () -> {
                                    try {
                                        var level = mc.getSingleplayerServer().overworld();
                                        level.getGameRules()
                                                .getRule(
                                                        net.minecraft.world.level.GameRules
                                                                .RULE_DOMOBSPAWNING)
                                                .set(false, mc.getSingleplayerServer());
                                        level.getGameRules()
                                                .getRule(
                                                        net.minecraft.world.level.GameRules
                                                                .RULE_RANDOMTICKING)
                                                .set(0, mc.getSingleplayerServer());
                                        var origin = new BlockPos(8, 128, 8);
                                        int maxX = SPRING ? 7 : 6;
                                        for (var pos :
                                                BlockPos.betweenClosed(
                                                        origin, origin.offset(maxX, 4, 6)))
                                            if (!level.getBlockState(pos).isAir())
                                                throw new IllegalStateException(
                                                        "Fixture area occupied " + pos);
                                        for (int x = 0; x <= maxX; x++)
                                            for (int z = 0; z < 7; z++)
                                                level.setBlock(
                                                        origin.offset(x, 0, z),
                                                        Blocks.IRON_BLOCK.defaultBlockState(),
                                                        3);
                                        if (SPRING) {
                                            var spring =
                                                    BuiltInRegistries.BLOCK.get(
                                                            ResourceLocation.fromNamespaceAndPath(
                                                                    "simulated", "spring"));
                                            if (spring == Blocks.AIR)
                                                throw new IllegalStateException(
                                                        "Simulated spring block missing");
                                            BlockPos controller = origin.offset(1, 1, 3),
                                                    partner = origin.offset(6, 1, 3);
                                            level.setBlock(
                                                    origin.offset(0, 1, 3),
                                                    Blocks.IRON_BLOCK.defaultBlockState(),
                                                    3);
                                            level.setBlock(
                                                    origin.offset(7, 1, 3),
                                                    Blocks.IRON_BLOCK.defaultBlockState(),
                                                    3);
                                            level.setBlock(
                                                    controller,
                                                    spring.defaultBlockState()
                                                            .setValue(
                                                                    net.minecraft.world.level.block
                                                                            .DirectionalBlock
                                                                            .FACING,
                                                                    net.minecraft.core.Direction
                                                                            .EAST),
                                                    3);
                                            level.setBlock(
                                                    partner,
                                                    spring.defaultBlockState()
                                                            .setValue(
                                                                    net.minecraft.world.level.block
                                                                            .DirectionalBlock
                                                                            .FACING,
                                                                    net.minecraft.core.Direction
                                                                            .WEST),
                                                    3);
                                            configureSpringPair(level, controller, partner);
                                        } else {
                                            for (int z = 1; z < 6; z++) {
                                                level.setBlock(
                                                        origin.offset(1, 1, z),
                                                        Blocks.OAK_LEAVES
                                                                .defaultBlockState()
                                                                .setValue(
                                                                        net.minecraft.world.level
                                                                                .block.LeavesBlock
                                                                                .PERSISTENT,
                                                                        true),
                                                        3);
                                                level.setBlock(
                                                        origin.offset(3, 1, z),
                                                        Blocks.RED_STAINED_GLASS
                                                                .defaultBlockState(),
                                                        3);
                                                level.setBlock(
                                                        origin.offset(4, 1, z),
                                                        Blocks.BLUE_STAINED_GLASS
                                                                .defaultBlockState(),
                                                        3);
                                                level.setBlock(
                                                        origin.offset(5, 1, z),
                                                        Blocks.COBWEB.defaultBlockState(),
                                                        3);
                                            }
                                            level.setBlock(
                                                    origin.offset(2, 1, 2),
                                                    Blocks.STONE_SLAB.defaultBlockState(),
                                                    3);
                                            level.setBlock(
                                                    origin.offset(2, 1, 4),
                                                    Blocks.CHEST.defaultBlockState(),
                                                    3);
                                        }
                                        List<BlockPos> blocks = new ArrayList<>();
                                        for (var pos :
                                                BlockPos.betweenClosed(
                                                        origin, origin.offset(maxX, 4, 6)))
                                            if (!level.getBlockState(pos).isAir())
                                                blocks.add(pos.immutable());
                                        Object bounds =
                                                Class.forName(
                                                                "dev.ryanhcode.sable.companion.math.BoundingBox3i")
                                                        .getConstructor(
                                                                int.class, int.class, int.class,
                                                                int.class, int.class, int.class)
                                                        .newInstance(8, 128, 8, 8 + maxX, 132, 14);
                                        Object assembled =
                                                Class.forName(
                                                                "dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                                                        .getMethod(
                                                                "assembleBlocks",
                                                                net.minecraft.server.level
                                                                        .ServerLevel.class,
                                                                BlockPos.class,
                                                                Iterable.class,
                                                                Class.forName(
                                                                        "dev.ryanhcode.sable.companion.math.BoundingBox3ic"))
                                                        .invoke(
                                                                null, level, origin, blocks,
                                                                bounds);
                                        fixture = (UUID) call(assembled, "getUniqueId");
                                        LOG.info(
                                                "DIAGRAM_PARITY assembled blocks={}",
                                                blocks.size());
                                        LOG.info("DIAGRAM_PARITY assembled {}", fixture);
                                    } catch (Throwable ex) {
                                        serverFailure = ex;
                                    }
                                });
            }
            if (!cleaned && seconds >= 5 && fixture != null) {
                cleaned = true;
                mc.getSingleplayerServer()
                        .execute(
                                () -> {
                                    try {
                                        Object helper =
                                                Class.forName("dev.ryanhcode.sable.Sable")
                                                        .getField("HELPER")
                                                        .get(null);
                                        List<net.minecraft.world.entity.Entity> remove =
                                                new ArrayList<>();
                                        for (var entity :
                                                mc.getSingleplayerServer()
                                                        .overworld()
                                                        .getAllEntities()) {
                                            if (entity
                                                    instanceof
                                                    net.minecraft.world.entity.player.Player)
                                                continue;
                                            for (String method :
                                                    List.of(
                                                            "getContaining",
                                                            "getTrackingOrVehicleSubLevel")) {
                                                Object owner =
                                                        helper.getClass()
                                                                .getMethod(
                                                                        method,
                                                                        net.minecraft.world.entity
                                                                                .Entity.class)
                                                                .invoke(helper, entity);
                                                if (owner != null
                                                        && fixture.equals(
                                                                call(owner, "getUniqueId"))) {
                                                    remove.add(entity);
                                                    break;
                                                }
                                            }
                                        }
                                        for (var entity : remove) {
                                            LOG.info(
                                                    "DIAGRAM_PARITY removing nondeterministic fixture entity {}",
                                                    entity.getType());
                                            entity.discard();
                                        }
                                    } catch (Throwable failure) {
                                        serverFailure = failure;
                                    }
                                });
            }
            if (step == 1 && seconds >= 10 && fixture != null) {
                Object container =
                        Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
                                .getMethod("getContainer", Level.class)
                                .invoke(null, mc.level);
                Object target = null;
                for (Object candidate : (Collection<?>) call(container, "getAllSubLevels"))
                    if (fixture.equals(call(candidate, "getUniqueId"))) {
                        target = candidate;
                        break;
                    }
                if (target == null) {
                    if (seconds > 35) throw new IllegalStateException("Fixture not received");
                    return;
                }
                root = target;
                if (SPRING) {
                    try {
                        verifySpringPair(target);
                    } catch (IllegalStateException notReady) {
                        if (seconds < 35) return;
                        throw notReady;
                    }
                }
                if (SPRING_SHORT) {
                    if (!shortCameraSet) {
                        shortCameraSet = true;
                        UUID playerId = mc.player.getUUID();
                        mc.getSingleplayerServer()
                                .execute(
                                        () -> {
                                            try {
                                                var player =
                                                        mc.getSingleplayerServer()
                                                                .getPlayerList()
                                                                .getPlayer(playerId);
                                                if (player == null)
                                                    throw new IllegalStateException(
                                                            "Fixture player missing");
                                                player.connection.teleport(
                                                        11.5, 130.5, 19.5, 180, 18);
                                            } catch (Throwable failure) {
                                                serverFailure = failure;
                                            }
                                        });
                        LOG.info(
                                "DIAGRAM_PARITY short-camera requested 11.5,130.5,19.5 yaw180 pitch18");
                        return;
                    }
                    if (!shortWorldCaptured) {
                        if (seconds < 15) return;
                        if (mc.player.position().distanceToSqr(11.5, 130.5, 19.5) > 4) {
                            if (seconds < 25) return;
                            throw new IllegalStateException(
                                    "Fixture camera teleport not reflected on client");
                        }
                        mc.player.setYRot(180);
                        mc.player.yRotO = 180;
                        mc.player.setXRot(18);
                        mc.player.xRotO = 18;
                        float renderPitch = mc.gameRenderer.getMainCamera().getXRot();
                        float renderYaw = mc.gameRenderer.getMainCamera().getYRot();
                        if (Math.abs(renderPitch - 18) > 2
                                || Math.abs(net.minecraft.util.Mth.wrapDegrees(renderYaw - 180))
                                        > 2) {
                            if (seconds < 25) return;
                            throw new IllegalStateException(
                                    "Actual render camera not aimed at spring: yaw="
                                            + renderYaw
                                            + " pitch="
                                            + renderPitch);
                        }
                        captureShortWorld(mc);
                        shortWorldCaptured = true;
                        return;
                    }
                    if (SPRING_WORLD_ONLY) {
                        if (seconds < 19
                                || !Files.isRegularFile(
                                        mc.gameDirectory
                                                .toPath()
                                                .resolve("screenshots/spring-world-short.png"))) {
                            if (seconds < 30) return;
                            throw new IllegalStateException("World-only screenshot did not finish");
                        }
                        step++;
                        LOG.info("DIAGRAM_PARITY spring-world-only normal-stop");
                        mc.stop();
                        return;
                    }
                    if (seconds < 19) return;
                    if (!Files.isRegularFile(
                            mc.gameDirectory
                                    .toPath()
                                    .resolve("screenshots/spring-world-short.png"))) {
                        if (seconds < 30) return;
                        throw new IllegalStateException("Short-world screenshot did not finish");
                    }
                }
                var type =
                        BuiltInRegistries.ENTITY_TYPE.get(
                                ResourceLocation.fromNamespaceAndPath(
                                        "simulated", "contraption_diagram"));
                Object entity = type.create(mc.level);
                Object config =
                        Class.forName(
                                        "dev.simulated_team.simulated.content.entities.diagram.DiagramConfig")
                                .getMethod("makeDefault", entity.getClass())
                                .invoke(null, entity);
                var screen =
                        Class.forName(
                                "dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen");
                for (var method : screen.getMethods())
                    if (method.getName().equals("open"))
                        method.invoke(null, entity, config, target);
                diagram = mc.screen;
                step++;
                LOG.info("DIAGRAM_PARITY opened {}", fixture);
            } else if (SPRING_SHORT && step == 2 && seconds >= 25) {
                capture(mc, "initial");
                step++;
                mc.screen.onClose();
            } else if (SPRING_SHORT && step == 3 && seconds >= 29) {
                step++;
                LOG.info("DIAGRAM_PARITY spring-short normal-stop");
                mc.stop();
            } else if (!SPRING_SHORT && step == 2 && seconds >= 16) {
                capture(mc, "initial");
                step++;
                ((org.joml.Quaternionf) diagram.getClass().getField("LOCAL_ORIENTATION").get(null))
                        .rotateY(1.5707963f);
            } else if (!SPRING_SHORT && step == 3 && seconds >= 21) {
                capture(mc, "rotated");
                step++;
                Object note = field(diagram, "note");
                note.getClass().getMethod("activate").invoke(note);
            } else if (!SPRING_SHORT && step == 4 && seconds >= 27) {
                capture(mc, "note");
                step++;
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                diagram = mc.screen;
            } else if (!SPRING_SHORT && step == 5 && seconds >= 33) {
                capture(mc, "scale3");
                step++;
                mc.reloadResourcePacks();
            } else if (!SPRING_SHORT && step == 6 && seconds >= 42 && mc.getOverlay() == null) {
                capture(mc, "reloaded");
                step++;
                mc.screen.onClose();
            } else if (!SPRING_SHORT && step == 7 && seconds >= 46) {
                step++;
                LOG.info("DIAGRAM_PARITY normal-stop");
                mc.stop();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Diagram parity fixture step " + step, ex);
        }
    }

    private static Object call(Object o, String name) throws ReflectiveOperationException {
        return o.getClass().getMethod(name).invoke(o);
    }

    private static void captureShortWorld(Minecraft mc) throws Exception {
        var dir = mc.gameDirectory.toPath().resolve("diagram-parity");
        Files.createDirectories(dir);
        var position = mc.player.position();
        float renderYaw = mc.gameRenderer.getMainCamera().getYRot();
        float renderPitch = mc.gameRenderer.getMainCamera().getXRot();
        if (Math.abs(renderPitch - 18) > 2
                || Math.abs(net.minecraft.util.Mth.wrapDegrees(renderYaw - 180)) > 2)
            throw new IllegalStateException(
                    "World capture render camera differs from spring fixture");
        Files.writeString(
                dir.resolve("short-world.json"),
                "{\"x\":"
                        + position.x
                        + ",\"y\":"
                        + position.y
                        + ",\"z\":"
                        + position.z
                        + ",\"yaw\":"
                        + mc.player.getYRot()
                        + ",\"pitch\":"
                        + mc.player.getXRot()
                        + ",\"renderYaw\":"
                        + renderYaw
                        + ",\"renderPitch\":"
                        + renderPitch
                        + ",\"springPairValid\":true}");
        Screenshot.grab(
                mc.gameDirectory,
                "spring-world-short.png",
                mc.getMainRenderTarget(),
                message ->
                        LOG.info("DIAGRAM_PARITY short-world screenshot {}", message.getString()));
        LOG.info(
                "DIAGRAM_PARITY short-world captured player={} yaw={} pitch={}",
                position,
                mc.player.getYRot(),
                mc.player.getXRot());
    }

    private static void configureSpringPair(Level level, BlockPos controller, BlockPos partner)
            throws Exception {
        Class<?> type =
                Class.forName(
                        "dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity");
        Object first = level.getBlockEntity(controller), second = level.getBlockEntity(partner);
        if (!type.isInstance(first) || !type.isInstance(second))
            throw new IllegalStateException("Spring pair block entities missing before assembly");
        type.getMethod("setController", boolean.class).invoke(first, true);
        type.getMethod("setController", boolean.class).invoke(second, false);
        type.getMethod("setDesiredLength", double.class).invoke(first, 6.0);
        type.getMethod("setDesiredLength", double.class).invoke(second, 6.0);
        type.getMethod("setPartnerPos", BlockPos.class, UUID.class).invoke(first, partner, null);
        type.getMethod("setPartnerPos", BlockPos.class, UUID.class)
                .invoke(second, controller, null);
        ((net.minecraft.world.level.block.entity.BlockEntity) first).setChanged();
        ((net.minecraft.world.level.block.entity.BlockEntity) second).setChanged();
        LOG.info(
                "DIAGRAM_PARITY spring-source controller={} partner={} length=6.0",
                controller,
                partner);
    }

    private static void verifySpringPair(Object subLevel) throws Exception {
        Level level = (Level) call(subLevel, "getLevel");
        Object plot = call(subLevel, "getPlot");
        net.minecraft.world.phys.AABB box =
                (net.minecraft.world.phys.AABB) call(call(plot, "getBoundingBox"), "toAABB");
        long volume =
                (long) (Math.ceil(box.maxX) - Math.floor(box.minX) + 1)
                        * (long) (Math.ceil(box.maxY) - Math.floor(box.minY) + 1)
                        * (long) (Math.ceil(box.maxZ) - Math.floor(box.minZ) + 1);
        if (volume > 100000)
            throw new IllegalStateException("Spring fixture plot too large to scan: " + volume);
        Class<?> type =
                Class.forName(
                        "dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity");
        int count = 0;
        Object controller = null, partner = null;
        for (var pos :
                BlockPos.betweenClosed(
                        BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            Object be = level.getBlockEntity(pos);
            if (!type.isInstance(be)) continue;
            count++;
            if ((Boolean) call(be, "isController")) {
                controller = be;
                partner = call(be, "getPairedSpring");
            }
        }
        if (count != 2 || controller == null || !type.isInstance(partner))
            throw new IllegalStateException(
                    "Diagram spring producer missing: count="
                            + count
                            + " controller="
                            + (controller != null)
                            + " partner="
                            + (partner != null));
        LOG.info(
                "DIAGRAM_PARITY spring-client controller={} partner={} sublevel={}",
                ((net.minecraft.world.level.block.entity.BlockEntity) controller).getBlockPos(),
                ((net.minecraft.world.level.block.entity.BlockEntity) partner).getBlockPos(),
                call(subLevel, "getUniqueId"));
    }

    private static void verifyPublishedSorting() {
        if (!net.neoforged.fml.ModList.get().isLoaded("radiance")) return;
        try {
            var type = Class.forName("com.radiance.client.render.RasterCompiledSection");
            var constructor = type.getConstructor(com.mojang.blaze3d.vertex.VertexSorting.class);
            Object first =
                    constructor.newInstance(
                            com.mojang.blaze3d.vertex.VertexSorting.byDistance(0, 0, 0));
            Object next =
                    constructor.newInstance(
                            com.mojang.blaze3d.vertex.VertexSorting.byDistance(10, 0, 0));
            var centers =
                    new org.joml.Vector3f[] {
                        new org.joml.Vector3f(1, 0, 0), new org.joml.Vector3f(9, 0, 0)
                    };
            var firstPolicy =
                    (com.mojang.blaze3d.vertex.VertexSorting) call(first, "rasterSorting");
            var nextPolicy = (com.mojang.blaze3d.vertex.VertexSorting) call(next, "rasterSorting");
            if (!Arrays.equals(firstPolicy.sort(centers), new int[] {1, 0})
                    || !Arrays.equals(nextPolicy.sort(centers), new int[] {0, 1})
                    || !Arrays.equals(firstPolicy.sort(centers), new int[] {1, 0}))
                throw new IllegalStateException(
                        "Published section sorting changed with another generation");
            LOG.info("DIAGRAM_PARITY compile-generation sorting PASS");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        if (mc.screen != diagram)
            throw new IllegalStateException("Diagram unexpectedly closed before " + name);
        if (SPRING) verifySpringPair(root);
        var dir = mc.gameDirectory.toPath().resolve("diagram-parity");
        Files.createDirectories(dir);
        Object helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
        List<String> members = new ArrayList<>();
        var chain =
                (Collection<?>)
                        Class.forName(
                                        "dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer")
                                .getMethod(
                                        "getRenderedChain",
                                        Class.forName(
                                                "dev.ryanhcode.sable.sublevel.ClientSubLevel"))
                                .invoke(null, root);
        for (Object member : chain)
            members.add("sublevel " + call(member, "getUniqueId") + " " + call(member, "getPlot"));
        for (var entity : mc.level.entitiesForRendering()) {
            Object containing =
                    helper.getClass()
                            .getMethod("getContaining", net.minecraft.world.entity.Entity.class)
                            .invoke(helper, entity);
            Object tracking =
                    helper.getClass()
                            .getMethod(
                                    "getTrackingOrVehicleSubLevel",
                                    net.minecraft.world.entity.Entity.class)
                            .invoke(helper, entity);
            if (chain.contains(containing) || chain.contains(tracking))
                members.add(
                        "entity "
                                + entity.getId()
                                + " "
                                + entity.getType()
                                + " "
                                + entity.position());
        }
        Files.write(dir.resolve(name + "-members.txt"), members);
        boolean vulkan = net.neoforged.fml.ModList.get().isLoaded("radiance");
        for (String key : List.of("fbo", "finalFbo")) {
            Object fbo = field(diagram, key);
            int w = (Integer) call(fbo, "getWidth"), h = (Integer) call(fbo, "getHeight");
            call(fbo, "bindRead");
            ByteBuffer rgba = MemoryUtil.memAlloc(w * h * 4);
            try {
                if (vulkan)
                    Class.forName("com.radiance.client.proxy.vulkan.FramebufferProxy")
                            .getMethod(
                                    "readPixels",
                                    int.class,
                                    int.class,
                                    int.class,
                                    int.class,
                                    int.class,
                                    int.class,
                                    long.class)
                            .invoke(null, 0, 0, w, h, 0x1908, 0x1401, MemoryUtil.memAddress(rgba));
                else org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, 0x1908, 0x1401, rgba);
                byte[] bytes = new byte[rgba.remaining()];
                rgba.get(bytes);
                Files.write(dir.resolve(name + "-" + key + ".rgba"), bytes);
                try (var image = new NativeImage(w, h, false)) {
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) {
                            int p = (y * w + x) * 4;
                            int abgr =
                                    (bytes[p] & 255)
                                            | ((bytes[p + 1] & 255) << 8)
                                            | ((bytes[p + 2] & 255) << 16)
                                            | ((bytes[p + 3] & 255) << 24);
                            image.setPixelRGBA(x, h - y - 1, abgr);
                        }
                    image.writeToFile(dir.resolve(name + "-" + key + ".png"));
                }
                Files.writeString(
                        dir.resolve(name + "-" + key + ".json"),
                        "{\"width\":" + w + ",\"height\":" + h + ",\"vulkan\":" + vulkan + "}");
                if (key.equals("fbo")) {
                    rgba.clear();
                    if (vulkan)
                        Class.forName("com.radiance.client.proxy.vulkan.FramebufferProxy")
                                .getMethod(
                                        "readPixels",
                                        int.class,
                                        int.class,
                                        int.class,
                                        int.class,
                                        int.class,
                                        int.class,
                                        long.class)
                                .invoke(
                                        null,
                                        0,
                                        0,
                                        w,
                                        h,
                                        0x1902,
                                        0x1406,
                                        MemoryUtil.memAddress(rgba));
                    else {
                        org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, 0x1902, 0x1406, rgba);
                        int depthBits =
                                org.lwjgl.opengl.GL30.glGetFramebufferAttachmentParameteri(
                                        org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,
                                        org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT,
                                        org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE);
                        Files.writeString(
                                dir.resolve(name + "-depth-bits.txt"), Integer.toString(depthBits));
                    }
                    rgba.get(bytes);
                    Files.write(dir.resolve(name + "-depth.f32"), bytes);
                }
                LOG.info("DIAGRAM_PARITY captured {} {} {}x{}", name, key, w, h);
            } finally {
                MemoryUtil.memFree(rgba);
                Class.forName("foundry.veil.api.client.render.framebuffer.AdvancedFbo")
                        .getMethod("unbind")
                        .invoke(null);
            }
        }
        var mainTarget = mc.getMainRenderTarget();
        if (mainTarget.width > 0 && mainTarget.height > 0)
            Screenshot.grab(
                    mc.gameDirectory,
                    "diagram-" + name + ".png",
                    mainTarget,
                    c -> LOG.info("DIAGRAM_PARITY screenshot {}", c.getString()));
        else
            LOG.warn(
                    "DIAGRAM_PARITY screenshot skipped: main target {}x{}",
                    mainTarget.width,
                    mainTarget.height);
    }
}
