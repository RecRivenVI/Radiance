package com.radiance.audit;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.client.vertex.PBRVertexFormats;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** One isolated main-world source→PBR→PT spring observation; no diagram or substitute mesh. */
public final class SpringWorldProbe {
    private static final boolean ENABLED = "1".equals(
        ExperimentAccess.getenv("RADIANCE_SPRING_DIRECT_WORLD"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final BlockPos CONTROLLER = new BlockPos(9, 129, 11);
    private static final BlockPos PARTNER = new BlockPos(14, 129, 11);
    private static final IdentityHashMap<PBRVertexConsumer, Boolean> ACTIVE = new IdentityHashMap<>();
    private static volatile Throwable serverFailure;
    private static volatile boolean placed;
    private static long start;
    private static boolean inputDumped;
    private static int step, segments, consumers, batches, vertices, normal2, alpha9,
        colorMix2, textureId;

    private SpringWorldProbe() {}

    public static void segment() {
        if (ENABLED) segments++;
    }

    public static void consumer(PBRVertexConsumer value) {
        if (!ENABLED) return;
        synchronized (ACTIVE) { ACTIVE.put(value, Boolean.TRUE); consumers++; }
    }

    public static void finished(PBRVertexConsumer consumer, MeshData mesh) {
        if (!ENABLED) return;
        synchronized (ACTIVE) { if (ACTIVE.remove(consumer) == null) return; }
        if (mesh == null) return;
        int count = mesh.drawState().vertexCount();
        int stride = mesh.drawState().format().getVertexSize();
        if (count <= 0 || stride != 128 || mesh.drawState().mode()
            != com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            throw new IllegalStateException("World spring PBR geometry is not full 128B QUADS");
        int[] offsets = PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElement();
        int norm = offsets[PBRVertexFormatElements.PBR_USE_NORM.id()];
        int alpha = offsets[PBRVertexFormatElements.PBR_POST_BASE.id()] + 12;
        int color = offsets[PBRVertexFormatElements.PBR_USE_COLOR_LAYER.id()];
        int tex = offsets[PBRVertexFormatElements.PBR_TEXTURE_ID.id()];
        ByteBuffer bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        if (bytes.remaining() != count * stride)
            throw new IllegalStateException("World spring PBR byte count changed");
        int localTexture = bytes.getInt(tex);
        for (int i = 0; i < count; i++) {
            int base = i * stride;
            if (bytes.getInt(base + norm) == 2) normal2++;
            if (bytes.getInt(base + alpha) == PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW) alpha9++;
            if (bytes.getInt(base + color) == 2) colorMix2++;
            if (bytes.getInt(base + tex) != localTexture)
                throw new IllegalStateException("World spring batch changed body texture per vertex");
        }
        if (localTexture <= 0 || (textureId != 0 && textureId != localTexture))
            throw new IllegalStateException("World spring PBR texture ID is missing or unstable");
        textureId = localTexture;
        if (!inputDumped && ExperimentAccess.getenv("RADIANCE_SPRING_TWIST_STEP") != null) {
            try {
                var folder = Minecraft.getInstance().gameDirectory.toPath().resolve("radiance-audit/spring-world");
                Files.createDirectories(folder);
                byte[] data = new byte[bytes.remaining()];
                bytes.duplicate().get(data);
                Files.write(folder.resolve("producer-pbr128.bin"), data);
                Files.writeString(folder.resolve("producer-input.json"), "{\"vertices\":" + count
                    + ",\"stride\":128,\"twistStepDegrees\":"
                    + Double.parseDouble(ExperimentAccess.getenv("RADIANCE_SPRING_TWIST_STEP")) + "}");
                inputDumped = true;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Isolated spring input dump", failure);
            }
        }
        vertices += count;
        batches++;
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || mc.level == null || mc.player == null) return;
        var root = mc.gameDirectory.toPath();
        if (!Files.isRegularFile(root.resolve(".radiance-acceptance")))
            throw new IllegalStateException("World spring probe requires isolated marker");
        long window = mc.getWindow().getWindow();
        if (mc.getWindow().getWidth() <= 0 || mc.getWindow().getHeight() <= 0
            || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) != 0
            || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == 0) return;
        if (start == 0) { start = System.nanoTime(); LOG.info("SPRING_WORLD world-ready"); }
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        if (serverFailure != null) throw new IllegalStateException("World spring placement failed", serverFailure);
        try {
            if (step == 0 && seconds >= 3) {
                step = 1;
                var server = mc.getSingleplayerServer();
                if (server == null) throw new IllegalStateException("Integrated server missing");
                server.execute(() -> {
                    try {
                        var level = server.overworld();
                        BlockPos origin = new BlockPos(8, 128, 8);
                        for (var pos : BlockPos.betweenClosed(origin, origin.offset(7, 4, 6)))
                            if (!level.getBlockState(pos).isAir())
                                throw new IllegalStateException("World spring area occupied " + pos);
                        for (int x = 0; x < 8; x++) for (int z = 0; z < 7; z++)
                            level.setBlock(origin.offset(x, 0, z), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                        level.setBlock(origin.offset(0, 1, 3), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                        level.setBlock(origin.offset(7, 1, 3), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                        var spring = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                            "simulated", "spring"));
                        if (spring == Blocks.AIR) throw new IllegalStateException("Simulated spring missing");
                        level.setBlock(CONTROLLER, spring.defaultBlockState().setValue(
                            net.minecraft.world.level.block.DirectionalBlock.FACING,
                            net.minecraft.core.Direction.EAST), 3);
                        level.setBlock(PARTNER, spring.defaultBlockState().setValue(
                            net.minecraft.world.level.block.DirectionalBlock.FACING,
                            net.minecraft.core.Direction.WEST), 3);
                        Class<?> type = Class.forName(
                            "dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity");
                        Object first = level.getBlockEntity(CONTROLLER), second = level.getBlockEntity(PARTNER);
                        if (!type.isInstance(first) || !type.isInstance(second))
                            throw new IllegalStateException("Main-world spring block entities missing");
                        type.getMethod("setController", boolean.class).invoke(first, true);
                        type.getMethod("setController", boolean.class).invoke(second, false);
                        type.getMethod("setDesiredLength", double.class).invoke(first, 6.0);
                        type.getMethod("setDesiredLength", double.class).invoke(second, 6.0);
                        type.getMethod("setPartnerPos", BlockPos.class, UUID.class)
                            .invoke(first, PARTNER, null);
                        type.getMethod("setPartnerPos", BlockPos.class, UUID.class)
                            .invoke(second, CONTROLLER, null);
                        ((net.minecraft.world.level.block.entity.BlockEntity) first).setChanged();
                        ((net.minecraft.world.level.block.entity.BlockEntity) second).setChanged();
                        placed = true;
                        LOG.info("SPRING_WORLD source-pair {} -> {} desiredLength=6", CONTROLLER, PARTNER);
                    } catch (Throwable failure) { serverFailure = failure; }
                });
            } else if (step == 1 && placed && seconds >= 7) {
                if (!clientPair(mc)) {
                    if (seconds < 20) return;
                    throw new IllegalStateException("Main-world spring client pair did not arrive");
                }
                UUID playerId = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player == null) throw new IllegalStateException("Fixture player missing");
                        player.connection.teleport(11.5, 130.5, 19.5, 180, 18);
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                LOG.info("SPRING_WORLD client-pair-valid camera-requested");
                step = 2;
            } else if (step == 2 && seconds >= 13) {
                if (!clientPair(mc)) throw new IllegalStateException("Main-world spring pair lost");
                if (mc.player.position().distanceToSqr(11.5, 130.5, 19.5) > 4) {
                    if (seconds < 24) return;
                    throw new IllegalStateException("World spring camera teleport failed");
                }
                mc.player.setYRot(180); mc.player.yRotO = 180;
                mc.player.setXRot(18); mc.player.xRotO = 18;
                float yaw = mc.gameRenderer.getMainCamera().getYRot();
                float pitch = mc.gameRenderer.getMainCamera().getXRot();
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - 180)) > 2
                    || Math.abs(pitch - 18) > 2) {
                    if (seconds < 24) return;
                    throw new IllegalStateException("World spring render camera mismatch " + yaw + ',' + pitch);
                }
                if (segments <= 0 || consumers <= 0 || batches <= 0 || vertices <= 0
                    || normal2 != vertices || alpha9 != vertices || colorMix2 != vertices) {
                    if (seconds < 24) return;
                    throw new IllegalStateException("World spring producer/PBR contract not observed: segments="
                        + segments + " consumers=" + consumers + " batches=" + batches
                        + " vertices=" + vertices + " normal2=" + normal2 + " alpha9=" + alpha9
                        + " mix2=" + colorMix2);
                }
                var output = root.resolve("radiance-audit/spring-world");
                Files.createDirectories(output);
                var position = mc.player.position();
                Files.writeString(output.resolve("manifest.json"), "{\"x\":" + position.x
                    + ",\"y\":" + position.y + ",\"z\":" + position.z
                    + ",\"renderYaw\":" + yaw + ",\"renderPitch\":" + pitch
                    + ",\"segments\":" + segments + ",\"consumers\":" + consumers
                    + ",\"batches\":" + batches + ",\"vertices\":" + vertices
                    + ",\"normalMode2\":" + normal2 + ",\"alphaMode9\":" + alpha9
                    + ",\"surfaceMix2\":" + colorMix2 + ",\"textureId\":" + textureId
                    + ",\"mainWorldPair\":true}");
                Screenshot.grab(mc.gameDirectory, "spring-main-world.png", mc.getMainRenderTarget(),
                    message -> LOG.info("SPRING_WORLD screenshot {}", message.getString()));
                LOG.info("SPRING_WORLD captured producer/PBR segments={} batches={} vertices={}"
                    + " normal2={} alpha9={} mix2={} textureId={} camera={},{}",
                    segments, batches, vertices, normal2, alpha9, colorMix2, textureId, yaw, pitch);
                step = 3;
            } else if (step == 3 && seconds >= 17) {
                if (!Files.isRegularFile(root.resolve("screenshots/spring-main-world.png"))) {
                    if (seconds < 25) return;
                    throw new IllegalStateException("Main-world spring screenshot did not finish");
                }
                step = 4;
                LOG.info("SPRING_WORLD normal-stop");
                mc.stop();
            }
        } catch (Exception failure) {
            throw new IllegalStateException("World spring direct-producer step " + step, failure);
        }
    }

    private static boolean clientPair(Minecraft mc) throws Exception {
        Class<?> type = Class.forName("dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity");
        Object first = mc.level.getBlockEntity(CONTROLLER), second = mc.level.getBlockEntity(PARTNER);
        if (!type.isInstance(first) || !type.isInstance(second)) return false;
        return (Boolean) type.getMethod("isController").invoke(first)
            && !(Boolean) type.getMethod("isController").invoke(second)
            && type.getMethod("getPairedSpring").invoke(first) == second;
    }
}
