package com.radiance.adapter.vanilla;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Queries made by vanilla tessellation, sampled again before reusing its world-context result. */
public final class VanillaBlockDependencies implements BlockAndTintGetter {
    private final BlockAndTintGetter source;
    private final Map<Object, Predicate<BlockAndTintGetter>> queries = new LinkedHashMap<>();
    private boolean repeatable = true;

    private record Query(String kind, Object a, Object b) {}

    VanillaBlockDependencies(BlockAndTintGetter source) {
        this.source = source;
    }

    private void remember(Object key, Predicate<BlockAndTintGetter> predicate) {
        queries.putIfAbsent(key, predicate);
    }

    public BlockAndTintGetter source() {
        return source;
    }

    public void unrepeatable() {
        repeatable = false;
    }

    public void color(
            net.minecraft.client.color.block.BlockColors colors,
            BlockState state,
            BlockPos value,
            int tint,
            int result) {
        var pos = value.immutable();
        remember(
                new Query("color", java.util.List.of(state, pos), tint),
                level -> colors.getColor(state, level, pos, tint) == result);
    }

    boolean matches(BlockAndTintGetter current) {
        if (current != source || !repeatable) return false;
        for (var query : queries.values()) if (!query.test(current)) return false;
        return true;
    }

    public BlockState getBlockState(BlockPos value) {
        var pos = value.immutable();
        var state = source.getBlockState(pos);
        remember(new Query("block", pos, null), level -> level.getBlockState(pos) == state);
        return state;
    }

    public FluidState getFluidState(BlockPos value) {
        var pos = value.immutable();
        var state = source.getFluidState(pos);
        remember(new Query("fluid", pos, null), level -> level.getFluidState(pos) == state);
        return state;
    }

    public BlockEntity getBlockEntity(BlockPos pos) {
        // Tile internals are not immutable state; a reference comparison cannot certify them.
        repeatable = false;
        return source.getBlockEntity(pos);
    }

    public float getShade(Direction direction, boolean shade) {
        float value = source.getShade(direction, shade);
        remember(
                new Query("geometryShade", direction, shade),
                world ->
                        Float.floatToRawIntBits(world.getShade(direction, shade))
                                == Float.floatToRawIntBits(value));
        return value;
    }

    public float getShade(float x, float y, float z, boolean shade) {
        float value = source.getShade(x, y, z, shade);
        remember(
                new Query("geometryNormalShade", java.util.List.of(x, y, z), shade),
                world ->
                        Float.floatToRawIntBits(world.getShade(x, y, z, shade))
                                == Float.floatToRawIntBits(value));
        return value;
    }

    public int getBlockTint(BlockPos value, ColorResolver resolver) {
        var pos = value.immutable();
        int result = source.getBlockTint(pos, resolver);
        remember(
                new Query("tint", pos, resolver),
                level -> level.getBlockTint(pos, resolver) == result);
        return result;
    }

    public int getBrightness(LightLayer layer, BlockPos value) {
        var pos = value.immutable();
        int result = source.getBrightness(layer, pos);
        remember(
                new Query("geometryLight", pos, layer),
                world -> world.getBrightness(layer, pos) == result);
        return result;
    }

    public int getRawBrightness(BlockPos value, int subtract) {
        var pos = value.immutable();
        int result = source.getRawBrightness(pos, subtract);
        remember(
                new Query("geometryRawLight", pos, subtract),
                world -> world.getRawBrightness(pos, subtract) == result);
        return result;
    }

    public LevelLightEngine getLightEngine() {
        // Unobserved calls into the mutable engine are legal, but cannot validate a cached result.
        repeatable = false;
        return source.getLightEngine();
    }

    public int getHeight() {
        return source.getHeight();
    }

    public int getMinBuildHeight() {
        return source.getMinBuildHeight();
    }
}
