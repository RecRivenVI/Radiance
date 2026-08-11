package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.adapter.vanilla.VanillaModelPose;
import com.radiance.audit.RendererReplacementProbe;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.radiance.client.vertex.BoundPartCapture", remap = false)
public abstract class ReplacementInputComparisonMixin {
    @WrapMethod(method = "add")
    private void audit$input(
            List<VanillaModelPose.Part> parts,
            RenderType layer,
            VertexConsumer consumer,
            int light,
            int overlay,
            Operation<Void> original) {
        RendererReplacementProbe.candidateParts(parts, consumer, light, overlay);
        original.call(parts, layer, consumer, light, overlay);
    }
}
