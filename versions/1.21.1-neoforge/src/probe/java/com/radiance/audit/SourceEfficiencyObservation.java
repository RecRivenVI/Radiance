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
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        pending =
                CompletableFuture.runAsync(
                        () -> {
                            var player = server.getPlayerList().getPlayer(id);
                            if (player == null)
                                throw new IllegalStateException("Moving fixture disconnected");
                            player.connection.teleport(targetX, y, z, yaw, pitch);
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
