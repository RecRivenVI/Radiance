package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.client.render.RasterPreviewScope;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's optional raster lighter retains tint setup, without baking light into PT albedo. */
@Mixin(QuadLighter.class)
public abstract class VanillaQuadLightingMixins {
    @Shadow private float[] brightness;
    @Shadow private int[] lightmap;

    @Shadow
    public abstract void reset();

    @Unique private boolean radiance$preview;

    @WrapMethod(method = "setup")
    private void radiance$setup(
            BlockAndTintGetter level, BlockPos pos, BlockState state, Operation<Void> original) {
        boolean preview = RasterPreviewScope.active();
        // The source caches setup by position. A preview must not reuse an unlit world setup.
        if (preview != radiance$preview) reset();
        radiance$preview = preview;
        original.call(level, pos, state);
    }

    @WrapOperation(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/neoforged/neoforge/client/model/lighting/QuadLighter;computeLightingAt(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void radiance$lightingOnly(
            QuadLighter receiver,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Operation<Void> original) {
        if (RasterPreviewScope.active()) original.call(receiver, level, pos, state);
    }

    @WrapMethod(method = "computeLightingForQuad([IZ)V")
    private void radiance$unlit(int[] vertices, boolean shade, Operation<Void> original) {
        if (RasterPreviewScope.active()) original.call(vertices, shade);
        else {
            Arrays.fill(brightness, 1);
            Arrays.fill(lightmap, 0);
        }
    }
}
