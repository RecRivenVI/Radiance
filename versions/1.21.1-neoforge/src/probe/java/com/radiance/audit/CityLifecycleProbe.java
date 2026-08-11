package com.radiance.audit;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Owned city lifecycle; ordinary player input and server entity APIs, no renderer setters. */
final class CityLifecycleProbe {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Gson JSON = new Gson();
    private static final List<UUID> entities = new ArrayList<>(); // Server-thread owned.
    private static String phase = "initial";
    private static long started, since, nextSample, quietSince, nextAnimation;
    private static double originX, originY, originZ, minX, maxX;
    private static float originYaw, originPitch;
    private static int direction = 1, turns, animateStep;
    private static boolean reentered;
    private static BufferedWriter output;
    private static CompletableFuture<?> operation;

    private CityLifecycleProbe() {}

    static void poll(Minecraft mc) {
        if (phase.equals("done")) return;
        long now = System.nanoTime();
        try {
            if (started != 0 && now - started > 1_100_000_000_000L)
                throw new IllegalStateException("City lifecycle exceeded bounded duration");
            if (phase.equals("initial")) {
                if (!(mc.screen instanceof TitleScreen)
                        || mc.getOverlay() != null
                        || !CityFrameComparisonProbe.viewportReady(mc, now)) return;
                if (!Files.isRegularFile(
                        mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")))
                    throw new IllegalStateException("Owned lifecycle fixture required");
                output =
                        Files.newBufferedWriter(
                                Path.of(
                                        System.getProperty("validation.result"),
                                        "lifecycle.jsonl"));
                EffectiveRenderSettings.checkpoint(mc, "lifecycle");
                if (FrameProfiler.request(1120, "city-lifecycle", ProfileMode.FRAME_ONLY) != 1)
                    throw new IllegalStateException("Another capture is active");
                started = now;
                change("title-before", now);
            }
            Properties sampled = null;
            if (now >= nextSample) {
                nextSample = now + 1_000_000_000L;
                sampled = record(mc, now);
            }
            switch (phase) {
                case "title-before" -> {
                    if (elapsed(now, 15) && FrameProfiler.collecting()) open(mc, now);
                }
                case "opening" -> {
                    if (mc.level != null && mc.player != null) {
                        if (!reentered) {
                            originX = mc.player.getX();
                            originY = mc.player.getY();
                            originZ = mc.player.getZ();
                            originYaw = mc.player.getYRot();
                            originPitch = mc.player.getXRot();
                        }
                        mc.setScreen(new ChatScreen(""));
                        LOG.info("CITY_COMPARISON world-ready lifecycle=true");
                        change(reentered ? "reenter-loading" : "loading", now);
                    }
                }
                case "loading", "reenter-loading", "route-settling" -> {
                    if (sampled == null) return;
                    if (operation != null && !operation.isDone()) return;
                    if (operation != null) {
                        operation.join();
                        operation = null;
                    }
                    var state =
                            sampled; // capture drains native work counters; share the single 1 Hz
                    // snapshot.
                    if (CityFrameComparisonProbe.quiet(state)
                            && BenchmarkChunkState.noNativeWork(state)) {
                        if (quietSince == 0) quietSince = now;
                        if (now - quietSince >= 10_000_000_000L)
                            change(
                                    reentered
                                            ? "reenter-steady"
                                            : phase.equals("route-settling")
                                                    ? "post-route-steady"
                                                    : "steady",
                                    now);
                    } else quietSince = 0;
                }
                case "steady" -> {
                    if (elapsed(now, 20)) {
                        if (!mc.player.getAbilities().flying)
                            throw new IllegalStateException(
                                    "Frozen city must already be in creative flight");
                        var uuid = mc.player.getUUID();
                        var server = mc.getSingleplayerServer();
                        operation =
                                server.submit(
                                        () -> {
                                            var p = server.getPlayerList().getPlayer(uuid);
                                            p.teleportTo(
                                                    p.serverLevel(), originX, 384, originZ, -90, 0);
                                        });
                        change("flight-positioning", now);
                    }
                }
                case "flight-positioning" -> {
                    if (!operation.isDone()) return;
                    operation.join();
                    if (Math.abs(mc.player.getY() - 384) > .01) return;
                    operation = null;
                    minX = maxX = mc.player.getX();
                    direction = 1;
                    mc.setScreen(null);
                    mc.options.keyUp.setDown(true);
                    change("flying", now);
                }
                case "flying" -> {
                    minX = Math.min(minX, mc.player.getX());
                    maxX = Math.max(maxX, mc.player.getX());
                    if ((direction > 0 && mc.player.getX() > originX + 128)
                            || (direction < 0 && mc.player.getX() < originX - 128)) {
                        direction = -direction;
                        ++turns;
                        mc.player.setYRot(direction > 0 ? -90 : 90);
                    }
                    if (elapsed(now, 180)) {
                        mc.options.keyUp.setDown(false);
                        if (maxX - minX < 100 || turns < 2)
                            throw new IllegalStateException(
                                    "Flight route did not actually move/return");
                        mc.setScreen(new ChatScreen(""));
                        var uuid = mc.player.getUUID();
                        var server = mc.getSingleplayerServer();
                        operation =
                                server.submit(
                                        () -> {
                                            var p = server.getPlayerList().getPlayer(uuid);
                                            p.teleportTo(
                                                    p.serverLevel(),
                                                    originX,
                                                    originY,
                                                    originZ,
                                                    originYaw,
                                                    originPitch);
                                        });
                        change("route-settling", now);
                    }
                }
                case "post-route-steady" -> {
                    if (elapsed(now, 20)) {
                        var uuid = mc.player.getUUID();
                        var server = mc.getSingleplayerServer();
                        operation =
                                server.submit(
                                        () -> {
                                            var player = server.getPlayerList().getPlayer(uuid);
                                            var world = player.serverLevel();
                                            for (int i = 0; i < 128; ++i) {
                                                Entity e =
                                                        (i < 64
                                                                        ? EntityType.ARMOR_STAND
                                                                        : EntityType.ZOMBIE)
                                                                .create(world);
                                                if (e == null)
                                                    throw new IllegalStateException(
                                                            "Entity factory failed");
                                                e.setNoGravity(true);
                                                e.setInvulnerable(true);
                                                if (e instanceof Mob mob) {
                                                    mob.setNoAi(true);
                                                    mob.setPersistenceRequired();
                                                }
                                                e.setPos(
                                                        originX - 16 + i % 16 * 2,
                                                        originY - 4,
                                                        originZ + 8 + i / 16 * 2);
                                                if (e instanceof LivingEntity living) {
                                                    var gear =
                                                            new net.minecraft.world.item.ItemStack(
                                                                    net.minecraft.world.item.Items
                                                                            .IRON_HELMET);
                                                    living.setItemSlot(
                                                            net.minecraft.world.entity.EquipmentSlot
                                                                    .HEAD,
                                                            gear);
                                                }
                                                if (!world.addFreshEntity(e))
                                                    throw new IllegalStateException(
                                                            "Entity insertion failed");
                                                entities.add(e.getUUID());
                                            }
                                        });
                        change("entities-create", now);
                    }
                }
                case "entities-create" -> {
                    if (operation.isDone()) {
                        operation.join();
                        operation = null;
                        change("entities-still", now);
                    }
                }
                case "entities-still" -> {
                    if (elapsed(now, 20)) change("entities-moving", now);
                }
                case "entities-moving" -> {
                    if (operation != null && operation.isDone()) {
                        operation.join();
                        operation = null;
                    }
                    if (elapsed(now, 30) && operation == null) {
                        change("entities-remove", now);
                        return;
                    }
                    if (operation == null && now >= nextAnimation) {
                        nextAnimation = now + 200_000_000L;
                        int frame = ++animateStep;
                        var server = mc.getSingleplayerServer();
                        var uuid = mc.player.getUUID();
                        operation =
                                server.submit(
                                        () -> {
                                            var world =
                                                    server.getPlayerList()
                                                            .getPlayer(uuid)
                                                            .serverLevel();
                                            for (int i = 0; i < entities.size(); ++i) {
                                                Entity e = world.getEntity(entities.get(i));
                                                if (e == null)
                                                    throw new IllegalStateException(
                                                            "Owned entity disappeared");
                                                e.teleportTo(
                                                        originX
                                                                - 16
                                                                + i % 16 * 2
                                                                + Math.sin(frame * .1),
                                                        originY - 4,
                                                        originZ + 8 + i / 16 * 2);
                                                e.setYRot(frame * 9);
                                                e.setXRot((float) (Math.sin(frame * .1) * 25));
                                                if (e instanceof LivingEntity living)
                                                    living.yBodyRot = frame * 9;
                                            }
                                        });
                    }
                }
                case "entities-remove" -> {
                    if (operation == null) {
                        var uuid = mc.player.getUUID();
                        var server = mc.getSingleplayerServer();
                        operation =
                                server.submit(
                                        () -> {
                                            var world =
                                                    server.getPlayerList()
                                                            .getPlayer(uuid)
                                                            .serverLevel();
                                            for (UUID id : entities) {
                                                var e = world.getEntity(id);
                                                if (e != null) e.discard();
                                            }
                                            entities.clear();
                                        });
                    } else if (operation.isDone()) {
                        operation.join();
                        operation = null;
                        change("after-remove", now);
                    }
                }
                case "after-remove" -> {
                    if (elapsed(now, 40)) leave(mc, now);
                }
                case "title-after" -> {
                    if (elapsed(now, 40)) {
                        if (!reentered) {
                            reentered = true;
                            open(mc, now);
                        } else {
                            FrameProfiler.requestStop();
                            change("flushing", now);
                        }
                    }
                }
                case "reenter-steady" -> {
                    if (elapsed(now, 20)) leave(mc, now);
                }
                case "flushing" -> {
                    if (FrameProfiler.requested()) return;
                    try (var paths =
                            Files.list(
                                    Path.of(System.getProperty("validation.result"), "profiles"))) {
                        if (!paths.filter(Files::isDirectory)
                                .allMatch(p -> Files.isRegularFile(p.resolve("STATUS.txt"))))
                            return;
                    }
                    record(mc, now);
                    output.close();
                    phase = "done";
                    CityFrameComparisonProbe.writeSourceObservations();
                    LOG.info(
                            "CITY_LIFECYCLE normal-stop turns={} xRange={}..{}", turns, minX, maxX);
                    mc.stop();
                }
                default -> {}
            }
        } catch (Exception | LinkageError failure) {
            mc.options.keyUp.setDown(false);
            phase = "done";
            LOG.error("CITY_LIFECYCLE FAIL; preserve evidence, no retry", failure);
            FrameProfiler.requestStop();
            try {
                if (output != null) output.close();
            } catch (java.io.IOException close) {
                failure.addSuppressed(close);
            }
            mc.stop();
        }
    }

    private static boolean elapsed(long now, int seconds) {
        return now - since >= seconds * 1_000_000_000L;
    }

    private static void change(String next, long now) {
        phase = next;
        since = now;
        quietSince = 0;
        LOG.info("CITY_LIFECYCLE phase={}", next);
    }

    private static void open(Minecraft mc, long now) {
        change("opening", now);
        mc.createWorldOpenFlows().openWorld("Newport City", () -> mc.setScreen(new TitleScreen()));
    }

    private static void leave(Minecraft mc, long now) {
        change("leaving", now); // disconnect runs nested client frames.
        mc.level.disconnect();
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
        change("title-after", System.nanoTime());
    }

    private static Properties record(Minecraft mc, long now)
            throws java.io.IOException, ReflectiveOperationException {
        var row = new LinkedHashMap<String, Object>();
        row.put("time", java.time.Instant.now().toString());
        row.put("elapsedS", (now - started) / 1e9);
        row.put("phase", phase);
        row.put("world", mc.level != null);
        row.put("turns", turns);
        var state = new Properties();
        BenchmarkChunkState.capture(state);
        row.put("chunks", state);
        row.put("heapUsed", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        row.put(
                "heapCommitted",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted());
        for (var pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
            row.put(
                    "buffer-" + pool.getName(),
                    java.util.Map.of(
                            "count",
                            pool.getCount(),
                            "used",
                            pool.getMemoryUsed(),
                            "capacity",
                            pool.getTotalCapacity()));
        if (mc.player != null)
            row.put(
                    "camera",
                    java.util.List.of(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (mc.level != null) row.put("clientEntities", mc.level.getEntityCount());
        output.write(JSON.toJson(row));
        output.newLine();
        output.flush();
        return state;
    }
}
