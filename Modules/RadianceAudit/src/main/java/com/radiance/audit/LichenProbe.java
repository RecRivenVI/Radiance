package com.radiance.audit;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Bounded isolated model/first-hit comparison. Never enabled by ordinary Audit installation. */
public final class LichenProbe {
    private static final boolean ENABLED = "1".equals(ExperimentAccess.getenv("RADIANCE_LICHEN_PROBE"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Direction[] SUPPORT = {Direction.DOWN, Direction.NORTH, Direction.UP};
    private static final String[] NAMES = {"floor", "wall", "ceiling"};
    private static long entered, next;
    private static int step;
    private static BlockPos origin;
    private static CompletableFuture<?> operation;
    private static boolean finished;

    public static boolean poll(Minecraft mc) {
        if (!ENABLED) return false;
        if (finished || mc.level == null || mc.player == null) return true;
        long now = System.nanoTime();
        try {
            if (entered == 0) {
                entered = now; next = now + 20_000_000_000L;
                origin = new BlockPos(mc.player.getBlockX(), 192, mc.player.getBlockZ());
                LOG.info("LICHEN_PROBE start origin={} isolated fixture; no visual approval", origin);
            }
            if (now - entered > 150_000_000_000L) throw new IllegalStateException("Lichen probe timeout");
            if (now < next || operation != null && !operation.isDone()) return true;
            if (operation != null) { operation.join(); operation = null; }
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("Requires isolated integrated server");
            if (step == 0) {
                var dimension = mc.level.dimension(); var playerId = mc.player.getUUID();
                operation = CompletableFuture.runAsync(() -> {
                    var level = server.getLevel(dimension);
                    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(14, 5, 5)))
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    for (int i = 0; i < SUPPORT.length; i++) {
                        BlockPos pos = origin.offset(i * 4, 0, 0);
                        level.setBlock(pos.relative(SUPPORT[i]), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
                        level.setBlock(pos, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(MultifaceBlock.getFaceProperty(SUPPORT[i]), true), 3);
                    }
                    server.getPlayerList().getPlayer(playerId).setGameMode(GameType.SPECTATOR);
                }, server);
                mc.options.hideGui = true;
                step++; next = now + 10_000_000_000L;
            } else if (step <= 9) {
                int index = (step - 1) / 3, phase = (step - 1) % 3;
                if (phase == 0) {
                    dumpModel(mc, index);
                    double x = origin.getX() + index * 4 + 0.5, y = origin.getY(), z = origin.getZ() + 0.5;
                    float pitch;
                    if (index == 0) { y += 3; pitch = 90; }
                    else if (index == 1) { y += 0.5; z += 3; pitch = 0; }
                    else { y -= 2; pitch = -90; }
                    final double px = x, py = y - mc.player.getEyeHeight(), pz = z;
                    var playerId = mc.player.getUUID();
                    operation = CompletableFuture.runAsync(() -> server.getPlayerList().getPlayer(playerId)
                        .connection.teleport(px, py, pz, 180, pitch), server);
                    next = now + 8_000_000_000L;
                    LOG.info("LICHEN_PROBE view={} eye={}, {}, {} pitch={}", NAMES[index], x, y, z, pitch);
                } else if (phase == 1) {
                    Files.writeString(mc.gameDirectory.toPath().resolve("radiance-world-capture.request"), NAMES[index]);
                    Screenshot.grab(mc.gameDirectory, "lichen-" + NAMES[index] + ".png", mc.getMainRenderTarget(),
                        message -> LOG.info("LICHEN_PROBE screenshot {}", message.getString()));
                    next = now + 5_000_000_000L;
                }
                step++;
            } else {
                finished = true;
                LOG.info("LICHEN_PROBE completed three views; normal close requested; appearance pending analysis");
                mc.stop();
            }
        } catch (Exception | LinkageError error) {
            finished = true;
            LOG.error("LICHEN_PROBE failed step={}; stopping without retry", step, error);
            mc.stop();
        }
        return true;
    }

    private static void dumpModel(Minecraft mc, int index) throws Exception {
        var state = mc.level.getBlockState(origin.offset(index * 4, 0, 0));
        if (!state.is(Blocks.GLOW_LICHEN)) throw new IllegalStateException("Fixture not synchronized " + state);
        var model = mc.getBlockRenderer().getBlockModel(state);
        var output = mc.gameDirectory.toPath().resolve("lichen-models"); Files.createDirectories(output);
        StringBuilder text = new StringBuilder(state.toString()).append('\n');
        int layerIndex = 0;
        for (var layer : model.getRenderTypes(state, RandomSource.create(0), ModelData.EMPTY)) {
            text.append("layer=").append(layer).append(" faceFlags=").append(MaterialFaces.capture(layer)).append('\n');
            var sides = new ArrayList<Direction>(); sides.add(null); sides.addAll(java.util.List.of(Direction.values()));
            try (var allocator = new ByteBufferBuilder(8192)) {
                var consumer = new PBRVertexConsumer(allocator, layer);
                for (var side : sides) for (var quad : model.getQuads(state, side, RandomSource.create(0), ModelData.EMPTY, layer)) {
                    text.append("quad=").append(quad.getDirection()).append(" sprite=").append(quad.getSprite().contents().name()).append('\n');
                    int[] vertices = quad.getVertices();
                    for (int i = 0; i < 4; i++) text.append(String.format(java.util.Locale.ROOT,
                        "%.9f %.9f %.9f uv %.9f %.9f%n", Float.intBitsToFloat(vertices[i * 8]),
                        Float.intBitsToFloat(vertices[i * 8 + 1]), Float.intBitsToFloat(vertices[i * 8 + 2]),
                        Float.intBitsToFloat(vertices[i * 8 + 4]), Float.intBitsToFloat(vertices[i * 8 + 5])));
                    consumer.putBulkData(new PoseStack().last(), quad, new float[]{1,1,1,1}, 1,1,1,1,
                        new int[]{0,0,0,0}, 0, true);
                }
                try (var mesh = consumer.endNullable()) {
                    if (mesh == null) throw new IllegalStateException("Empty fixture mesh");
                    var buffer = mesh.vertexBuffer().duplicate(); byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes);
                    Files.write(output.resolve(NAMES[index] + "-layer-" + layerIndex++ + "-pbr.bin"), bytes);
                    text.append("pbrVertices=").append(mesh.drawState().vertexCount()).append('\n');
                }
            }
        }
        Files.writeString(output.resolve(NAMES[index] + ".txt"), text);
    }
}
