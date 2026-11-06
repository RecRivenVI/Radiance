package com.radiance.audit;

import java.nio.file.Files;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/** Checks screenshots and frame ownership through resize and a world reopen. */
final class FrameExecutionProbe {
    private static final BlockPos TARGET = new BlockPos(2, 72, 3);
    private static int step, frames, width, height;
    private static long started;

    private FrameExecutionProbe() {}

    static boolean poll(Minecraft mc, long now, Map<String, Object> result)
            throws java.io.IOException {
        if (started != 0 && now - started > 120_000_000_000L)
            throw new IllegalStateException("Frame execution fixture timed out at step " + step);
        if (step == 3) {
            if (mc.level == null && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                step = 4;
                mc.createWorldOpenFlows()
                        .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
            }
            return false;
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return false;
        switch (step) {
            case 0 -> {
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return false;
                if (!Files.isRegularFile(
                        mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")))
                    throw new IllegalStateException("Owned world required");
                started = now;
                width = mc.getWindow().getWidth();
                height = mc.getWindow().getHeight();
                mc.options.hideGui = false;
                DrawingCensusProbe.command(mc, "gamerule doDaylightCycle false");
                DrawingCensusProbe.command(mc, "gamerule doWeatherCycle false");
                DrawingCensusProbe.command(mc, "weather clear");
                DrawingCensusProbe.command(mc, "time set noon");
                DrawingCensusProbe.command(mc, "fill -10 70 -8 14 70 18 stone");
                DrawingCensusProbe.command(mc, "fill -4 71 7 8 77 7 blue_concrete");
                DrawingCensusProbe.command(mc, "fill 1 71 5 3 73 5 blue_stained_glass");
                DrawingCensusProbe.command(mc, "setblock 2 72 3 sea_lantern");
                DrawingCensusProbe.command(mc, "gamemode creative @a");
                DrawingCensusProbe.command(
                        mc, "item replace entity @a weapon.mainhand with diamond_sword");
                DrawingCensusProbe.command(
                        mc, "scoreboard objectives add frame_execution dummy \"Frame ownership\"");
                DrawingCensusProbe.command(mc, "scoreboard players set Fixture frame_execution 42");
                DrawingCensusProbe.command(
                        mc, "scoreboard objectives setdisplay sidebar frame_execution");
                DrawingCensusProbe.command(mc, "tp @a 2.5 71 0.5 0 0");
                step = 1;
            }
            case 1 -> {
                if (!mc.level.getBlockState(TARGET).is(Blocks.SEA_LANTERN)
                        || !mc.player.getMainHandItem().is(Items.DIAMOND_SWORD)
                        || !(mc.hitResult instanceof BlockHitResult hit)
                        || !hit.getBlockPos().equals(TARGET)
                        || ++frames < 180) return false;
                mc.gui.getChat().clearMessages(false);
                if (frames == 180 && Boolean.getBoolean("radiance.audit.capture")) {
                    Files.writeString(
                            mc.gameDirectory.toPath().resolve("radiance-display-capture.request"),
                            "frame-execution\n");
                    Files.writeString(
                            mc.gameDirectory.toPath().resolve("radiance-world-capture.request"),
                            "frame-execution\n");
                }
                if (frames < 210) return false;
                EntityTakeoverProbe.screenshot(mc, "frame-execution.png");
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width - 320, height - 180);
                step = 2;
                frames = 0;
            }
            case 2 -> {
                if (mc.getWindow().getWidth() != width - 320
                        || mc.getWindow().getHeight() != height - 180
                        || ++frames < 60) return false;
                EntityTakeoverProbe.screenshot(mc, "frame-execution-resized.png");
                result.put("resized", true);
                step = 3;
                mc.level.disconnect();
                mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                mc.setScreen(new TitleScreen());
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
                frames = 0;
            }
            case 4 -> {
                if (mc.getWindow().getWidth() != width
                        || mc.getWindow().getHeight() != height
                        || !mc.level.getBlockState(TARGET).is(Blocks.SEA_LANTERN)
                        || ++frames < 120) return false;
                EntityTakeoverProbe.screenshot(mc, "frame-execution-reentered.png");
                result.put("reentered", true);
                step = 5;
            }
            case 5 -> {
                var screenshots = mc.gameDirectory.toPath().resolve("screenshots");
                return Files.isRegularFile(screenshots.resolve("frame-execution.png"))
                        && Files.isRegularFile(screenshots.resolve("frame-execution-resized.png"))
                        && Files.isRegularFile(
                                screenshots.resolve("frame-execution-reentered.png"));
            }
            default -> throw new IllegalStateException("Unexpected frame execution step " + step);
        }
        return false;
    }
}
