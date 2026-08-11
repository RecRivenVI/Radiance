package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.render.LineSources;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(
        targets =
                "dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer",
        remap = false)
public abstract class SimulatedRopeLineSourceMixins {
    @WrapMethod(method = "renderOutline", remap = false)
    private static void radiance$registerSource(
            PoseStack pose,
            MultiBufferSource buffers,
            float radius,
            it.unimi.dsi.fastutil.objects.ObjectArrayList<?> points,
            net.minecraft.core.BlockPos origin,
            Operation<Void> original) {
        try (var source = LineSources.enter(LineSources.SIMULATED_ROPE)) {
            original.call(pose, buffers, radius, points, origin);
        }
    }
}
