package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.adapter.vanilla.VanillaEntityAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class VanillaAttachmentMixins {
    private VanillaAttachmentMixins() {}

    @Mixin(EntityRenderer.class)
    public interface Leashes extends VanillaEntityAttachments.Leash {
        @Invoker("renderLeash")
        void radiance$leash(
                Entity entity,
                float partial,
                PoseStack pose,
                MultiBufferSource output,
                Entity holder);
    }

    @Mixin(EntityRenderDispatcher.class)
    public abstract static class Dispatchers implements VanillaEntityAttachments.Dispatcher {
        @Shadow private boolean renderHitBoxes;
        @Shadow private Quaternionf cameraOrientation;

        @Invoker("renderFlame")
        protected abstract void radiance$flame(
                PoseStack pose, MultiBufferSource output, Entity entity, Quaternionf camera);

        @Invoker("renderHitbox")
        private static void radiance$hitbox(
                PoseStack pose,
                VertexConsumer output,
                Entity entity,
                float partial,
                float r,
                float g,
                float b) {
            throw new AssertionError();
        }

        public void radiance$attachments(
                Entity entity, float partial, PoseStack pose, MultiBufferSource output) {
            if (entity.displayFireAnimation()) {
                var dispatcher = (EntityRenderDispatcher) (Object) this;
                var offset = dispatcher.getRenderer(entity).getRenderOffset(entity, partial);
                pose.pushPose();
                try {
                    pose.translate(offset.x, offset.y, offset.z);
                    radiance$flame(
                            pose,
                            output,
                            entity,
                            Mth.rotationAroundAxis(
                                    Mth.Y_AXIS, cameraOrientation, new Quaternionf()));
                } finally {
                    pose.popPose();
                }
            }
            if (renderHitBoxes
                    && !entity.isInvisible()
                    && !Minecraft.getInstance().showOnlyReducedInfo())
                radiance$hitbox(
                        pose, output.getBuffer(RenderType.lines()), entity, partial, 1, 1, 1);
        }
    }
}
