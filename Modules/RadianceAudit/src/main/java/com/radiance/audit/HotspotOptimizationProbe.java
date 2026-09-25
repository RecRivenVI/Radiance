package com.radiance.audit;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.audit.mixin.LevelRendererAccessor;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Explicitly enabled, isolated regression only; never part of timed comparisons. */
public final class HotspotOptimizationProbe {
    private static final boolean ENABLED = "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_HOTSPOT_REGRESSION"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long entered, next;
    private static int step;
    private static boolean stopped;
    private static CloudStatus clouds;
    private static BlockPos testPosition;
    private static BlockState original;
    private static CompletableFuture<?> operation;
    private static volatile java.util.UUID sublevelId;

    public static boolean poll(Minecraft mc) {
        if (!ENABLED) return false;
        if (stopped || mc.level == null || mc.player == null) return true;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Hotspot regression requires isolated marker");
        long now = System.nanoTime();
        try {
            if (entered == 0) {
                entered = now; next = now + 35_000_000_000L;
                clouds = mc.options.cloudStatus().get();
                testPosition = mc.player.blockPosition().offset(2, 0, 2);
                LOG.info("HOTSPOT_REGRESSION started cloudMode={} position={}", clouds, testPosition);
            }
            if (now - entered > 210_000_000_000L) throw new IllegalStateException("Regression timed out");
            if (now < next || (operation != null && !operation.isDone())) return true;
            if (operation != null) { operation.join(); operation = null; }
            switch (step++) {
                case 0 -> {
                    MaterialStateProbe.run(mc, "initial");
                    verifyIndex(mc, "initial", false);
                    var server = mc.getSingleplayerServer();
                    if (server == null) throw new IllegalStateException("Requires isolated integrated server");
                    var dimension = mc.level.dimension();
                    operation = CompletableFuture.runAsync(() -> {
                        var level = server.getLevel(dimension);
                        original = level.getBlockState(testPosition);
                        if (level.getBlockEntity(testPosition) != null)
                            throw new IllegalStateException("Fixture must not overwrite a block entity");
                        level.setBlock(testPosition, Blocks.CHEST.defaultBlockState(), 3);
                        if ("1".equals(ExperimentAccess.getenv("RADIANCE_REGRESSION_SABLE")))
                            assembleSublevel(level, testPosition.above(8));
                    }, server);
                    next = now + 10_000_000_000L;
                }
                case 1 -> {
                    verifyIndex(mc, "chest-added", true);
                    var server = mc.getSingleplayerServer(); var dimension = mc.level.dimension();
                    operation = CompletableFuture.runAsync(() ->
                        server.getLevel(dimension).setBlock(testPosition, original, 3), server);
                    next = now + 10_000_000_000L;
                }
                case 2 -> {
                    verifyIndex(mc, "chest-removed", false);
                    operation = mc.reloadResourcePacks();
                    LOG.info("HOTSPOT_REGRESSION F3+T requested");
                    next = now + 20_000_000_000L;
                }
                case 3 -> {
                    MaterialStateProbe.run(mc, "after-reload");
                    verifyIndex(mc, "after-F3+T", false);
                    mc.levelRenderer.allChanged();
                    LOG.info("HOTSPOT_REGRESSION F3+A requested");
                    next = now + 20_000_000_000L;
                }
                case 4 -> {
                    verifyIndex(mc, "after-F3+A", false);
                    mc.options.cloudStatus().set(CloudStatus.OFF);
                    LOG.info("HOTSPOT_REGRESSION clouds disabled temporarily");
                    next = now + 5_000_000_000L;
                }
                case 5 -> {
                    mc.options.cloudStatus().set(clouds);
                    LOG.info("HOTSPOT_REGRESSION clouds restored={}", clouds);
                    next = now + 10_000_000_000L;
                }
                case 6 -> {
                    MaterialStateProbe.run(mc, "final");
                    verifyIndex(mc, "final", false);
                    LOG.info("HOTSPOT_REGRESSION PASS elapsed={} stages=7; no visual approval",
                        (now - entered) / 1e9);
                    stopped = true; mc.stop();
                }
                default -> throw new IllegalStateException("Unexpected regression stage");
            }
        } catch (Exception | LinkageError failure) {
            LOG.error("HOTSPOT_REGRESSION FAIL stage={}; ending normally, not retrying", step, failure);
            stopped = true; mc.stop();
        }
        return true;
    }

    private static void assembleSublevel(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        try {
            var blocks = List.of(origin, origin.offset(1, 0, 0), origin.offset(2, 0, 0));
            for (var pos : blocks)
                if (!level.getBlockState(pos).isAir()) throw new IllegalStateException("Sable fixture area occupied " + pos);
            level.setBlock(blocks.get(0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
            level.setBlock(blocks.get(1), Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 3);
            level.setBlock(blocks.get(2), Blocks.RED_STAINED_GLASS.defaultBlockState(), 3);
            Object bounds = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i")
                .getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
                .newInstance(origin.getX(), origin.getY(), origin.getZ(), origin.getX()+2, origin.getY(), origin.getZ());
            Object sublevel = Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                .getMethod("assembleBlocks", net.minecraft.server.level.ServerLevel.class,
                    BlockPos.class, Iterable.class, Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic"))
                .invoke(null, level, origin, blocks, bounds);
            sublevelId = (java.util.UUID) sublevel.getClass().getMethod("getUniqueId").invoke(sublevel);
            LOG.info("HOTSPOT_REGRESSION assembled Sable id={} origin={} mixedMaterials=3", sublevelId, origin);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Sable fixture assembly", failure); }
    }

    private static void verifyIndex(Minecraft mc, String stage, boolean expectChest) throws Exception {
        if (sublevelId != null) {
            Object container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
                .getMethod("getContainer", net.minecraft.world.level.Level.class).invoke(null, mc.level);
            var sublevels = (java.util.Collection<?>) container.getClass().getMethod("getAllSubLevels").invoke(container);
            boolean found = false;
            for (Object sublevel : sublevels)
                found |= sublevelId.equals(sublevel.getClass().getMethod("getUniqueId").invoke(sublevel));
            if (!found) throw new IllegalStateException("Sable fixture not delivered to client");
            LOG.info("HOTSPOT_REGRESSION Sable delivered stage={} id={}", stage, sublevelId);
        }
        var area = ((LevelRendererAccessor) mc.levelRenderer).radianceAudit$getViewArea();
        var field = ChunkProxy.class.getDeclaredField("blockEntitySections"); field.setAccessible(true);
        Object index = field.get(null);
        synchronized (index) {
            var method = index.getClass().getDeclaredMethod("snapshot"); method.setAccessible(true);
            var entries = (List<?>) method.invoke(index);
            Map<Object, Object> actual = new IdentityHashMap<>();
            boolean chest = false;
            for (Object entry : entries) {
                var section = entry.getClass().getDeclaredMethod("section"); section.setAccessible(true);
                var compiled = entry.getClass().getDeclaredMethod("compiled"); compiled.setAccessible(true);
                actual.put(section.invoke(entry), compiled.invoke(entry));
            }
            int expected = 0;
            for (var section : area.sections) {
                var compiled = section.getCompiled();
                if (compiled.getRenderableBlockEntities().isEmpty()) {
                    if (actual.containsKey(section)) throw new IllegalStateException("Stale empty section");
                    continue;
                }
                expected++;
                if (actual.get(section) != compiled) throw new IllegalStateException("Missing/replaced compiled owner");
                for (var entity : compiled.getRenderableBlockEntities())
                    if (entity.getBlockPos().equals(testPosition)) chest = true;
            }
            if (expected != actual.size() || chest != expectChest)
                throw new IllegalStateException("Presence mismatch: expected=" + expected + " actual="
                    + actual.size() + " chest=" + chest + " expectedChest=" + expectChest);
            LOG.info("HOTSPOT_REGRESSION index-match stage={} fullSlots={} active={} chest={}",
                stage, area.sections.length, actual.size(), chest);
        }
        if ("1".equals(ExperimentAccess.getenv("RADIANCE_REGRESSION_SCREENSHOTS"))) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "regression-" + stage + ".png",
                mc.getMainRenderTarget(), result -> LOG.info("HOTSPOT_REGRESSION screenshot={}", result.getString()));
        }
    }
}
