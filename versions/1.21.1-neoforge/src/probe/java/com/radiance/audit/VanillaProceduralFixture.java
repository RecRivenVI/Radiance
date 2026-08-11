package com.radiance.audit;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import org.joml.Vector3f;

/** All registered vanilla particle options, including custom models and non-drawing emitters. */
final class VanillaProceduralFixture {
    private static final Map<String, Object> particles = new TreeMap<>();
    private static long next;
    private static int index;
    private static List<ParticleType<?>> types;

    private VanillaProceduralFixture() {}

    static void start(Minecraft mc) {
        types =
                BuiltInRegistries.PARTICLE_TYPE.stream()
                        .filter(
                                type ->
                                        BuiltInRegistries.PARTICLE_TYPE
                                                .getKey(type)
                                                .getNamespace()
                                                .equals("minecraft"))
                        .toList();
        index = 0;
        next = 0;
        var item =
                new net.minecraft.world.entity.item.ItemEntity(
                        mc.level,
                        mc.player.getX(),
                        mc.player.getY() + 1,
                        mc.player.getZ(),
                        new ItemStack(Items.DIAMOND));
        mc.particleEngine.add(
                new ItemPickupParticle(
                        mc.getEntityRenderDispatcher(),
                        mc.renderBuffers(),
                        mc.level,
                        item,
                        mc.player));
        mc.particleEngine.createTrackingEmitter(mc.player, ParticleTypes.HEART, 3);
        mc.particleEngine.add(
                new FireworkParticles.Starter(
                        mc.level,
                        mc.player.getX(),
                        mc.player.getY() + 3,
                        mc.player.getZ(),
                        0,
                        0,
                        0,
                        mc.particleEngine,
                        List.of(
                                new net.minecraft.world.item.component.FireworkExplosion(
                                        net.minecraft.world.item.component.FireworkExplosion.Shape
                                                .SMALL_BALL,
                                        it.unimi.dsi.fastutil.ints.IntList.of(0xff2244),
                                        it.unimi.dsi.fastutil.ints.IntList.of(0x44ff88),
                                        true,
                                        true))));
        particles.put(
                "non_registry:item_pickup",
                Map.of("class", ItemPickupParticle.class.getName(), "draws", true));
        particles.put(
                "non_registry:tracking_emitter",
                Map.of("class", TrackingEmitter.class.getName(), "draws", false));
        particles.put(
                "non_registry:firework_starter",
                Map.of("class", FireworkParticles.Starter.class.getName(), "draws", false));
        var server = Objects.requireNonNull(mc.getSingleplayerServer());
        server.execute(
                () -> {
                    var source =
                            server.createCommandSourceStack()
                                    .withSuppressedOutput()
                                    .withPermission(4);
                    for (String command :
                            List.of(
                                    "weather rain",
                                    "time set 6000",
                                    "summon minecraft:lightning_bolt 2 66 3",
                                    "fill 15 66 1 16 66 2 minecraft:water",
                                    "setblock 15 65 1 minecraft:stone",
                                    "summon minecraft:armor_stand 1 66 3 {CustomName:'{\"text\":\"Stage 6 world text\"}',CustomNameVisible:1b,NoGravity:1b}"))
                        server.getCommands().performPrefixedCommand(source, command);
                });
    }

    static boolean poll(Minecraft mc, long now) {
        if (index == types.size()) return true;
        if (now < next) return false;
        next = now + 150_000_000L;
        var type = types.get(index++);
        var id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        var p =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition()
                        .add(mc.player.getViewVector(1).scale(4));
        Particle particle =
                mc.particleEngine.createParticle(options(type, mc), p.x, p.y, p.z, .03, .02, .01);
        if (particle == null)
            throw new IllegalStateException("Vanilla provider produced no particle: " + id);
        particles.put(
                id.toString(),
                Map.of(
                        "class",
                        particle.getClass().getName(),
                        "sheet",
                        particle.getRenderType().toString(),
                        "draws",
                        particle.getRenderType() != ParticleRenderType.NO_RENDER));
        if (particle instanceof SingleQuadParticle) verifyQuad(mc, particle, id.toString());
        return false;
    }

    private static void verifyQuad(Minecraft mc, Particle particle, String id) {
        try {
            var factory =
                    com.radiance.client.proxy.world.EntityProxy.class.getDeclaredMethod(
                            "radiance$particleRenderType", String.class, ParticleRenderType.class);
            factory.setAccessible(true);
            var type = particle.getRenderType();
            var layer = (net.minecraft.client.renderer.RenderType) factory.invoke(null, id, type);
            int alpha = com.radiance.adapter.vanilla.VanillaParticleMaterials.alphaMode(type);
            var storage =
                    new com.radiance.client.vertex.StorageVertexConsumerProvider(512) {
                        protected com.mojang.blaze3d.vertex.VertexConsumer createQuadConsumer(
                                com.mojang.blaze3d.vertex.ByteBufferBuilder allocation,
                                net.minecraft.client.renderer.RenderType ignored) {
                            return com.radiance.client.vertex.PBRVertexConsumer.dynamicMaterial(
                                    allocation,
                                    layer,
                                    false,
                                    com.radiance.render.contract.SurfaceMaterial.ordinaryPolicy(
                                            alpha));
                        }
                    }.particleModels(mc.gameRenderer.getMainCamera().getPosition());
            try {
                var out = storage.getBuffer(layer);
                for (float partial : new float[] {0, .25F, .75F}) {
                    if (com.radiance.client.vertex.ParticleQuadTakeover.replacementEligible(
                            particle, out))
                        com.radiance.client.vertex.ParticleQuadTakeover.request(
                                particle, out, mc.gameRenderer.getMainCamera(), partial);
                    else particle.render(out, mc.gameRenderer.getMainCamera(), partial);
                }
                // Input comparison only: do not submit this second execution to the world.
            } finally {
                storage.close();
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions options(ParticleType<?> type, Minecraft mc) {
        if (type instanceof SimpleParticleType simple) return simple;
        String key = BuiltInRegistries.PARTICLE_TYPE.getKey(type).getPath();
        return switch (key) {
            case "block", "block_marker", "falling_dust", "dust_pillar" ->
                    new BlockParticleOption(
                            (ParticleType<BlockParticleOption>) type,
                            Blocks.STONE.defaultBlockState());
            case "dust" -> new DustParticleOptions(new Vector3f(.8f, .2f, .1f), 1);
            case "dust_color_transition" ->
                    new DustColorTransitionOptions(
                            new Vector3f(.8f, .2f, .1f), new Vector3f(.1f, .4f, .9f), 1);
            case "entity_effect" ->
                    ColorParticleOption.create(
                            (ParticleType<ColorParticleOption>) type, 0x9944cc88);
            case "item" ->
                    new ItemParticleOption(
                            (ParticleType<ItemParticleOption>) type, new ItemStack(Items.DIAMOND));
            case "vibration" ->
                    new VibrationParticleOption(
                            new BlockPositionSource(
                                    BlockPos.containing(mc.player.position()).offset(2, 0, 2)),
                            30);
            case "sculk_charge" -> new SculkChargeParticleOptions(.6f);
            case "shriek" -> new ShriekParticleOption(0);
            default ->
                    throw new IllegalStateException(
                            "Vanilla particle option lacks fixture: " + key);
        };
    }

    static Map<String, Object> results() {
        return Map.of(
                "registered",
                types == null ? 0 : types.size(),
                "visited",
                index,
                "particles",
                Map.copyOf(particles),
                "quadComparison",
                ParticleTakeoverProbe.results(),
                "sharedQuadCounters",
                com.radiance.client.vertex.ParticleQuadTakeover.counters());
    }
}
