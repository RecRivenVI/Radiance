package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.vanilla.*;
import com.radiance.client.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Source state selects immutable procedural meshes; no original renderer is invoked. */
public final class VanillaProceduralEntityMixins {
    private VanillaProceduralEntityMixins() {}

    @Mixin(FishingHookRenderer.class)
    public abstract static class Fishing implements VanillaEntityReplacement.Source {
        @Shadow @Final private static RenderType RENDER_TYPE;

        @Invoker("getPlayerHandPos")
        protected abstract net.minecraft.world.phys.Vec3 radiance$hand(
                net.minecraft.world.entity.player.Player player, float swing, float partial);

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var hook = (net.minecraft.world.entity.projectile.FishingHook) value;
            var player = hook.getPlayerOwner();
            if (player == null) return false;
            pose.pushPose();
            pose.pushPose();
            pose.scale(.5F, .5F, .5F);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            LocalMeshTakeover.request(
                    VanillaProjectileMeshes.HOOK,
                    pose.last(),
                    buffers.getBuffer(RENDER_TYPE),
                    light,
                    -1,
                    VanillaProjectileMeshes.HOOK);
            pose.popPose();
            float swing = Mth.sin(Mth.sqrt(player.getAttackAnim(partial)) * (float) Math.PI);
            var hand = radiance$hand(player, swing, partial);
            var end = hook.getPosition(partial).add(0, .25, 0);
            float x = (float) (hand.x - end.x),
                    y = (float) (hand.y - end.y),
                    z = (float) (hand.z - end.z);
            var out = buffers.getBuffer(RenderType.lineStrip());
            for (int i = 0; i <= 16; i++) {
                float t = i / 16F,
                        next = (i + 1) / 16F,
                        px = x * t,
                        py = y * (t * t + t) * .5F + .25F,
                        pz = z * t;
                float dx = x * next - px,
                        dy = y * (next * next + next) * .5F + .25F - py,
                        dz = z * next - pz,
                        length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                out.addVertex(pose.last(), px, py, pz)
                        .setColor(0xff000000)
                        .setNormal(pose.last(), dx / length, dy / length, dz / length);
            }
            pose.popPose();
            return true;
        }
    }

    @Mixin(LightningBoltRenderer.class)
    public abstract static class Lightning implements VanillaEntityReplacement.Source {
        public boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            VanillaLightningRequests.collect(
                    ((net.minecraft.world.entity.LightningBolt) entity).seed, pose, buffers);
            return false;
        }
    }

    @Mixin(ArrowRenderer.class)
    public abstract static class Arrows implements VanillaEntityReplacement.Source {
        @SuppressWarnings("unchecked")
        public boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var arrow = (AbstractArrow) entity;
            pose.pushPose();
            pose.mulPose(
                    Axis.YP.rotationDegrees(Mth.lerp(partial, arrow.yRotO, arrow.getYRot()) - 90));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, arrow.xRotO, arrow.getXRot())));
            float shake = arrow.shakeTime - partial;
            if (shake > 0) pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3) * shake));
            pose.mulPose(Axis.XP.rotationDegrees(45));
            pose.scale(.05625F, .05625F, .05625F);
            pose.translate(-4, 0, 0);
            var texture = ((EntityRenderer<Entity>) (Object) this).getTextureLocation(entity);
            LocalMeshTakeover.request(
                    VanillaProjectileMeshes.ARROW,
                    pose.last(),
                    buffers.getBuffer(RenderType.entityCutout(texture)),
                    light,
                    -1,
                    VanillaProjectileMeshes.ARROW);
            pose.popPose();
            return true;
        }
    }

    @Mixin(DragonFireballRenderer.class)
    public abstract static class DragonFireballs implements VanillaEntityReplacement.Source {
        @Shadow @Final private static RenderType RENDER_TYPE;

        public boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            pose.pushPose();
            pose.scale(2, 2, 2);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            LocalMeshTakeover.request(
                    VanillaProjectileMeshes.FIREBALL,
                    pose.last(),
                    buffers.getBuffer(RENDER_TYPE),
                    light,
                    -1,
                    VanillaProjectileMeshes.FIREBALL);
            pose.popPose();
            return true;
        }
    }

    @Mixin(ExperienceOrbRenderer.class)
    public abstract static class Orbs implements VanillaEntityReplacement.Source {
        @Shadow @Final private static RenderType RENDER_TYPE;

        public boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var orb = (net.minecraft.world.entity.ExperienceOrb) entity;
            int icon = orb.getIcon();
            float time = (orb.tickCount + partial) / 2;
            int red = (int) ((Mth.sin(time) + 1) * .5F * 255),
                    blue = (int) ((Mth.sin(time + (float) (Math.PI * 4 / 3)) + 1) * .1F * 255);
            int color = net.minecraft.util.FastColor.ARGB32.color(128, red, 255, blue);
            pose.pushPose();
            pose.translate(0, .1F, 0);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.scale(.3F, .3F, .3F);
            var mesh = VanillaProjectileMeshes.ORBS[icon];
            LocalMeshTakeover.request(
                    mesh, pose.last(), buffers.getBuffer(RENDER_TYPE), light, color, mesh);
            pose.popPose();
            return true;
        }
    }
}
