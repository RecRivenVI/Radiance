package com.radiance.audit;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Stage-3 source comparisons in an owned small fixture; no performance claims. */
public final class EntityTakeoverProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.entityFixture");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static int step;
    private static long start, since;
    private static CompletableFuture<?> pending;

    private EntityTakeoverProbe() {}

    public static void poll(Minecraft mc) {
        if (!ENABLED || !ExperimentAccess.permitted() || step == 7) return;
        long now = System.nanoTime();
        try {
            if (start == 0) start = now;
            require(now - start < 360_000_000_000L, "bounded fixture duration");
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                require(
                        Files.isRegularFile(
                                mc.gameDirectory.toPath().resolve(".radiance-entity-takeover")),
                        "owned fixture");
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
            }
            if (mc.level == null || mc.player == null) return;
            if (step == 1) {
                require(
                        mc.getWindow().getWidth() == 1920 && mc.getWindow().getHeight() == 1080,
                        "viewport");
                require(
                        mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)
                                == 0,
                        "muted");
                since = now;
                step = 2;
                mc.setScreen(new ChatScreen(""));
                LOG.info(
                        "ENTITY_TAKEOVER world-ready foreign={}",
                        Boolean.getBoolean("radiance.audit.unregisteredSource"));
            } else if (step == 2 && now - since > 20_000_000_000L) {
                var server = Objects.requireNonNull(mc.getSingleplayerServer());
                pending = CompletableFuture.runAsync(() -> setup(mc, server), server);
                ModelSourceRegistryProbe.begin(mc);
                step = 3;
                since = now;
            } else if (step == 3 && pending.isDone()) {
                pending.join();
                if (!ModelSourceRegistryProbe.poll(mc) || now - since < 15_000_000_000L) return;
                verifyComparison();
                if (Boolean.getBoolean("radiance.audit.partLifecycle")) {
                    step = 7; // The existing lifecycle driver owns mutations, disconnect and close.
                    LOG.info("ENTITY_TAKEOVER lifecycle-owner-ready");
                    return;
                }
                if (!Boolean.getBoolean("radiance.audit.unregisteredSource")
                        && com.radiance.mixin_related.SourceCompatibility.compatible(
                                com.radiance.mixin_related.SourceCompatibility.Source.STATIC_STATE))
                    PartModelProbe.precise(
                            () ->
                                    result.put(
                                            "publicStateMutations",
                                            PartStateMutationProbe.verify(mc)));
                result.put("beforeReload", PartModelProbe.results());
                result.put("bakedBeforeReload", BakedTakeoverProbe.results());
                result.put("sourceRegistry", ModelSourceRegistryProbe.rows());
                if (Boolean.getBoolean("radiance.audit.rendererReplacement")) write();
                if (Boolean.getBoolean("radiance.audit.rendererReplacement")) {
                    result.put("worldItems", RendererReplacementProbe.compareItems(mc));
                    result.put(
                            "nestedParticles", RendererReplacementProbe.compareNestedParticles(mc));
                    result.put(
                            "worldBlockChanges",
                            RendererReplacementProbe.compareWorldBlockChanges(mc));
                    result.put("worldLighting", RendererReplacementProbe.compareWorldLighting(mc));
                    result.put(
                            "preEventModelMutation",
                            RendererReplacementProbe.compareEventMutation(mc));
                }
                result.put(
                        "coverageBeforeReload",
                        com.radiance.mixin_related.SourceCompatibility.coverage());
                screenshot(mc, "entity-takeover-before.png");
                if (!Boolean.getBoolean("radiance.audit.unregisteredSource"))
                    PartModelProbe.checkpoint("entity-before-reload");
                pending = mc.reloadResourcePacks();
                step = 4;
            } else if (step == 4 && pending.isDone()) {
                pending.join();
                since = now;
                step = 5;
                var server = Objects.requireNonNull(mc.getSingleplayerServer());
                pending =
                        CompletableFuture.runAsync(
                                () -> {
                                    var source =
                                            server.createCommandSourceStack()
                                                    .withSuppressedOutput()
                                                    .withPermission(4);
                                    server.getCommands()
                                            .performPrefixedCommand(
                                                    source,
                                                    "data merge entity @e[type=minecraft:armor_stand,tag=stage3_owner,limit=1] {ShowArms:1b,NoBasePlate:1b,Pose:{Head:[25f,45f,10f],Body:[0f,-25f,0f]}}");
                                    for (var command :
                                            List.of(
                                                    "data merge entity @e[type=minecraft:creeper,tag=stage3_owner,limit=1] {powered:1b}",
                                                    "item replace entity @e[type=minecraft:armor_stand,tag=stage3_owner,limit=1] armor.head with minecraft:iron_helmet",
                                                    "gamemode creative RecRivenVI",
                                                    "item replace entity RecRivenVI weapon.mainhand with minecraft:diamond_sword"))
                                        server.getCommands()
                                                .performPrefixedCommand(source, command);
                                },
                                server);
            } else if (step == 5 && pending.isDone() && now - since > 15_000_000_000L) {
                pending.join();
                if (!Boolean.getBoolean("radiance.audit.unregisteredSource")
                        && com.radiance.mixin_related.SourceCompatibility.compatible(
                                com.radiance.mixin_related.SourceCompatibility.Source.STATIC_STATE))
                    PartModelProbe.precise(
                            () ->
                                    result.put(
                                            "publicStateMutationsAfterReload",
                                            PartStateMutationProbe.verify(mc)));
                verifyComparison();
                result.put("afterReload", PartModelProbe.results());
                if (Boolean.getBoolean("radiance.audit.rendererReplacement"))
                    result.put(
                            "firstPersonActions", RendererReplacementProbe.compareFirstPerson(mc));
                result.put("bakedAfterReload", BakedTakeoverProbe.results());
                result.put("coverage", com.radiance.mixin_related.SourceCompatibility.coverage());
                result.put("resourceReload", true);
                result.put("fontViolations", FontSourceProbe.violations());
                require(FontSourceProbe.violations() == 0, "font ownership");
                OffscreenDependencyProbe.verify("entity-takeover");
                screenshot(mc, "entity-takeover-after.png");
                write();
                if (Boolean.getBoolean("radiance.audit.routeCensus"))
                    ProducerCensus.start(
                            Path.of(System.getProperty("validation.result"), "route-census"),
                            true,
                            true);
                step = 6;
                since = now;
            } else if (step == 6 && now - since > 3_000_000_000L) {
                if (Boolean.getBoolean("radiance.audit.routeCensus")) ProducerCensus.frame();
                ProducerCensus.finish();
                step = 7;
                LOG.info(
                        "ENTITY_TAKEOVER PASS normal-stop scope=source-input-and-lifecycle-not-user-visual");
                mc.stop();
            } else if (step == 6 && Boolean.getBoolean("radiance.audit.routeCensus")) {
                ProducerCensus.frame();
            }
        } catch (Exception | LinkageError | AssertionError failure) {
            step = 7;
            LOG.error("ENTITY_TAKEOVER FAIL; preserve and stop without retry", failure);
            result.put("failure", failure.toString());
            result.put("sourceRegistry", ModelSourceRegistryProbe.rows());
            try {
                write();
            } catch (java.io.IOException evidenceFailure) {
                failure.addSuppressed(evidenceFailure);
                LOG.error("Comparison evidence write failed", evidenceFailure);
            }
            mc.stop();
        }
    }

    private static void verifyComparison() {
        if (com.radiance.client.vertex.BakedModelTakeover.sourceCompatible())
            require(
                    ((Number) BakedTakeoverProbe.results().get("checks")).longValue() > 10,
                    "actual baked-source comparisons with state execution retained");
        if (Boolean.getBoolean("radiance.audit.unregisteredSource")) {
            UnregisteredSourceProbe.verify();
            require(PartModelProbe.checked() == 0, "unknown source never uses a direct part");
        } else {
            require(
                    com.radiance.client.vertex.PartModelCapture.sourceCompatible(),
                    "part source qualification");
            require(PartModelProbe.checked() > 20, "actual Cube.compile comparisons");
        }
    }

    private static void setup(Minecraft mc, net.minecraft.server.MinecraftServer server) {
        var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        var commands =
                new ArrayList<String>(
                        List.of(
                                "gamerule doMobSpawning false",
                                "gamerule doDaylightCycle false",
                                "gamerule doWeatherCycle false",
                                "gamerule randomTickSpeed 0",
                                "time set 6000",
                                "weather clear",
                                "fill -14 64 -8 14 64 12 minecraft:light_gray_concrete",
                                "kill @e[tag=stage3_owner]"));
        int i = 0;
        for (String entity :
                List.of(
                        "armor_stand",
                        "zombie",
                        "skeleton",
                        "creeper",
                        "cow",
                        "pig",
                        "sheep",
                        "spider",
                        "enderman",
                        "wolf",
                        "cat",
                        "boat",
                        "minecart")) {
            int x = -12 + (i % 7) * 4, z = -3 + (i / 7) * 4;
            commands.add(
                    "summon minecraft:"
                            + entity
                            + " "
                            + x
                            + " 65 "
                            + z
                            + " {Tags:[\"stage3_owner\"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Invulnerable:1b"
                            + (entity.equals("creeper") ? ",powered:1b" : "")
                            + "}");
            i++;
        }
        commands.addAll(
                List.of(
                        "setblock -10 65 8 minecraft:chest",
                        "setblock -6 65 8 minecraft:ender_chest",
                        "setblock -2 65 8 minecraft:red_bed[part=foot,facing=north]",
                        "setblock -2 65 7 minecraft:red_bed[part=head,facing=north]",
                        "setblock 2 65 8 minecraft:oak_sign",
                        "setblock 6 65 8 minecraft:shulker_box",
                        "setblock 10 65 8 minecraft:decorated_pot"));
        for (var command : commands) server.getCommands().performPrefixedCommand(source, command);
        LOG.info(
                "ENTITY_TAKEOVER fixture-ready source-callbacks=retained commands={}",
                commands.size());
    }

    static void screenshot(Minecraft mc, String name) {
        net.minecraft.client.Screenshot.grab(
                mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }

    private static void write() throws java.io.IOException {
        var directory = Path.of(System.getProperty("validation.result"));
        Files.createDirectories(directory);
        result.put(
                "scope",
                Boolean.getBoolean("radiance.audit.rendererReplacement")
                        ? "Actual independent renderer versus declared part/layer/item requests; route and remaining callbacks recorded per source; final visual pending"
                        : "Actual model/Cube inputs; renderer/layer callbacks retained; final dynamic visual acceptance pending");
        Files.writeString(
                directory.resolve("entity-takeover.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result)
                        + "\n");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Entity takeover: " + message);
    }
}
