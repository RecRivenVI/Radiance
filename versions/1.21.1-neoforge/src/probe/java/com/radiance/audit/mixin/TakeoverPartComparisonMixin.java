package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import com.radiance.audit.PartModelProbe;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;

/** Compare at the adapter, leaving all vanilla source classes unmodified by the observer. */
@Mixin(targets = "com.radiance.client.vertex.PartModelCapture", remap = false)
public abstract class TakeoverPartComparisonMixin {
    @WrapMethod(method = "capture")
    private static boolean audit$part(
            Object part,
            List<ModelPart.Cube> cubes,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int color,
            Operation<Boolean> original) {
        com.radiance.audit.RendererReplacementProbe.referencePart(
                part, cubes, pose, consumer, light, overlay, color);
        boolean[] handled = {false};
        PartModelProbe.compile(
                cubes,
                pose,
                consumer,
                light,
                overlay,
                color,
                () ->
                        handled[0] =
                                original.call(part, cubes, pose, consumer, light, overlay, color));
        return handled[0];
    }
}
