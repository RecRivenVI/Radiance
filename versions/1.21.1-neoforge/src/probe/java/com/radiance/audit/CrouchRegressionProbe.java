package com.radiance.audit;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.Input;

/** Bounded indoor pose experiment; controls input through the normal player tick. */
public final class CrouchRegressionProbe {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.crouchRegression");
    private static final int[] DURATIONS =
            "TIMED".equals(System.getProperty("radiance.audit.crouchMode"))
                    ? new int[] {8, 2, 8, 2, 8}
                    : new int[] {20, 2, 20, 2, 20};
    private static int step, phase;
    private static long deadline, entered, quietSince, nextPoll, started, phaseStart;
    private static boolean captured;
    private static BufferedWriter frames;
    private static Input original;
    private static boolean indoorSelected;
    private static java.util.concurrent.CompletableFuture<Void> teleport;
    private static final Input controlled =
            new Input() {
                @Override
                public void tick(boolean slow, float factor) {
                    shiftKeyDown = phase == 1 || phase == 3;
                }
            };

    private CrouchRegressionProbe() {}

    static boolean enabled() {
        return ENABLED;
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || !ExperimentAccess.permitted() || step == 4) return;
        long now = System.nanoTime();
        try {
            if (deadline == 0) deadline = now + 240_000_000_000L;
            if (now > deadline) throw new IllegalStateException("Crouch case exceeded 240 seconds");
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld("Newport City", () -> mc.setScreen(new TitleScreen()));
            }
            if (mc.level == null || mc.player == null) return;
            if (step == 1) {
                entered = now;
                mc.setScreen(new ChatScreen(""));
                original = mc.player.input;
                mc.player.input = controlled;
                step = 2;
                LOG.info(
                        "CROUCH world-ready position={} rotation={}/{} skyVisible={} flying={}",
                        mc.player.position(),
                        mc.player.getYRot(),
                        mc.player.getXRot(),
                        mc.level.canSeeSky(mc.player.blockPosition()),
                        mc.player.getAbilities().flying);
                if (Boolean.getBoolean("radiance.audit.crouchVerifySources")) {
                    var comparisons = new java.util.LinkedHashMap<String, Object>();
                    for (String source :
                            new String[] {
                                "ber/minecraft:sign",
                                "ber/minecraft:hanging_sign",
                                "entity/minecraft:spider",
                                "entity/minecraft:enderman",
                                "entity/minecraft:creeper",
                                "player/WIDE",
                                "player/SLIM"
                            })
                        comparisons.put(source, RendererReplacementProbe.compare(mc, source));
                    Files.writeString(
                            Path.of(
                                    System.getProperty("validation.result"),
                                    "source-comparisons.json"),
                            new com.google.gson.GsonBuilder()
                                            .setPrettyPrinting()
                                            .create()
                                            .toJson(comparisons)
                                    + "\n");
                }
            }
            if (step == 2 && now >= nextPoll) {
                nextPoll = now + 1_000_000_000L;
                var state = new Properties();
                BenchmarkChunkState.capture(state);
                Files.writeString(
                        Path.of(System.getProperty("validation.result"), "chunk-state.jsonl"),
                        new com.google.gson.Gson()
                                        .toJson(java.util.Map.of("java_ns", now, "state", state))
                                + "\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                if (!CityFrameComparisonProbe.quiet(state)) quietSince = 0;
                else if (quietSince == 0) quietSince = now;
                boolean quiet = quietSince != 0 && now - quietSince >= 10_000_000_000L;
                if (now - entered < 30_000_000_000L || (!quiet && now - entered < 60_000_000_000L))
                    return;
                if (Boolean.getBoolean("radiance.audit.crouchIndoor") && !indoorSelected) {
                    var interior = findInterior(mc);
                    indoorSelected = true;
                    var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
                    float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
                    var id = mc.player.getUUID();
                    teleport =
                            java.util.concurrent.CompletableFuture.runAsync(
                                    () -> {
                                        var player =
                                                java.util.Objects.requireNonNull(
                                                        server.getPlayerList().getPlayer(id));
                                        player.teleportTo(
                                                player.serverLevel(),
                                                interior.getX() + .5,
                                                interior.getY(),
                                                interior.getZ() + .5,
                                                yaw,
                                                pitch);
                                    },
                                    server);
                    LOG.info(
                            "CROUCH selected-indoor feet={} roof={}",
                            interior,
                            mc.level.getBlockState(interior.above(3)));
                    step = 6;
                    return;
                }
                Path output = Path.of(System.getProperty("validation.result"));
                frames = Files.newBufferedWriter(output.resolve("pose-frames.csv"));
                frames.write(
                        "frame,java_ns,phase,elapsed_s,player_x,player_y,player_z,camera_x,camera_y,camera_z,pose,crouching,input_shift,sky_visible\n");
                String mode = System.getProperty("radiance.audit.crouchMode", "FRAME_ONLY");
                if (FrameProfiler.request(
                                java.util.Arrays.stream(DURATIONS).sum() + 2,
                                "indoor-crouch",
                                ProfileMode.valueOf(mode))
                        != 1) throw new IllegalStateException("Profiler already active");
                started = phaseStart = now;
                step = 3;
                LOG.info("CROUCH sequence-start mode={} position={}", mode, mc.player.position());
            }
            if (step == 6 && teleport.isDone()) {
                teleport.join();
                entered = now;
                quietSince = 0;
                step = 2;
                return;
            }
            if (step == 5) {
                if (!FrameProfiler.requested()) {
                    step = 4;
                    LOG.info(
                            "CROUCH PASS normal-stop; observed rendering is not visual acceptance");
                    mc.stop();
                }
                return;
            }
            if (step != 3) return;
            var camera = mc.gameRenderer.getMainCamera().getPosition();
            frames.write(
                    String.format(
                            Locale.ROOT,
                            "%d,%d,%d,%.6f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%s,%s,%s,%s%n",
                            FrameProfiler.frameToken(),
                            now,
                            phase,
                            (now - started) / 1e9,
                            mc.player.getX(),
                            mc.player.getY(),
                            mc.player.getZ(),
                            camera.x,
                            camera.y,
                            camera.z,
                            mc.player.getPose(),
                            mc.player.isCrouching(),
                            mc.player.input.shiftKeyDown,
                            mc.level.canSeeSky(mc.player.blockPosition())));
            if (!captured
                    && now - phaseStart
                            > (phase == 1 || phase == 3 ? 800_000_000L : 5_000_000_000L)) {
                net.minecraft.client.Screenshot.grab(
                        mc.gameDirectory,
                        "crouch-phase-" + phase + ".png",
                        mc.getMainRenderTarget(),
                        message -> {});
                if (Boolean.getBoolean("radiance.audit.crouchCapture") && phase < 3) {
                    Files.writeString(
                            mc.gameDirectory.toPath().resolve("radiance-world-capture.request"),
                            "phase=" + phase + "\n");
                    Files.writeString(
                            Path.of(
                                    System.getProperty("validation.result"),
                                    "capture-requests.jsonl"),
                            new com.google.gson.Gson()
                                            .toJson(
                                                    java.util.Map.of(
                                                            "phase",
                                                            phase,
                                                            "java_ns",
                                                            now,
                                                            "profile_frame",
                                                            FrameProfiler.frameToken()))
                                    + "\n",
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                }
                captured = true;
            }
            if (now - phaseStart >= DURATIONS[phase] * 1_000_000_000L) {
                LOG.info(
                        "CROUCH phase-end phase={} crouching={} camera={} player={}",
                        phase,
                        mc.player.isCrouching(),
                        camera,
                        mc.player.position());
                phaseStart = now;
                captured = false;
                if (++phase == DURATIONS.length) {
                    frames.close();
                    mc.player.input = original;
                    step = 5;
                }
            }
        } catch (Exception | LinkageError error) {
            step = 4;
            LOG.error("CROUCH FAIL; no automatic retry", error);
            if (mc.player != null && original != null) mc.player.input = original;
            try {
                if (frames != null) frames.close();
            } catch (java.io.IOException suppressed) {
                error.addSuppressed(suppressed);
            }
            mc.stop();
        }
    }

    private static net.minecraft.core.BlockPos findInterior(Minecraft mc) {
        var origin = mc.player.blockPosition();
        net.minecraft.core.BlockPos best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (int y = origin.getY() - 20; y <= origin.getY() + 4; y++)
            for (int x = origin.getX() - 20; x <= origin.getX() + 20; x++)
                for (int z = origin.getZ() - 20; z <= origin.getZ() + 20; z++) {
                    var p = new net.minecraft.core.BlockPos(x, y, z);
                    if (!mc.level.hasChunkAt(p)
                            || !mc.level.getBlockState(p).isAir()
                            || !mc.level.getBlockState(p.above()).isAir()
                            || mc.level
                                    .getBlockState(p.below())
                                    .getCollisionShape(mc.level, p.below())
                                    .isEmpty()) continue;
                    boolean roof = false;
                    for (int h = 2; h <= 6; h++)
                        if (!mc.level.getBlockState(p.above(h)).isAir()) {
                            roof = true;
                            break;
                        }
                    if (!roof) continue;
                    int walls = 0;
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                        if (!mc.level.getBlockState(p.relative(direction, 3).above()).isAir())
                            walls++;
                    double distance = p.distSqr(origin);
                    if (walls >= 2 && distance < nearest) {
                        nearest = distance;
                        best = p;
                    }
                }
        if (best == null)
            throw new IllegalStateException(
                    "No loaded building interior found near saved position");
        return best;
    }
}
