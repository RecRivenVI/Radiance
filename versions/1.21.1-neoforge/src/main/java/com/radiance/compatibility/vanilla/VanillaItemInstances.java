package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;

/** Vanilla count/spread state for one item resource, shared by dropped and ominous items. */
public final class VanillaItemInstances {
    private VanillaItemInstances() {}

    public static void add(
            ItemStack stack,
            BakedModel model,
            RandomSource random,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        if (stack.isEmpty()) return;
        int count = stack.getCount();
        int copies = count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
        var scale = model.getTransforms().ground.scale;
        boolean solid = model.isGui3d();
        if (!solid)
            pose.translate(
                    -0F * (copies - 1) * .5F * scale.x(),
                    -0F * (copies - 1) * .5F * scale.y(),
                    -.09375F * (copies - 1) * .5F * scale.z());
        boolean spread =
                net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack)
                        .shouldSpreadAsEntity(stack);
        for (int i = 0; i < copies; i++) {
            pose.pushPose();
            if (i > 0 && spread) {
                if (solid)
                    pose.translate(
                            (random.nextFloat() * 2 - 1) * .15F,
                            (random.nextFloat() * 2 - 1) * .15F,
                            (random.nextFloat() * 2 - 1) * .15F);
                else
                    pose.translate(
                            (random.nextFloat() * 2 - 1) * .15F * .5F,
                            (random.nextFloat() * 2 - 1) * .15F * .5F,
                            0);
            }
            VanillaItemReplacement.render(
                    stack,
                    ItemDisplayContext.GROUND,
                    false,
                    pose,
                    buffers,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    model);
            pose.popPose();
            if (!solid) pose.translate(0F * scale.x(), 0F * scale.y(), .09375F * scale.z());
        }
    }
}
