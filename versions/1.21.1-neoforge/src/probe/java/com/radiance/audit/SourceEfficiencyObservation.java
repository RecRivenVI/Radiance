package com.radiance.audit;

import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Same probe for both frozen products; optional source APIs are observed through reflection. */
final class SourceEfficiencyObservation {
    private static long[] previous;
    private static long changed, started;
    private static int move = -1;
    private static double x, y, z;
    private static float yaw, pitch;
    private static CompletableFuture<Void> pending;

    static boolean quiet(long now) throws Exception {
        if (!Boolean.getBoolean("radiance.audit.sourceEfficiencyVoxy")) return true;
        long[] counters = counters();
        if (counters.length < 18) throw new IllegalStateException("Voxy source is not active");
        long[] stable = Arrays.copyOf(counters, 13);
        stable[12] = counters.length > 20 ? counters[20] : -1;
        if (!Arrays.equals(previous, stable)) {
            changed = now;
            previous = stable;
        }
        return counters[11] == 0 && now - changed >= 10_000_000_000L;
    }

    private static long[] counters() throws Exception {
        Class<?> bridge = Class.forName("com.radiance.extra.compatibility.voxy.VoxyPtBridge");
        return (long[]) bridge.getMethod("counters").invoke(null);
    }

    static void checkpoint(String label) throws Exception {
        if (!Boolean.getBoolean("radiance.audit.sourceEfficiency")) return;
        long[] counters =
                Boolean.getBoolean("radiance.audit.sourceEfficiencyVoxy")
                        ? counters()
                        : new long[0];
        String line = label + " " + Arrays.toString(counters) + System.lineSeparator();
        Files.writeString(
                Path.of(System.getProperty("validation.result"), "source-efficiency.txt"),
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        var mc = Minecraft.getInstance();
        var witness = new java.util.TreeMap<String, Object>();
        var eye = mc.gameRenderer.getMainCamera().getPosition();
        var blocks = new java.util.TreeMap<String, Object>();
        int cx = net.minecraft.core.SectionPos.blockToSectionCoord((int) Math.floor(eye.x));
        int cz = net.minecraft.core.SectionPos.blockToSectionCoord((int) Math.floor(eye.z));
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                var chunk =
                        mc.level
                                .getChunkSource()
                                .getChunk(
                                        cx + dx,
                                        cz + dz,
                                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                        false);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    var be = entry.getValue();
                    var state = new java.util.TreeMap<String, Object>();
                    state.put("state", be.getBlockState().toString());
                    state.put("class", be.getClass().getName());
                    try {
                        Object speed = be.getClass().getMethod("getSpeed").invoke(be);
                        state.put("kineticSpeed", speed);
                    } catch (NoSuchMethodException ordinaryBlockEntity) {
                        // Non-kinetic block entities have no Flywheel speed input.
                    }
                    blocks.put(entry.getKey().toShortString(), state);
                }
            }
        var entities = new java.util.TreeMap<String, Object>();
        for (var entity : mc.level.entitiesForRendering())
            if (entity.position().distanceToSqr(eye) < 4096)
                entities.put(
                        entity.getUUID().toString(),
                        java.util.Map.of(
                                "type",
                                entity.getType().toString(),
                                "position",
                                java.util.List.of(entity.getX(), entity.getY(), entity.getZ()),
                                "yaw",
                                entity.getYRot(),
                                "pitch",
                                entity.getXRot(),
                                "velocity",
                                entity.getDeltaMovement().toString()));
        witness.put("blockEntities", blocks);
        witness.put("entities", entities);
        witness.put("eye", java.util.List.of(eye.x, eye.y, eye.z));
        witness.put(
                "scope",
                "Nearby block states, kinetic speed and entity poses; animation phase is not frozen");
        Files.writeString(
                Path.of(System.getProperty("validation.result"), "motion-" + label + ".json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(witness)
                        + "\n");
    }

    static void start(Minecraft mc, String label) throws Exception {
        EffectiveRenderSettings.checkpoint(mc, label);
        checkpoint("begin-" + label);
        started = System.nanoTime();
        move = -1;
        x = mc.player.getX();
        y = mc.player.getY();
        z = mc.player.getZ();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
    }

    static void moving(Minecraft mc) {
        if (!Boolean.getBoolean("radiance.audit.sourceEfficiencyMove")) return;
        if (pending != null) {
            if (!pending.isDone()) return;
            pending.join();
        }
        int tick = (int) ((System.nanoTime() - started) / 200_000_000L);
        if (tick == move) return;
        move = tick;
        double targetX = x + 4 * Math.sin(tick * .2);
        float targetYaw =
                Boolean.getBoolean("radiance.audit.sourceEfficiencyTurn")
                        ? yaw + (float) (12 * Math.sin(tick * .2))
                        : yaw;
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        pending =
                CompletableFuture.runAsync(
                        () -> {
                            var player = server.getPlayerList().getPlayer(id);
                            if (player == null)
                                throw new IllegalStateException("Moving fixture disconnected");
                            player.connection.teleport(targetX, y, z, targetYaw, pitch);
                        },
                        server);
    }

    static void finish(Minecraft mc, String label) throws Exception {
        checkpoint("end-" + label);
        if (!Boolean.getBoolean("radiance.audit.sourceEfficiencyMove")) return;
        if (pending != null) pending.join();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.submit(
                        () ->
                                server.getPlayerList()
                                        .getPlayer(id)
                                        .connection
                                        .teleport(x, y, z, yaw, pitch))
                .join();
    }
}
