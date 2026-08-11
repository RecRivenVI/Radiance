package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

/** Original built-in item definitions use the same part/BER executors as their world forms. */
public final class VanillaBuiltinItemRequests {
    private VanillaBuiltinItemRequests() {}

    public interface Source {
        boolean radiance$supportsItem(ItemStack stack);

        void radiance$collectItem(
                ItemStack stack,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay);
    }

    public static boolean supports(ItemStack stack) {
        var renderer = Minecraft.getInstance().getItemRenderer().getBlockEntityRenderer();
        return renderer instanceof Source source
                && SourceCompatibility.producer(renderer.getClass())
                && source.radiance$supportsItem(stack);
    }

    public static void collect(
            ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var owner = (StorageVertexConsumerProvider) buffers;
        var requests = new BoundPartCapture(owner);
        ((Source) Minecraft.getInstance().getItemRenderer().getBlockEntityRenderer())
                .radiance$collectItem(stack, pose, owner, requests, light, overlay);
        requests.submit();
    }
}
