package com.radiance.adapter.vanilla;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.Test;

class VanillaBlockDependenciesTest {
    @Test
    void resamplesActualLightShadeAndTintBeforeAWorldTemplateCanBeReused() {
        var values = new HashMap<String, Object>();
        values.put("getBrightness", 7);
        values.put("getRawBrightness", 6);
        values.put("getShade", .75F);
        values.put("getBlockTint", 0x123456);
        var world =
                (BlockAndTintGetter)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {BlockAndTintGetter.class},
                                (proxy, method, args) -> values.get(method.getName()));
        var position = new BlockPos(10, 64, -3);
        for (var changed :
                java.util.List.of(
                        "getBrightness", "getRawBrightness", "getShade", "getBlockTint")) {
            var dependencies = new VanillaBlockDependencies(world);
            dependencies.getBrightness(LightLayer.BLOCK, position);
            dependencies.getRawBrightness(position, 2);
            dependencies.getShade(Direction.UP, true);
            dependencies.getBlockTint(position, (biome, x, z) -> 0);
            assertTrue(dependencies.matches(world));
            Object original = values.get(changed);
            if (original instanceof Float) values.put(changed, .5F);
            else values.put(changed, 23);
            assertFalse(dependencies.matches(world), changed);
            values.put(changed, original);
            assertTrue(dependencies.matches(world));
        }
        var unknown = new VanillaBlockDependencies(world);
        unknown.getLightEngine();
        assertFalse(unknown.matches(world));
        var mutableTile = new VanillaBlockDependencies(world);
        mutableTile.getBlockEntity(position);
        assertFalse(mutableTile.matches(world));
    }
}
