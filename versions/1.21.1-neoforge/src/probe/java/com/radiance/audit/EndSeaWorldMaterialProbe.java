package com.radiance.audit;

import com.radiance.client.proxy.vulkan.FramebufferProxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.system.MemoryUtil;

/** Own fixture only: actual source mask, section tint and source-driven spatial changes. */
final class EndSeaWorldMaterialProbe {
    private static final boolean FAR =
            "endsea-far".equals(System.getProperty("radiance.audit.worldMaterials134"));
    private static final net.minecraft.resources.ResourceKey<Level> DIMENSION =
            System.getProperty("radiance.audit.worldMaterials134", "").equals("endsea-end")
                    ? Level.END
                    : Level.OVERWORLD;
    private static CompletableFuture<Void> setup;
    private static UUID identity;
    private static org.joml.Vector3d platformLocal;
    private static Object fixtureBody, physicsPipeline;
    private static Class<?> pipelineApi, bodyApi;
    private static org.joml.Vector3d pinnedPosition;
    private static org.joml.Quaterniond pinnedOrientation;
    private static long since;
    private static int stage, frames;
    private static boolean enteringDimension;
    private static int dimensionFrames;

    private EndSeaWorldMaterialProbe() {}

    static boolean poll(Minecraft mc, long now, Map<String, Object> result) throws Exception {
        if (setup == null && mc.level.dimension() != DIMENSION) {
            if (!enteringDimension) {
                command(mc, "tp @a 38 77 16 facing 38 78 0");
                enteringDimension = true;
            }
            return false;
        }
        // Establish the target world's player tracking before allocating a physics body.
        // Otherwise its initial tracking pass can retire it while the client changes dimension.
        if (setup == null && ++dimensionFrames < 60) return false;
        if (setup == null) {
            var server = Objects.requireNonNull(mc.getSingleplayerServer());
            setup =
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    var level = Objects.requireNonNull(server.getLevel(DIMENSION));
                                    var dimensionPhysics =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics");
                                    Object still =
                                            dimensionPhysics
                                                    .getConstructor(
                                                            ResourceLocation.class,
                                                            int.class,
                                                            Optional.class,
                                                            Optional.class,
                                                            Optional.class,
                                                            Optional.class,
                                                            Optional.class,
                                                            boolean.class)
                                                    .newInstance(
                                                            DIMENSION.location(),
                                                            Integer.MAX_VALUE,
                                                            Optional.empty(),
                                                            Optional.of(new org.joml.Vector3f()),
                                                            Optional.empty(),
                                                            Optional.empty(),
                                                            Optional.empty(),
                                                            false);
                                    Class.forName(
                                                    "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData")
                                            .getMethod(
                                                    "addPhysicsWithPriority",
                                                    net.minecraft.resources.ResourceKey.class,
                                                    dimensionPhysics)
                                            .invoke(null, DIMENSION, still);
                                    run(mc, "gamerule doMobSpawning false");
                                    run(mc, "gamemode spectator @a");
                                    run(mc, "time set noon");
                                    run(mc, "weather clear");
                                    run(mc, "fill 20 40 -8 40 100 8 air");
                                    run(mc, "fill 34 73 6 40 80 6 glass");
                                    var positions = new ArrayList<BlockPos>();
                                    for (int y = 48; y <= 90; y++)
                                        for (int x = 29; x <= 31; x++)
                                            for (int z = -1; z <= 1; z++) {
                                                var p = new BlockPos(x, y, z);
                                                positions.add(p);
                                                level.setBlock(
                                                        p,
                                                        ((y / 4) % 2 == 0
                                                                        ? Blocks.WHITE_CONCRETE
                                                                        : Blocks.BRICKS)
                                                                .defaultBlockState(),
                                                        3);
                                            }
                                    for (int x = 32; x <= 40; x++)
                                        for (int z = -3; z <= 3; z++) {
                                            var p = new BlockPos(x, 79, z);
                                            positions.add(p);
                                            level.setBlock(
                                                    p,
                                                    Blocks.WHITE_CONCRETE.defaultBlockState(),
                                                    3);
                                        }
                                    var boundsType =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.companion.math.BoundingBox3i");
                                    Object bounds =
                                            boundsType
                                                    .getConstructor(
                                                            int.class, int.class, int.class,
                                                            int.class, int.class, int.class)
                                                    .newInstance(29, 48, -3, 40, 90, 3);
                                    Object sublevel =
                                            Class.forName(
                                                            "dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                                                    .getMethod(
                                                            "assembleBlocks",
                                                            net.minecraft.server.level.ServerLevel
                                                                    .class,
                                                            BlockPos.class,
                                                            Iterable.class,
                                                            Class.forName(
                                                                    "dev.ryanhcode.sable.companion.math.BoundingBox3ic"))
                                                    .invoke(
                                                            null,
                                                            level,
                                                            new BlockPos(30, 70, 0),
                                                            positions,
                                                            bounds);
                                    identity =
                                            (UUID)
                                                    sublevel.getClass()
                                                            .getMethod("getUniqueId")
                                                            .invoke(sublevel);
                                    var plot =
                                            sublevel.getClass()
                                                    .getMethod("getPlot")
                                                    .invoke(sublevel);
                                    var anchor =
                                            (BlockPos)
                                                    plot.getClass()
                                                            .getMethod("getCenterBlock")
                                                            .invoke(plot);
                                    platformLocal =
                                            new org.joml.Vector3d(
                                                    anchor.getX() + 8.5,
                                                    anchor.getY() + 9,
                                                    anchor.getZ() + .5);
                                    fixtureBody = sublevel;
                                    Object initialPose =
                                            sublevel.getClass()
                                                    .getMethod("logicalPose")
                                                    .invoke(sublevel);
                                    pinnedPosition =
                                            new org.joml.Vector3d(
                                                    (org.joml.Vector3dc)
                                                            initialPose
                                                                    .getClass()
                                                                    .getMethod("position")
                                                                    .invoke(initialPose));
                                    pinnedOrientation =
                                            new org.joml.Quaterniond(
                                                    (org.joml.Quaterniondc)
                                                            initialPose
                                                                    .getClass()
                                                                    .getMethod("orientation")
                                                                    .invoke(initialPose));
                                    var containerType =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                                    Object container =
                                            containerType
                                                    .getMethod("getContainer", Level.class)
                                                    .invoke(null, level);
                                    // Far teleports precede destination chunk streaming. Keep this
                                    // owned fixture alive through that gap using Sable's real
                                    // ticket.
                                    var serverContainer =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
                                    var ticketType =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
                                    serverContainer
                                            .getMethod(
                                                    "addForceLoadTicket",
                                                    Class.forName(
                                                            "dev.ryanhcode.sable.sublevel.ServerSubLevel"),
                                                    ticketType,
                                                    Object.class)
                                            .invoke(
                                                    container,
                                                    sublevel,
                                                    ticketType.getField("COMMAND_FORCED").get(null),
                                                    net.minecraft.util.Unit.INSTANCE);
                                    Object physicsSystem =
                                            container
                                                    .getClass()
                                                    .getMethod("physicsSystem")
                                                    .invoke(container);
                                    physicsPipeline =
                                            physicsSystem
                                                    .getClass()
                                                    .getMethod("getPipeline")
                                                    .invoke(physicsSystem);
                                    pipelineApi =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.api.physics.PhysicsPipeline");
                                    bodyApi =
                                            Class.forName(
                                                    "dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
                                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                                            EndSeaWorldMaterialProbe::holdPose);
                                    var physics =
                                            Class.forName(
                                                    "dev.simulated_team.simulated.content.end_sea.EndSeaPhysics");
                                    Object sea =
                                            physics.getConstructor(
                                                            ResourceLocation.class,
                                                            Optional.class,
                                                            double.class,
                                                            double.class,
                                                            double.class)
                                                    .newInstance(
                                                            DIMENSION.location(),
                                                            Optional.of(Integer.MAX_VALUE),
                                                            80d,
                                                            0d,
                                                            0d);
                                    Class.forName(
                                                    "dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData")
                                            .getMethod(
                                                    "addKeyWithPriority",
                                                    net.minecraft.resources.ResourceKey.class,
                                                    physics)
                                            .invoke(null, DIMENSION, sea);
                                    run(mc, "tp @a 38 77 16 facing 38 78 0");
                                } catch (ReflectiveOperationException e) {
                                    throw new IllegalStateException(e);
                                }
                            },
                            server);
            since = now;
            return false;
        }
        if (!setup.isDone()) return false;
        setup.join();
        if (now - since < 8_000_000_000L || ++frames < 90) return false;
        Map<String, Object> mask = readMask();
        result.put("endSea-stage-" + stage, mask);
        EntityTakeoverProbe.screenshot(mc, "endsea-" + stage + ".png");
        if (((Number) mask.get("validDepthPixels")).longValue() == 0
                || ((Number) mask.get("softStrengthPixels")).longValue() == 0
                || ((Number) mask.get("pixelsTouchingSeaLayers")).longValue() == 0)
            throw new AssertionError(
                    "Actual source mask missing geometry or five-pass soft spread: " + mask);
        if (stage == 0
                && Math.abs(
                                ((Number) mask.get("platformBlockerY")).doubleValue()
                                        - ((Number) mask.get("platformExpectedY")).doubleValue())
                        > 1)
            throw new AssertionError("Source mask is not aligned with the known platform: " + mask);
        if (stage == 0) {
            // Force the same real cache-retirement boundary that failed in Prism. The next
            // actual source draw must rebuild its raster resources without stale map entries.
            Class.forName(
                            "com.radiance.extra.compatibility.simulated.SimulatedDiagramCompatibility")
                    .getMethod("worldMask", Runnable.class)
                    .invoke(null, (Runnable) () -> {});
            result.put("forcedMaskRetirement", true);
        }
        if (stage == 1) {
            var before = (Map<?, ?>) result.get("endSea-stage-0");
            double moved =
                    Math.abs(
                                    ((Number) before.get("centroidX")).doubleValue()
                                            - ((Number) mask.get("centroidX")).doubleValue())
                            + Math.abs(
                                    ((Number) before.get("centroidY")).doubleValue()
                                            - ((Number) mask.get("centroidY")).doubleValue());
            if (moved < .5)
                throw new AssertionError(
                        "Source mask did not respond to translated/tilted structure");
        }
        if (stage == 0) {
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                pinnedPosition = new org.joml.Vector3d(34, 65, 0);
                                pinnedOrientation =
                                        new org.joml.Quaterniond()
                                                .rotateY(-Math.toRadians(30))
                                                .rotateX(Math.toRadians(25));
                            });
        } else if (stage == 1) {
            double x = ((Number) mask.get("platformX")).doubleValue();
            double y = ((Number) mask.get("platformExpectedY")).doubleValue();
            double z = ((Number) mask.get("platformZ")).doubleValue();
            command(mc, "time set midnight");
            command(
                    mc,
                    String.format(
                            java.util.Locale.ROOT,
                            "tp @a %.5f %.5f %.5f facing %.5f %.5f %.5f",
                            x,
                            y - 2,
                            z,
                            x + .2,
                            y - 8,
                            z + .2));
        } else if (FAR && (stage == 2 || stage == 3)) {
            double x = stage == 2 ? 20000001.25 : -20000001.25;
            double z = stage == 2 ? -20000003.75 : 20000003.75;
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                pinnedPosition = new org.joml.Vector3d(x, 65, z);
                                run(
                                        mc,
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "tp @a %.5f 77 %.5f facing %.5f 78 %.5f",
                                                x + 4.125,
                                                z + 16.25,
                                                x,
                                                z));
                            });
        } else {
            mc.getSingleplayerServer().execute(() -> physicsPipeline = null);
            result.put(
                    "endSea",
                    Map.of(
                            "sourcePlanes",
                            48,
                            "spacing",
                            .5,
                            "startY",
                            80,
                            "sublevel",
                            identity.toString(),
                            "actualMaskReadback",
                            true,
                            "visualAcceptance",
                            "pending"));
            return true;
        }
        stage++;
        since = now;
        frames = 0;
        return false;
    }

    private static void holdPose(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        if (physicsPipeline == null) return;
        try {
            pipelineApi.getMethod("resetVelocity", bodyApi).invoke(physicsPipeline, fixtureBody);
            pipelineApi
                    .getMethod(
                            "teleport",
                            bodyApi,
                            org.joml.Vector3dc.class,
                            org.joml.Quaterniondc.class)
                    .invoke(physicsPipeline, fixtureBody, pinnedPosition, pinnedOrientation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot hold owned End Sea fixture pose", e);
        }
    }

    private static void run(Minecraft mc, String command) {
        var server = Objects.requireNonNull(mc.getSingleplayerServer());
        server.getCommands()
                .performPrefixedCommand(
                        server.createCommandSourceStack()
                                .withLevel(Objects.requireNonNull(server.getLevel(DIMENSION)))
                                .withSuppressedOutput()
                                .withPermission(4),
                        command);
    }

    private static void command(Minecraft mc, String command) {
        mc.getSingleplayerServer().execute(() -> run(mc, command));
    }

    private static Map<String, Object> readMask() throws Exception {
        var mc = Minecraft.getInstance();
        var containerType = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container =
                containerType.getMethod("getContainer", Level.class).invoke(null, mc.level);
        Object selected = null;
        for (Object member :
                (Iterable<?>) container.getClass().getMethod("getAllSubLevels").invoke(container))
            if (identity.equals(member.getClass().getMethod("getUniqueId").invoke(member)))
                selected = member;
        if (selected == null) throw new AssertionError("Fixture sublevel is not present on client");
        Object pose = selected.getClass().getMethod("renderPose").invoke(selected);
        var platform = new org.joml.Vector3d(platformLocal);
        pose.getClass()
                .getMethod("transformPosition", org.joml.Vector3d.class)
                .invoke(pose, platform);
        var effects =
                Class.forName("com.radiance.extra.compatibility.simulated.SimulatedWorldEffects");
        var surfaceField = effects.getDeclaredField("surface");
        surfaceField.setAccessible(true);
        var surface = surfaceField.get(null);
        if (surface == null) throw new AssertionError("No End Sea world surface producer");
        var materialField = surface.getClass().getDeclaredField("material");
        materialField.setAccessible(true);
        var material = materialField.get(surface);
        var acceptedField = material.getClass().getDeclaredField("accepted");
        acceptedField.setAccessible(true);
        if (acceptedField.get(material) == null)
            throw new AssertionError("End Sea world material was never published to Native");
        Object fbo =
                Class.forName("dev.simulated_team.simulated.content.end_sea.EndSeaShadowRenderer")
                        .getMethod("getShadowsFramebuffer")
                        .invoke(null);
        if (fbo == null) throw new AssertionError("No End Sea framebuffer");
        int id = (Integer) fbo.getClass().getMethod("getId").invoke(fbo);
        int previous = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, id);
        try {
            int[] size = FramebufferProxy.dimensions(FramebufferProxy.READ_FRAMEBUFFER);
            int count = Math.multiplyExact(size[0], size[1]);
            var pixels =
                    MemoryUtil.memAlloc(Math.multiplyExact(count, 4))
                            .order(java.nio.ByteOrder.nativeOrder());
            try {
                FramebufferProxy.readPixels(
                        0, 0, size[0], size[1], 0x1902, 0x1406, MemoryUtil.memAddress(pixels));
                long valid = 0, touchesSea = 0;
                double centroidX = 0, centroidY = 0;
                for (int i = 0; i < count; i++)
                    if (pixels.getFloat(i * 4) < 1) {
                        valid++;
                        if (80 - 128 + .5 + pixels.getFloat(i * 4) * (128 - .5) >= 80 - 47 * .5)
                            touchesSea++;
                        centroidX += i % size[0];
                        centroidY += i / size[0];
                    }
                var origin =
                        (org.joml.Vector3dc)
                                Class.forName(
                                                "dev.simulated_team.simulated.content.end_sea.EndSeaShadowRenderer")
                                        .getMethod("getLastRenderOrigin")
                                        .invoke(null);
                if (FAR && stage >= 3) {
                    var encoded =
                            java.nio.ByteBuffer.wrap((byte[]) acceptedField.get(material))
                                    .order(java.nio.ByteOrder.nativeOrder());
                    double x = (double) encoded.getFloat(80) + encoded.getFloat(56);
                    double z = (double) encoded.getFloat(88) + encoded.getFloat(60);
                    if (x != origin.x() || z != origin.z())
                        throw new AssertionError(
                                "Published far plane origin lost source precision");
                }
                int platformX = (int) ((.5 + (platform.x - origin.x()) / 256) * size[0]);
                int platformZ = (int) ((.5 + (platform.z - origin.z()) / 256) * size[1]);
                float platformDepth = pixels.getFloat((platformZ * size[0] + platformX) * 4);
                double platformY = 80 - 128 + .5 + platformDepth * (128 - .5);
                FramebufferProxy.readPixels(
                        0, 0, size[0], size[1], 0x1908, 0x1401, MemoryUtil.memAddress(pixels));
                long soft = 0, positive = 0;
                for (int i = 0; i < count; i++) {
                    int alpha = pixels.get(i * 4 + 3) & 255;
                    if (alpha > 0) positive++;
                    if (alpha > 0 && alpha < 255) soft++;
                }
                return Map.ofEntries(
                        Map.entry("pixelsTouchingSeaLayers", touchesSea),
                        Map.entry("platformExpectedY", platform.y),
                        Map.entry("platformX", platform.x),
                        Map.entry("platformZ", platform.z),
                        Map.entry("width", size[0]),
                        Map.entry("height", size[1]),
                        Map.entry("validDepthPixels", valid),
                        Map.entry("softStrengthPixels", soft),
                        Map.entry("positiveStrengthPixels", positive),
                        Map.entry("centroidX", valid == 0 ? 0 : centroidX / valid),
                        Map.entry("centroidY", valid == 0 ? 0 : centroidY / valid),
                        Map.entry("platformBlockerY", platformY));
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } finally {
            FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, previous);
        }
    }
}
