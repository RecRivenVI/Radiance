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
                "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverRenderer",
        remap = false)
public abstract class SimulatedLeverLineSourceMixins {
    @WrapMethod(method = "renderOutline", remap = false)
    private static void radiance$registerSource(
            dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity be,
            PoseStack pose,
            MultiBufferSource buffers,
            float angle,
            Operation<Void> original) {
        try (var source = LineSources.enter(LineSources.SIMULATED_LEVER)) {
            original.call(be, pose, buffers, angle);
        }
    }
}
