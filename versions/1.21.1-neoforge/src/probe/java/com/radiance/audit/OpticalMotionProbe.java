package com.radiance.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Capture moving reflections and transmission separately from camera motion. */
final class OpticalMotionProbe {
    private static final UUID MIRROR_TARGET = new UUID(0x613c6d8d1f027f54L, 0xb4ff77b16d82d102L);
    private static final UUID GLASS_TARGET = new UUID(0x613c6d8d1f027f54L, 0xb4ff77b16d82d103L);
    private static final UUID CHAIN_TARGET = new UUID(0x613c6d8d1f027f54L, 0xb4ff77b16d82d104L);
    private static final boolean STRESS = Boolean.getBoolean("radiance.audit.opticalMotionStress");
    private static final String[] PASSES = {
        "mirror-object", "mirror-camera", "glass-object", "glass-camera"
    };
    private static int pass;
    private static int frames;
    private static long start;
    private static boolean positioned;
    private static Display.BlockDisplay activeTarget;
    private static Vec3 nextTargetPosition;
    private static Vec3 lastRenderedPosition;
    private static double maximumPositionCorrection;

    private OpticalMotionProbe() {}

    private static String summon(UUID target, double x, double z, String block) {
        return "summon block_display "
                + x
                + " 67 "
                + z
                + " {UUID:[I;"
                + (int) (target.getMostSignificantBits() >>> 32)
                + ","
                + (int) target.getMostSignificantBits()
                + ","
                + (int) (target.getLeastSignificantBits() >>> 32)
                + ","
                + (int) target.getLeastSignificantBits()
                + "],block_state:{Name:\"minecraft:"
                + block
                + "\"}}";
    }

    private static void beforeRender(RenderFrameEvent.Pre event) {
        if (activeTarget == null || activeTarget.isRemoved()) return;
        if (lastRenderedPosition != null) {
            maximumPositionCorrection =
                    Math.max(
                            maximumPositionCorrection,
                            activeTarget.position().distanceTo(lastRenderedPosition));
        }
        // A pending Display interpolation can still run during the client tick.
        activeTarget.setPos(nextTargetPosition);
        activeTarget.setOldPosAndRot();
        lastRenderedPosition = activeTarget.position();
    }

    static boolean poll(Minecraft mc, Map<String, Object> result) throws IOException {
        if (start == 0) {
            if (!Files.isRegularFile(
                    mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")))
                throw new IllegalStateException("Owned optical fixture required");
            start = System.nanoTime();
            for (String command :
                    new String[] {
                        "gamerule doMobSpawning false",
                        "gamerule doDaylightCycle false",
                        "weather clear",
                        "time set noon",
                        "gamemode creative @a",
                        "item replace entity @a weapon.mainhand with diamond_sword",
                        "fill -8 65 -5 25 65 18 white_concrete",
                        "fill -4 66 9 5 73 9 iron_block",
                        "fill -4 66 -4 5 72 -4 blue_concrete",
                        "fill 12 66 5 21 73 5 light_blue_stained_glass",
                        "fill 12 66 7 21 73 7 pink_stained_glass",
                        "fill 12 66 15 21 72 15 yellow_concrete",
                        summon(MIRROR_TARGET, -0.25, -1, "magenta_concrete"),
                        summon(GLASS_TARGET, 15.75, 12, "magenta_concrete"),
                        "tp @a 0.5 66 2.5 0 0"
                    }) DrawingCensusProbe.command(mc, command);
            NeoForge.EVENT_BUS.addListener(OpticalMotionProbe::beforeRender);
            return false;
        }
        if (System.nanoTime() - start > 90_000_000_000L)
            throw new IllegalStateException("Optical fixture timed out at " + PASSES[pass]);
        if (!mc.player.getMainHandItem().is(Items.DIAMOND_SWORD)
                || !mc.level.getBlockState(new BlockPos(0, 68, 9)).is(Blocks.IRON_BLOCK)
                || !mc.level
                        .getBlockState(new BlockPos(16, 68, 5))
                        .is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                || !mc.level.getBlockState(new BlockPos(16, 68, 7)).is(Blocks.PINK_STAINED_GLASS))
            return false;
        boolean glass = pass >= 2;
        boolean chain = STRESS && pass == 1;
        boolean occluder = STRESS && pass == 2;
        boolean movingObject = (pass & 1) == 0;
        double x = glass ? 16.5 : 0.5;
        double z = glass ? 12 : chain ? 6 : -1;
        if (!positioned) {
            if (chain) {
                DrawingCensusProbe.command(mc, "fill -4 66 -4 5 72 -4 iron_block");
                DrawingCensusProbe.command(mc, summon(CHAIN_TARGET, 3.0, z, "lime_concrete"));
            }
            if (STRESS && glass)
                DrawingCensusProbe.command(
                        mc, "setblock 16 67 10 " + (occluder ? "white_concrete" : "air"));
            DrawingCensusProbe.command(mc, "tp @a " + x + " 66 2.5 0 0");
            positioned = true;
            return false;
        }
        if ((chain && !mc.level.getBlockState(new BlockPos(0, 68, -4)).is(Blocks.IRON_BLOCK))
                || (STRESS
                        && glass
                        && mc.level
                                        .getBlockState(new BlockPos(16, 67, 10))
                                        .is(Blocks.WHITE_CONCRETE)
                                != occluder)) return false;
        UUID targetId = glass ? GLASS_TARGET : chain ? CHAIN_TARGET : MIRROR_TARGET;
        Display.BlockDisplay target = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Display.BlockDisplay display
                    && display.getUUID().equals(targetId)) {
                target = display;
                break;
            }
        }
        if (target == null) return false;
        if (Math.abs(mc.player.getX() - x) > 0.1) return false;
        if (!mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        double frame = Math.max(0, frames - 90);
        double offset = STRESS ? Math.min(frame, 12) * (occluder ? 0.36 : 0.16) : frame * 0.06;
        double targetX = chain ? 3.0 : occluder ? 14.4 : x - 0.75;
        activeTarget = target;
        nextTargetPosition = new Vec3(targetX + (movingObject ? offset : 0), 67, z);
        mc.player.setYRot(movingObject ? 0 : (float) (offset * (STRESS ? 6 : 4)));
        mc.player.yRotO = mc.player.getYRot();
        if (frames == 92) {
            for (String request : new String[] {"world", "nrd"})
                Files.writeString(
                        mc.gameDirectory
                                .toPath()
                                .resolve("radiance-" + request + "-capture.request"),
                        PASSES[pass]);
        }
        if (++frames < 110) return false;
        EntityTakeoverProbe.screenshot(mc, PASSES[pass] + ".png");
        result.put(
                PASSES[pass],
                Map.of(
                        "frames", frames,
                        "targetZ", z,
                        "movingObject", movingObject,
                        "stress", STRESS,
                        "doubleMirror", chain,
                        "occluder", occluder,
                        "maximumPositionCorrection", maximumPositionCorrection));
        frames = 0;
        positioned = false;
        activeTarget = null;
        lastRenderedPosition = null;
        maximumPositionCorrection = 0;
        return ++pass == PASSES.length;
    }
}
