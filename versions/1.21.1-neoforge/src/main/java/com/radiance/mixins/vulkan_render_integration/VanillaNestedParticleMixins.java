package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.vanilla.*;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import net.minecraft.client.Camera;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;

public final class VanillaNestedParticleMixins {
    private VanillaNestedParticleMixins() {}

    @Mixin(MobAppearanceParticle.class)
    public abstract static class Apparitions extends Particle
            implements VanillaNestedParticleRequests.Source {
        protected Apparitions(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Shadow @Final private Model model;
        @Shadow @Final private RenderType renderType;

        public boolean radiance$nested(
                StorageVertexConsumerProvider output, Camera camera, float partial) {
            if (!PartModelCapture.ENABLED
                    || !SourceCompatibility.producer(getClass())
                    || !VanillaModelPose.supports(model)) return false;
            output.nestedParticle(this, System.identityHashCode(this));
            float progress = (age + partial) / lifetime;
            int colour =
                    FastColor.ARGB32.colorFromFloat(
                            .05F + .5F * Mth.sin(progress * (float) Math.PI), 1, 1, 1);
            var pose = new PoseStack();
            pose.mulPose(camera.rotation());
            pose.mulPose(Axis.XP.rotationDegrees(150 * progress - 60));
            pose.scale(1, -1, -1);
            pose.translate(0, -1.101F, 1.5F);
            // This source explicitly bypasses the lightmap using the fullbright model argument.
            try (var evaluation = VanillaModelPose.source(this);
                    var emission = PBRMaterialContext.pushAlbedoEmission(1)) {
                var requests = new BoundPartCapture(output);
                requests.add(
                        VanillaModelPose.extract(model, pose, colour),
                        renderType,
                        output.getBuffer(renderType),
                        15728880,
                        OverlayTexture.NO_OVERLAY);
                requests.submit();
            }
            return true;
        }
    }

    @Mixin(ItemPickupParticle.class)
    public abstract static class Pickups implements VanillaNestedParticleRequests.Source {
        @Shadow @Final private Entity itemEntity;
        @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;
        @Shadow private int life;
        @Shadow private double targetX, targetY, targetZ, targetXOld, targetYOld, targetZOld;

        public boolean radiance$nested(
                StorageVertexConsumerProvider output, Camera camera, float partial) {
            if (!PartModelCapture.ENABLED || !SourceCompatibility.producer(getClass()))
                return false;
            boolean living =
                    VanillaLivingReplacement.eligible(
                            entityRenderDispatcher, itemEntity, output, output);
            if (!living
                    && !VanillaEntityReplacement.eligible(
                            entityRenderDispatcher, itemEntity, output, output)) return false;
            output.nestedParticle(this, System.identityHashCode(this));
            float progress = (life + partial) / 3;
            progress *= progress;
            var target = camera.getPosition();
            var pose = new PoseStack();
            pose.translate(
                    Mth.lerp(progress, itemEntity.getX(), Mth.lerp(partial, targetXOld, targetX))
                            - target.x,
                    Mth.lerp(progress, itemEntity.getY(), Mth.lerp(partial, targetYOld, targetY))
                            - target.y,
                    Mth.lerp(progress, itemEntity.getZ(), Mth.lerp(partial, targetZOld, targetZ))
                            - target.z);
            int light = entityRenderDispatcher.getPackedLightCoords(itemEntity, partial);
            if (living)
                VanillaLivingReplacement.render(
                        entityRenderDispatcher,
                        (net.minecraft.world.entity.LivingEntity) itemEntity,
                        partial,
                        pose,
                        output,
                        output,
                        light);
            else
                VanillaEntityReplacement.render(
                        entityRenderDispatcher,
                        itemEntity,
                        itemEntity.getYRot(),
                        partial,
                        pose,
                        output,
                        output,
                        light);
            VanillaEntityAttachments.collect(
                    entityRenderDispatcher, itemEntity, partial, pose, output);
            return true;
        }
    }
}
