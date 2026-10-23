package com.radiance.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

/** Exercises vanilla's actual camera selection and block overlay producers, not native helpers. */
final class VanillaScreenFixture {
    private static final EntityType<?>[] CAMERAS = {
        EntityType.CREEPER, EntityType.SPIDER, EntityType.ENDERMAN
    };
    private static int phase;
    private static long since;

    private VanillaScreenFixture() {}

    static void start(Minecraft mc, long now) {
        DrawingCensus.INSTANCE.window("R5-entity-post-block");
        DrawingCensusProbe.command(mc, "gamemode creative @a");
        phase = 0;
        camera(mc, phase);
        since = now;
    }

    private static void camera(Minecraft mc, int index) {
        var entity = java.util.Objects.requireNonNull(CAMERAS[index].create(mc.level));
        entity.moveTo(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot());
        mc.setCameraEntity(entity);
    }

    static boolean poll(Minecraft mc, long now) {
        if (now - since < 2_000_000_000L) return false;
        since = now;
        phase++;
        if (phase < CAMERAS.length) {
            camera(mc, phase);
        } else if (phase == 3) {
            mc.setCameraEntity(mc.player);
            DrawingCensusProbe.command(mc, "tp @a 100 100 100 0 0");
        } else if (phase == 4) {
            DrawingCensusProbe.command(mc, "fill 99 100 99 101 102 101 stone");
        } else if (phase == 5) {
            DrawingCensusProbe.command(mc, "fill 99 100 99 101 102 101 air");
        } else {
            var rows = DrawingCensus.INSTANCE.snapshot();
            for (String source :
                    java.util.List.of(
                            "entity.CREEPER", "entity.SPIDER", "entity.INVERT", "camera.block")) {
                long consumed =
                        rows.entrySet().stream()
                                .filter(row -> row.getKey().source().equals(source))
                                .mapToLong(row -> row.getValue().consumed)
                                .sum();
                if (consumed == 0)
                    throw new IllegalStateException("Screen source not consumed: " + source);
            }
            com.mojang.logging.LogUtils.getLogger()
                    .info("VANILLA_SCREEN_FIXTURE PASS: three entity effects and block overlay");
            return true;
        }
        return false;
    }
}
