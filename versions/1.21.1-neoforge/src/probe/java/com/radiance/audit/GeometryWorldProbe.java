package com.radiance.audit;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Bounded isolated topology observation. Screenshots are evidence, not visual approval. */
public final class GeometryWorldProbe {
    private static final boolean ENABLED =
            "1".equals(ExperimentAccess.getenv("RADIANCE_GEOMETRY_WORLD_PROBE"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long entered;
    private static int step;
    private static CompletableFuture<?> pending;
    private static UUID boatId;
    private static boolean stopped;

    private GeometryWorldProbe() {}

    public static void poll(Minecraft mc) {
        if (!ENABLED || stopped || mc.level == null || mc.player == null) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-audit-test-instance"))
                || !Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Geometry world probe requires isolated markers");
        if (entered == 0) {
            entered = System.nanoTime();
            LOG.info("GEOMETRY_WORLD world-ready");
        }
        long seconds = (System.nanoTime() - entered) / 1_000_000_000L;
        if (pending != null) {
            if (!pending.isDone()) return;
            pending.join();
            pending = null;
        }
        try {
            if (step == 0 && seconds >= 8) {
                step++;
                var server = mc.getSingleplayerServer();
                var dimension = mc.level.dimension();
                var id = mc.player.getUUID();
                if (server == null)
                    throw new IllegalStateException("Geometry probe needs integrated server");
                pending =
                        CompletableFuture.runAsync(
                                () -> {
                                    var level = server.getLevel(dimension);
                                    var player = server.getPlayerList().getPlayer(id);
                                    player.setGameMode(GameType.CREATIVE);
                                    player.getInventory()
                                            .setItem(
                                                    player.getInventory().selected,
                                                    new ItemStack(Items.DIAMOND_SWORD));
                                    HotspotOptimizationProbe.assembleSublevel(
                                            level, new BlockPos(8, 75, 0));
                                    player.teleportTo(.5, 70.0, 18.5);
                                    player.setYRot(180);
                                    player.setXRot(18);
                                },
                                server);
                LOG.info("GEOMETRY_WORLD setup requested (Sable, held item, source factory)");
            } else if (step == 1 && seconds >= 20) {
                step++;
                capture(mc, "ground-factory-items");
                position(mc, -6.5, 70.0, 4.5, 180, 55);
            } else if (step == 2 && seconds >= 30) {
                step++;
                capture(mc, "water-above");
                position(mc, -6.5, 64.7, .5, 180, -15);
            } else if (step == 3 && seconds >= 40) {
                step++;
                capture(mc, "water-below");
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                var dimension = mc.level.dimension();
                pending =
                        CompletableFuture.runAsync(
                                () -> {
                                    var level = server.getLevel(dimension);
                                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                                    Boat boat = EntityType.BOAT.create(level);
                                    if (boat == null)
                                        throw new IllegalStateException("Boat fixture creation");
                                    boat.setPos(-6.5, 67.2, .5);
                                    level.addFreshEntity(boat);
                                    boatId = boat.getUUID();
                                    player.teleportTo(-6.5, 68.2, .5);
                                    if (!player.startRiding(boat, true))
                                        throw new IllegalStateException(
                                                "Boat fixture mount rejected");
                                },
                                server);
            } else if (step == 4 && seconds >= 50) {
                step++;
                capture(mc, "first-person-boat");
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                var dimension = mc.level.dimension();
                pending =
                        CompletableFuture.runAsync(
                                () -> {
                                    var level = server.getLevel(dimension);
                                    var player = server.getPlayerList().getPlayer(id);
                                    if (boatId == null
                                            || level.getEntity(boatId) == null
                                            || !player.isPassenger())
                                        throw new IllegalStateException(
                                                "Boat ownership fixture not reached");
                                    player.stopRiding();
                                    player.setGameMode(GameType.SPECTATOR);
                                    player.teleportTo(.5, 63.0, .5);
                                },
                                server);
            } else if (step == 5 && seconds >= 60) {
                step++;
                capture(mc, "spectator-inside");
                position(mc, .5, 70, 18.5, 180, 18);
            } else if (step == 6 && seconds >= 65) {
                step++;
                pending =
                        mc.reloadResourcePacks()
                                .thenRun(() -> LOG.info("GEOMETRY_WORLD client-reload-complete"));
            } else if (step == 7 && seconds >= 78) {
                step++;
                capture(mc, "after-reload");
                mc.player.connection.sendCommand("reload");
            } else if (step == 8 && seconds >= 90) {
                step++;
                stopped = true;
                LOG.info("GEOMETRY_WORLD normal-stop");
                mc.stop();
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Geometry probe evidence", failure);
        }
    }

    private static void position(
            Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        pending =
                CompletableFuture.runAsync(
                        () -> {
                            var player = server.getPlayerList().getPlayer(id);
                            player.teleportTo(x, y, z);
                            player.setYRot(yaw);
                            player.setXRot(pitch);
                        },
                        server);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static void capture(Minecraft mc, String label) throws java.io.IOException {
        if ("spectator-inside".equals(label)
                && mc.level
                        .getBlockState(
                                BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition()))
                        .isAir())
            throw new IllegalStateException(
                    "Spectator fixture camera is not inside source geometry");
        Files.writeString(
                mc.gameDirectory.toPath().resolve("geometry-" + label + ".json"),
                "{\"x\":"
                        + mc.player.getX()
                        + ",\"y\":"
                        + mc.player.getY()
                        + ",\"z\":"
                        + mc.player.getZ()
                        + ",\"eyeY\":"
                        + mc.gameRenderer.getMainCamera().getPosition().y
                        + ",\"yaw\":"
                        + mc.player.getYRot()
                        + ",\"pitch\":"
                        + mc.player.getXRot()
                        + ",\"passenger\":"
                        + mc.player.isPassenger()
                        + "}");
        Screenshot.grab(
                mc.gameDirectory,
                "geometry-" + label + ".png",
                mc.getMainRenderTarget(),
                message ->
                        LOG.info(
                                "GEOMETRY_WORLD capture={} result={}", label, message.getString()));
    }
}
