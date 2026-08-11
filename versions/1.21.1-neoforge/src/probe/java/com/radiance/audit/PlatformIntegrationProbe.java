package com.radiance.audit;

import com.radiance.api.render.*;
import com.radiance.client.gui.RenderStatusScreen;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.EntityType;

/** Actual packaged platform UI/effect exercise alongside the existing source/reload fixture. */
public final class PlatformIntegrationProbe {
    private static long start;
    private static int step;
    private static final Map<String, Object> result = new LinkedHashMap<>();

    private PlatformIntegrationProbe() {}

    public static void poll(Minecraft mc) {
        if (!Boolean.getBoolean("radiance.audit.platform")
                || mc.level == null
                || mc.player == null
                || step == 8) return;
        try {
            long now = System.nanoTime();
            if (start == 0) {
                start = now;
                return;
            }
            long seconds = (now - start) / 1_000_000_000L;
            if (step == 0 && seconds >= 3) {
                Set<String> expected =
                        new HashSet<>(
                                List.of(
                                        System.getProperty("radiance.audit.expectedSources", "")
                                                .split(",")));
                expected.remove("");
                if (!expected.equals(new HashSet<>(RenderPlatform.integrations())))
                    throw new AssertionError(
                            "Active platform sources differ: " + RenderPlatform.integrations());
                result.put("sources", RenderPlatform.integrations());
                result.put("availability", RenderPlatform.availability());
                if (Boolean.getBoolean("radiance.audit.persistentSources130"))
                    result.put("spaceEndpoints", checkSpaceEndpoints());
                EntityTakeoverProbe.screenshot(mc, "platform-world.png");
                mc.setScreen(new RenderStatusScreen(mc.screen));
                step++;
            } else if (step == 1 && seconds >= 6) {
                if (!(mc.screen instanceof RenderStatusScreen screen))
                    throw new AssertionError("Platform status screen missing");
                result.put("status", screen.lines());
                result.put("routes", RenderRoutes.snapshot());
                if (RenderRoutes.snapshot().stream().noneMatch(r -> r.submitted() > 0))
                    throw new AssertionError("Status has no actual consumer");
                EntityTakeoverProbe.screenshot(mc, "platform-status.png");
                mc.setScreen(new ChatScreen(""));
                camera(mc, EntityType.CREEPER);
                step++;
            } else if (step == 2 && seconds >= 8) {
                EntityTakeoverProbe.screenshot(mc, "platform-creeper.png");
                camera(mc, EntityType.SPIDER);
                step++;
            } else if (step == 3 && seconds >= 10) {
                EntityTakeoverProbe.screenshot(mc, "platform-spider.png");
                camera(mc, EntityType.ENDERMAN);
                step++;
            } else if (step == 4 && seconds >= 12) {
                EntityTakeoverProbe.screenshot(mc, "platform-invert.png");
                mc.setCameraEntity(mc.player);
                step++;
            } else if (step == 5 && seconds >= 17) {
                result.put("persistentBeforeSetup", PersistentInstances.counters());
                step++;
            } else if (step == 6 && seconds >= 38) {
                result.put("persistentAfterSetup", PersistentInstances.counters());
                step++;
            } else if (step == 7 && seconds >= 48) {
                result.put("persistentLate", PersistentInstances.counters());
                TakeoverSourceProbe.finish();
                result.put("performanceMeasured", false);
                result.put("visualAcceptance", "pending");
                var path =
                        Path.of(
                                System.getProperty("validation.result"),
                                "platform-integration.json");
                Files.writeString(
                        path,
                        new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .toJson(result)
                                + "\n");
                com.mojang.logging.LogUtils.getLogger()
                        .info(
                                "PLATFORM_INTEGRATION PASS actual status/effects; user visual pending");
                step++;
            }
        } catch (Exception | LinkageError | AssertionError failure) {
            step = 8;
            com.mojang.logging.LogUtils.getLogger().error("PLATFORM_INTEGRATION FAIL", failure);
            mc.stop();
        }
    }

    private static Map<String, Object> checkSpaceEndpoints() {
        try (var parent = new RenderSpace();
                var child = new RenderSpace(parent);
                var unrelated = new RenderSpace()) {
            var before =
                    new PoseEndpoint(
                            new org.joml.Vector3d(1, 2, 3),
                            new org.joml.Quaterniond(),
                            new org.joml.Vector3d(1));
            var after =
                    new PoseEndpoint(
                            new org.joml.Vector3d(5, 4, -3),
                            new org.joml.Quaterniond().rotationY(.7),
                            new org.joml.Vector3d(2, 1, .5));
            parent.motion(before, after, 10, 11, 10.25);
            child.handle();
            unrelated.handle();
            long unrelatedRevision = unrelated.version();
            for (double time : new double[] {9, 10.5, 11, 12}) {
                parent.sample(time);
                RenderSpace.flushUpdates();
                child.handle();
                if (!child.transform(new org.joml.Matrix4d()).isFinite())
                    throw new AssertionError("Sampled child is not finite");
            }
            if (unrelated.version() != unrelatedRevision)
                throw new AssertionError("Unrelated space invalidated");
            parent.pose(new org.joml.Matrix4d().translation(9, 8, 7), new org.joml.Matrix3f());
            RenderSpace.flushUpdates();
            if (!child.transform(new org.joml.Matrix4d())
                    .equals(new org.joml.Matrix4d().translation(9, 8, 7), 1e-9))
                throw new AssertionError("Final pose retained source motion");
        }
        return Map.of(
                "nativeMotionAndSampleCalled",
                true,
                "finalPoseRestored",
                true,
                "unrelatedVersionStable",
                true,
                "retirement",
                "real JNI removal; selected-current-frame lifetime covered separately");
    }

    private static void camera(Minecraft mc, EntityType<?> type) {
        var e = Objects.requireNonNull(type.create(mc.level));
        e.moveTo(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot());
        mc.setCameraEntity(e);
    }
}
