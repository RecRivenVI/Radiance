package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import com.radiance.audit.BakedTakeoverProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.radiance.client.vertex.BakedModelTakeover", remap = false)
public abstract class TakeoverBakedComparisonMixin {
    @WrapMethod(method = "item")
    private static boolean audit$item(
            BakedModel model,
            ItemStack stack,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Operation<Boolean> original) {
        int before = BakedTakeoverProbe.pending(consumer);
        boolean handled = original.call(model, stack, light, overlay, pose, consumer);
        if (handled)
            BakedTakeoverProbe.compare(
                    consumer,
                    pose,
                    target -> {
                        var poses = new PoseStack();
                        poses.last().pose().set(pose.pose());
                        poses.last().normal().set(pose.normal());
                        Minecraft.getInstance()
                                .getItemRenderer()
                                .renderModelLists(model, stack, light, overlay, poses, target);
                    },
                    before);
        return handled;
    }

    @WrapMethod(method = "block")
    private static boolean audit$block(
            BakedModel model,
            float r,
            float g,
            float b,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Operation<Boolean> original) {
        int before = BakedTakeoverProbe.pending(consumer);
        boolean handled = original.call(model, r, g, b, light, overlay, pose, consumer);
        if (handled)
            BakedTakeoverProbe.compare(
                    consumer,
                    pose,
                    target ->
                            Minecraft.getInstance()
                                    .getBlockRenderer()
                                    .getModelRenderer()
                                    .renderModel(
                                            pose, target, null, model, r, g, b, light, overlay),
                    before);
        return handled;
    }
}
