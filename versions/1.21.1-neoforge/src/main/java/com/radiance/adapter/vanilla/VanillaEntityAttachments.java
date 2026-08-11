package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;

/** Independent source algorithms; attachments do not determine the body's resource eligibility. */
public final class VanillaEntityAttachments {
    private VanillaEntityAttachments() {}

    public interface Dispatcher {
        void radiance$attachments(
                Entity entity, float partial, PoseStack pose, MultiBufferSource buffers);
    }

    public interface Leash {
        void radiance$leash(
                Entity entity,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                Entity holder);
    }

    public static void leash(
            EntityRenderer<?> renderer,
            Entity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource output) {
        if (entity instanceof Leashable leash && leash.getLeashHolder() != null)
            ((Leash) renderer)
                    .radiance$leash(entity, partial, pose, output, leash.getLeashHolder());
    }

    public static void collect(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource output) {
        ((Dispatcher) dispatcher).radiance$attachments(entity, partial, pose, output);
    }
}
