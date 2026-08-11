package com.radiance.mixins.compatibility.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.compatibility.create.CreateValueBoxWorldGeometry;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueBox", remap = false)
public abstract class CreateValueBoxMixins {

    private static final String RENDER_METHOD =
        "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/createmod/catnip/render/SuperRenderTypeBuffer;"
            + "Lnet/minecraft/world/phys/Vec3;F)V";

    @WrapMethod(method = RENDER_METHOD)
    private void radiance$captureWorldGeometry(PoseStack poseStack,
        SuperRenderTypeBuffer bufferSource, Vec3 localPos, float partialTicks,
        Operation<Void> original) {
        try (CreateValueBoxWorldGeometry.CaptureToken capture =
                 CreateValueBoxWorldGeometry.begin(this)) {
            original.call(poseStack, bufferSource, localPos, partialTicks);
            capture.commit();
        }
    }

    @ModifyArg(method = RENDER_METHOD,
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllIcons;render("
                + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 1)
    private MultiBufferSource radiance$routeOutlineIcon(MultiBufferSource original) {
        return CreateValueBoxWorldGeometry.route(original);
    }

    @ModifyArg(method = RENDER_METHOD,
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueBox;renderContents("
                + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;)V"),
        index = 1)
    private MultiBufferSource radiance$routeContents(MultiBufferSource original) {
        return CreateValueBoxWorldGeometry.route(original);
    }

}
