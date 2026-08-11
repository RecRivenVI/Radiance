package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.vanilla.*;
import com.radiance.client.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Pure original placement/animation formulas feed the common part and item request executors. */
public final class VanillaEntityDefinitionMixins {
    @Mixin(net.minecraft.client.renderer.entity.FallingBlockRenderer.class)
    public abstract static class FallingBlocks implements VanillaEntityReplacement.Source {
        @Shadow @Final private net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher;

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            return com.radiance.adapter.vanilla.VanillaWorldBlockRequests.falling(
                    (net.minecraft.world.entity.item.FallingBlockEntity) value,
                    dispatcher,
                    pose,
                    buffers);
        }
    }

    private VanillaEntityDefinitionMixins() {}

    @Mixin(EnderDragonRenderer.class)
    public abstract static class Dragons implements VanillaEntityReplacement.Source {
        @Shadow @Final private EnderDragonRenderer.DragonModel model;
        @Shadow @Final private static RenderType RENDER_TYPE;
        @Shadow @Final private static RenderType EYES;
        @Shadow @Final private static RenderType DECAL;
        @Shadow @Final private static ResourceLocation DRAGON_EXPLODING_LOCATION;

        @Invoker("renderRays")
        private static void radiance$deathRays(
                PoseStack pose, float progress, com.mojang.blaze3d.vertex.VertexConsumer output) {
            throw new AssertionError();
        }

        public boolean radiance$supportsEntity(Entity value) {
            var dragon = (net.minecraft.world.entity.boss.enderdragon.EnderDragon) value;
            return com.radiance.mixin_related.SourceCompatibility.producer(model.getClass());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var dragon = (net.minecraft.world.entity.boss.enderdragon.EnderDragon) value;
            pose.pushPose();
            float body = (float) dragon.getLatencyPos(7, partial)[0],
                    pitch =
                            (float)
                                    (dragon.getLatencyPos(5, partial)[1]
                                            - dragon.getLatencyPos(10, partial)[1]);
            pose.mulPose(Axis.YP.rotationDegrees(-body));
            pose.mulPose(Axis.XP.rotationDegrees(pitch * 10));
            pose.translate(0, 0, 1);
            pose.scale(-1, -1, 1);
            pose.translate(0, -1.501F, 0);
            model.prepareMobModel(dragon, 0, 0, partial);
            var parts = ((VanillaDragonRequests.PoseSource) model).radiance$parts(pose, -1);
            if (dragon.dragonDeathTime > 0) {
                var explosion = RenderType.dragonExplosionAlpha(DRAGON_EXPLODING_LOCATION);
                int color =
                        net.minecraft.util.FastColor.ARGB32.color(
                                Mth.floor(dragon.dragonDeathTime / 200F * 255), -1);
                var fading =
                        parts.stream()
                                .map(
                                        part ->
                                                new VanillaModelPose.Part(
                                                        part.part(), part.pose(), color))
                                .toList();
                requests.add(
                        fading,
                        explosion,
                        buffers.getBuffer(explosion),
                        light,
                        OverlayTexture.NO_OVERLAY);
                requests.add(
                        parts,
                        DECAL,
                        buffers.getBuffer(DECAL),
                        light,
                        OverlayTexture.pack(0F, dragon.hurtTime > 0));
            } else
                requests.add(
                        parts,
                        RENDER_TYPE,
                        buffers.getBuffer(RENDER_TYPE),
                        light,
                        OverlayTexture.pack(0F, dragon.hurtTime > 0));
            requests.add(parts, EYES, buffers.getBuffer(EYES), light, OverlayTexture.NO_OVERLAY);
            if (dragon.dragonDeathTime > 0) {
                pose.pushPose();
                pose.translate(0, -1, -2);
                float progress = (dragon.dragonDeathTime + partial) / 200F;
                radiance$deathRays(pose, progress, buffers.getBuffer(RenderType.dragonRays()));
                radiance$deathRays(pose, progress, buffers.getBuffer(RenderType.dragonRaysDepth()));
                pose.popPose();
            }
            pose.popPose();
            if (dragon.nearestCrystal != null) {
                var crystal = dragon.nearestCrystal;
                VanillaDragonRequests.beam(
                        (float) (crystal.getX() - Mth.lerp(partial, dragon.xo, dragon.getX())),
                        (float) (crystal.getY() - Mth.lerp(partial, dragon.yo, dragon.getY()))
                                + EndCrystalRenderer.getY(crystal, partial),
                        (float) (crystal.getZ() - Mth.lerp(partial, dragon.zo, dragon.getZ())),
                        partial,
                        dragon.tickCount,
                        pose,
                        buffers,
                        light);
            }
            return true;
        }
    }

    @Mixin(FireworkEntityRenderer.class)
    public abstract static class Fireworks implements VanillaEntityReplacement.Source {
        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaItemReplacement.supportsWorldStack(
                    ((FireworkRocketEntity) entity).getItem());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var rocket = (FireworkRocketEntity) value;
            pose.pushPose();
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            if (rocket.isShotAtAngle()) {
                pose.mulPose(Axis.ZP.rotationDegrees(180));
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.XP.rotationDegrees(90));
            }
            VanillaItemReplacement.worldItem(
                    rocket.level(),
                    rocket.getItem(),
                    net.minecraft.world.item.ItemDisplayContext.GROUND,
                    pose,
                    buffers,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    rocket.getId());
            pose.popPose();
            return true;
        }
    }

    @Mixin(OminousItemSpawnerRenderer.class)
    public abstract static class OminousItems implements VanillaEntityReplacement.Source {
        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaItemReplacement.supportsWorldStack(
                    ((net.minecraft.world.entity.OminousItemSpawner) entity).getItem());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var spawner = (net.minecraft.world.entity.OminousItemSpawner) value;
            var stack = spawner.getItem();
            if (stack.isEmpty()) return false;
            pose.pushPose();
            if (spawner.tickCount <= 50) {
                float scale = Math.min(spawner.tickCount + partial, 50) / 50;
                pose.scale(scale, scale, scale);
            }
            var level = spawner.level();
            float previous = Mth.wrapDegrees((float) (level.getGameTime() - 1L)) * 40,
                    current = Mth.wrapDegrees((float) level.getGameTime()) * 40;
            pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial, previous, current)));
            var model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
            VanillaItemInstances.add(stack, model, level.random, pose, buffers, 15728880);
            pose.popPose();
            return false;
        }
    }

    @Mixin(TntRenderer.class)
    public abstract static class Tnt implements VanillaEntityReplacement.Source {
        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaBlockModelRequests.supports(
                    ((net.minecraft.world.entity.item.PrimedTnt) entity).getBlockState());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var tnt = (net.minecraft.world.entity.item.PrimedTnt) value;
            pose.pushPose();
            pose.translate(0, .5F, 0);
            int fuse = tnt.getFuse();
            if (fuse - partial + 1 < 10) {
                float factor = Mth.clamp(1 - (fuse - partial + 1) / 10, 0, 1);
                factor *= factor;
                factor *= factor;
                float scale = 1 + factor * .3F;
                pose.scale(scale, scale, scale);
            }
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.translate(-.5F, -.5F, .5F);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            VanillaBlockModelRequests.add(
                    tnt.getBlockState(),
                    pose,
                    buffers,
                    light,
                    fuse / 5 % 2 == 0
                            ? OverlayTexture.pack(OverlayTexture.u(1), 10)
                            : OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(LeashKnotRenderer.class)
    public abstract static class Knots implements VanillaEntityReplacement.Source {
        @Shadow @Final
        private LeashKnotModel<net.minecraft.world.entity.decoration.LeashFenceKnotEntity> model;

        @Shadow @Final private static ResourceLocation KNOT_LOCATION;

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var knot = (net.minecraft.world.entity.decoration.LeashFenceKnotEntity) value;
            pose.pushPose();
            pose.scale(-1, -1, 1);
            model.setupAnim(knot, 0, 0, 0, 0, 0);
            var type = model.renderType(KNOT_LOCATION);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(WindChargeRenderer.class)
    public abstract static class WindCharges implements VanillaEntityReplacement.Source {
        @Shadow @Final private WindChargeModel model;
        @Shadow @Final private static ResourceLocation TEXTURE_LOCATION;

        @Invoker("xOffset")
        protected abstract float radiance$xOffset(float time);

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var charge =
                    (net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge) value;
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (charge.tickCount < 2
                    && dispatcher.camera.getEntity().distanceToSqr(charge)
                            < (double) Mth.square(3.5F)) return false;
            float age = charge.tickCount + partial;
            var type = RenderType.breezeWind(TEXTURE_LOCATION, radiance$xOffset(age) % 1, 0);
            model.setupAnim(charge, 0, 0, age, 0, 0);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            return true;
        }
    }

    @Mixin(EndCrystalRenderer.class)
    public abstract static class Crystals implements VanillaEntityReplacement.Source {
        @Shadow @Final private net.minecraft.client.model.geom.ModelPart cube;
        @Shadow @Final private net.minecraft.client.model.geom.ModelPart glass;
        @Shadow @Final private net.minecraft.client.model.geom.ModelPart base;
        @Shadow @Final private static RenderType RENDER_TYPE;
        @Shadow @Final private static float SIN_45;

        public boolean radiance$supportsEntity(Entity value) {
            return true;
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var crystal = (net.minecraft.world.entity.boss.enderdragon.EndCrystal) value;
            pose.pushPose();
            float bob = EndCrystalRenderer.getY(crystal, partial),
                    angle = (crystal.time + partial) * 3;
            var out = buffers.getBuffer(RENDER_TYPE);
            pose.pushPose();
            pose.scale(2, 2, 2);
            pose.translate(0, -.5F, 0);
            if (crystal.showsBottom())
                requests.add(
                        VanillaModelPose.extract(base, pose, -1),
                        RENDER_TYPE,
                        out,
                        light,
                        OverlayTexture.NO_OVERLAY);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0, 1.5F + bob / 2, 0);
            pose.mulPose(
                    new org.joml.Quaternionf()
                            .setAngleAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45));
            requests.add(
                    VanillaModelPose.extract(glass, pose, -1),
                    RENDER_TYPE,
                    out,
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.scale(.875F, .875F, .875F);
            pose.mulPose(
                    new org.joml.Quaternionf()
                            .setAngleAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45));
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            requests.add(
                    VanillaModelPose.extract(glass, pose, -1),
                    RENDER_TYPE,
                    out,
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.scale(.875F, .875F, .875F);
            pose.mulPose(
                    new org.joml.Quaternionf()
                            .setAngleAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45));
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            requests.add(
                    VanillaModelPose.extract(cube, pose, -1),
                    RENDER_TYPE,
                    out,
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            pose.popPose();
            var target = crystal.getBeamTarget();
            if (target != null) {
                float x = (float) (target.getX() + .5 - crystal.getX());
                float y = (float) (target.getY() + .5 - crystal.getY());
                float z = (float) (target.getZ() + .5 - crystal.getZ());
                pose.pushPose();
                pose.translate(x, y, z);
                VanillaDragonRequests.beam(
                        -x, -y + bob, -z, partial, crystal.time, pose, buffers, light);
                pose.popPose();
            }
            return true;
        }
    }

    @Mixin(ItemFrameRenderer.class)
    public abstract static class Frames implements VanillaEntityReplacement.Source {
        @Shadow
        public abstract net.minecraft.world.phys.Vec3 getRenderOffset(
                net.minecraft.world.entity.decoration.ItemFrame entity, float partial);

        @Invoker("getFrameModelResourceLoc")
        protected abstract net.minecraft.client.resources.model.ModelResourceLocation
                radiance$frame(
                        net.minecraft.world.entity.decoration.ItemFrame frame,
                        net.minecraft.world.item.ItemStack item);

        @Invoker("getLightVal")
        protected abstract int radiance$light(
                net.minecraft.world.entity.decoration.ItemFrame frame, int glow, int ordinary);

        public boolean radiance$nameFirst() {
            return true;
        }

        public boolean radiance$supportsEntity(Entity entity) {
            var frame = (net.minecraft.world.entity.decoration.ItemFrame) entity;
            var item = frame.getItem();
            return (VanillaItemReplacement.supportsWorldStack(item)
                            || net.minecraft.world.item.MapItem.getSavedData(item, frame.level())
                                    != null)
                    && BakedModelTakeover.declared(
                            Minecraft.getInstance()
                                    .getModelManager()
                                    .getModel(radiance$frame(frame, item)));
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var frame = (net.minecraft.world.entity.decoration.ItemFrame) value;
            pose.pushPose();
            var direction = frame.getDirection();
            var offset = getRenderOffset(frame, partial);
            pose.translate(-offset.x(), -offset.y(), -offset.z());
            pose.translate(
                    direction.getStepX() * .46875,
                    direction.getStepY() * .46875,
                    direction.getStepZ() * .46875);
            pose.mulPose(Axis.XP.rotationDegrees(frame.getXRot()));
            pose.mulPose(Axis.YP.rotationDegrees(180 - frame.getYRot()));
            var item = frame.getItem();
            boolean invisible = frame.isInvisible();
            if (!invisible) {
                var model =
                        Minecraft.getInstance()
                                .getModelManager()
                                .getModel(radiance$frame(frame, item));
                pose.pushPose();
                pose.translate(-.5F, -.5F, -.5F);
                try (var emission =
                        PBRMaterialContext.pushAlbedoEmission(
                                frame.getType()
                                                == net.minecraft.world.entity.EntityType
                                                        .GLOW_ITEM_FRAME
                                        ? com.radiance.client.render.GlowItemFrameEmission.FRAME
                                        : 0)) {
                    BakedModelTakeover.requestBlock(
                            model,
                            1,
                            1,
                            1,
                            light,
                            OverlayTexture.NO_OVERLAY,
                            pose.last(),
                            buffers.getBuffer(Sheets.solidBlockSheet()));
                }
                pose.popPose();
            }
            if (!item.isEmpty()) {
                pose.translate(0, 0, invisible ? .5F : .4375F);
                var map = net.minecraft.world.item.MapItem.getSavedData(item, frame.level());
                int rotation = map == null ? frame.getRotation() : frame.getRotation() % 4 * 2;
                pose.mulPose(Axis.ZP.rotationDegrees(rotation * 360F / 8));
                if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                        .post(
                                new net.neoforged.neoforge.client.event.RenderItemInFrameEvent(
                                        frame,
                                        (ItemFrameRenderer<?>) (Object) this,
                                        pose,
                                        buffers,
                                        light))
                        .isCanceled()) {
                    if (map != null) {
                        pose.mulPose(Axis.ZP.rotationDegrees(180));
                        pose.scale(.0078125F, .0078125F, .0078125F);
                        pose.translate(-64, -64, -1);
                        int mapLight = radiance$light(frame, 15728850, light);
                        try (var emission =
                                PBRMaterialContext.pushAlbedoEmission(
                                        frame.getType()
                                                        == net.minecraft.world.entity.EntityType
                                                                .GLOW_ITEM_FRAME
                                                ? com.radiance.client.render.GlowItemFrameEmission
                                                        .fromPackedLight(mapLight)
                                                : 0)) {
                            Minecraft.getInstance()
                                    .gameRenderer
                                    .getMapRenderer()
                                    .render(
                                            pose,
                                            buffers,
                                            frame.getFramedMapId(item),
                                            map,
                                            true,
                                            mapLight);
                        }
                    } else {
                        pose.scale(.5F, .5F, .5F);
                        int itemLight = radiance$light(frame, 15728880, light);
                        try (var emission =
                                PBRMaterialContext.pushAlbedoEmission(
                                        frame.getType()
                                                        == net.minecraft.world.entity.EntityType
                                                                .GLOW_ITEM_FRAME
                                                ? com.radiance.client.render.GlowItemFrameEmission
                                                        .fromPackedLight(itemLight)
                                                : 0)) {
                            VanillaItemReplacement.worldItem(
                                    frame.level(),
                                    item,
                                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                                    pose,
                                    buffers,
                                    itemLight,
                                    OverlayTexture.NO_OVERLAY,
                                    frame.getId());
                        }
                    }
                }
            }
            pose.popPose();
            return true;
        }
    }

    @Mixin(ItemEntityRenderer.class)
    public abstract static class DroppedItems implements VanillaEntityReplacement.Source {
        @Shadow @Final private net.minecraft.util.RandomSource random;

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaItemReplacement.supportsWorldStack(
                    ((net.minecraft.world.entity.item.ItemEntity) entity).getItem());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var entity = (net.minecraft.world.entity.item.ItemEntity) value;
            var stack = entity.getItem();
            pose.pushPose();
            random.setSeed(ItemEntityRenderer.getSeedForItemStack(stack));
            var model =
                    Minecraft.getInstance()
                            .getItemRenderer()
                            .getModel(stack, entity.level(), null, entity.getId());
            boolean bob =
                    net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack)
                            .shouldBobAsEntity(stack);
            float offset =
                    bob
                            ? Mth.sin((entity.getAge() + partial) / 10 + entity.bobOffs) * .1F + .1F
                            : 0;
            pose.translate(
                    0,
                    offset
                            + .25F
                                    * model.getTransforms()
                                            .getTransform(
                                                    net.minecraft.world.item.ItemDisplayContext
                                                            .GROUND)
                                            .scale
                                            .y(),
                    0);
            pose.mulPose(Axis.YP.rotation(entity.getSpin(partial)));
            VanillaItemInstances.add(stack, model, random, pose, buffers, light);
            pose.popPose();
            return true;
        }
    }

    @Mixin(EvokerFangsRenderer.class)
    public abstract static class Fangs implements VanillaEntityReplacement.Source {
        @Shadow @Final private EvokerFangsModel<EvokerFangs> model;
        @Shadow @Final private static ResourceLocation TEXTURE_LOCATION;

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var fangs = (EvokerFangs) value;
            float progress = fangs.getAnimationProgress(partial);
            if (progress == 0) return false;
            float scale = 2;
            if (progress > .9F) scale *= (1 - progress) / .1F;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90 - fangs.getYRot()));
            pose.scale(-scale, -scale, scale);
            pose.translate(0, -.626, 0);
            pose.scale(.5F, .5F, .5F);
            model.setupAnim(fangs, progress, 0, 0, fangs.getYRot(), fangs.getXRot());
            var type = model.renderType(TEXTURE_LOCATION);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(MinecartRenderer.class)
    public abstract static class Minecarts implements VanillaEntityReplacement.Source {
        public boolean radiance$nameFirst() {
            return true;
        }

        @Shadow @Final
        protected EntityModel<net.minecraft.world.entity.vehicle.AbstractMinecart> model;

        @Shadow
        public abstract ResourceLocation getTextureLocation(
                net.minecraft.world.entity.vehicle.AbstractMinecart entity);

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaModelPose.supports(model)
                    && VanillaBlockModelRequests.supports(
                            ((net.minecraft.world.entity.vehicle.AbstractMinecart) entity)
                                    .getDisplayBlockState());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var cart = (net.minecraft.world.entity.vehicle.AbstractMinecart) value;
            pose.pushPose();
            long hash = (long) cart.getId() * 493286711L;
            hash = hash * hash * 4392167121L + hash * 98761L;
            pose.translate(
                    (((float) (hash >> 16 & 7L) + .5F) / 8 - .5F) * .004F,
                    (((float) (hash >> 20 & 7L) + .5F) / 8 - .5F) * .004F,
                    (((float) (hash >> 24 & 7L) + .5F) / 8 - .5F) * .004F);
            double x = Mth.lerp((double) partial, cart.xOld, cart.getX()),
                    y = Mth.lerp((double) partial, cart.yOld, cart.getY()),
                    z = Mth.lerp((double) partial, cart.zOld, cart.getZ());
            var track = cart.getPos(x, y, z);
            float pitch = Mth.lerp(partial, cart.xRotO, cart.getXRot());
            if (track != null) {
                var front = cart.getPosOffs(x, y, z, .3F);
                var back = cart.getPosOffs(x, y, z, -.3F);
                if (front == null) front = track;
                if (back == null) back = track;
                pose.translate(track.x - x, (front.y + back.y) / 2 - y, track.z - z);
                var direction = back.add(-front.x, -front.y, -front.z);
                if (direction.length() != 0) {
                    direction = direction.normalize();
                    yaw = (float) (Math.atan2(direction.z, direction.x) * 180 / Math.PI);
                    pitch = (float) (Math.atan(direction.y) * 73);
                }
            }
            pose.translate(0, .375F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(-pitch));
            float hurt = cart.getHurtTime() - partial,
                    damage = Math.max(0, cart.getDamage() - partial);
            if (hurt > 0)
                pose.mulPose(
                        Axis.XP.rotationDegrees(
                                Mth.sin(hurt) * hurt * damage / 10 * cart.getHurtDir()));
            var state = cart.getDisplayBlockState();
            if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) {
                pose.pushPose();
                pose.scale(.75F, .75F, .75F);
                pose.translate(-.5F, (cart.getDisplayOffset() - 8) / 16F, .5F);
                pose.mulPose(Axis.YP.rotationDegrees(90));
                int overlay = OverlayTexture.NO_OVERLAY;
                if (cart instanceof net.minecraft.world.entity.vehicle.MinecartTNT tnt) {
                    int fuse = tnt.getFuse();
                    if (fuse > -1 && fuse - partial + 1 < 10) {
                        float amount = Mth.clamp(1 - (fuse - partial + 1) / 10, 0, 1);
                        amount *= amount;
                        amount *= amount;
                        float scale = 1 + amount * .3F;
                        pose.scale(scale, scale, scale);
                    }
                    if (fuse > -1 && fuse / 5 % 2 == 0)
                        overlay = OverlayTexture.pack(OverlayTexture.u(1), 10);
                }
                VanillaBlockModelRequests.add(state, pose, buffers, light, overlay);
                pose.popPose();
            }
            pose.scale(-1, -1, 1);
            model.setupAnim(cart, 0, 0, 0, 0, 0);
            var type = model.renderType(getTextureLocation(cart));
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(NoopRenderer.class)
    public abstract static class Empty implements VanillaEntityReplacement.Source {
        public boolean radiance$collectEntity(
                Entity e,
                float yaw,
                float partial,
                PoseStack p,
                MultiBufferSource b,
                BoundPartCapture r,
                int light) {
            return true;
        }
    }

    @Mixin(BoatRenderer.class)
    public abstract static class Boats implements VanillaEntityReplacement.Source {
        @Shadow
        public abstract com.mojang.datafixers.util.Pair<ResourceLocation, ListModel<Boat>>
                getModelWithLocation(Boat boat);

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaModelPose.supports(getModelWithLocation((Boat) entity).getSecond());
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var boat = (Boat) value;
            pose.pushPose();
            pose.translate(0, .375F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            float hurt = boat.getHurtTime() - partial,
                    damage = Math.max(0, boat.getDamage() - partial);
            if (hurt > 0)
                pose.mulPose(
                        Axis.XP.rotationDegrees(
                                Mth.sin(hurt) * hurt * damage / 10 * boat.getHurtDir()));
            float bubble = boat.getBubbleAngle(partial);
            if (!Mth.equal(bubble, 0))
                pose.mulPose(
                        new org.joml.Quaternionf()
                                .setAngleAxis(bubble * (float) (Math.PI / 180), 1, 0, 1));
            var pair = getModelWithLocation(boat);
            var model = pair.getSecond();
            pose.scale(-1, -1, 1);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            model.setupAnim(boat, partial, 0, -.1F, 0, 0);
            var type = model.renderType(pair.getFirst());
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            if (!boat.isUnderWater() && model instanceof WaterPatchModel water) {
                var floor =
                        model
                                        instanceof
                                        com.radiance.client.render.BoatWaterExclusion.FloorSource
                                                source
                                ? source.radiance$waterFloor()
                                : null;
                com.radiance.client.render.BoatWaterExclusion.request(
                        water.waterPatch(),
                        floor,
                        pose,
                        buffers.getBuffer(RenderType.waterMask()),
                        light,
                        OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
            return true;
        }
    }

    @Mixin(ThrownItemRenderer.class)
    public abstract static class ThrownItems implements VanillaEntityReplacement.Source {
        @Shadow @Final private float scale;

        public boolean radiance$supportsEntity(Entity entity) {
            return VanillaItemReplacement.supportsWorldStack(((ItemSupplier) entity).getItem());
        }

        public boolean radiance$collectEntity(
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (entity.tickCount < 2 && dispatcher.camera.getEntity().distanceToSqr(entity) < 12.25)
                return false;
            pose.pushPose();
            pose.scale(scale, scale, scale);
            pose.mulPose(dispatcher.cameraOrientation());
            var renderer = Minecraft.getInstance().getItemRenderer();
            var stack = ((ItemSupplier) entity).getItem();
            var model = renderer.getModel(stack, entity.level(), null, entity.getId());
            VanillaItemReplacement.render(
                    stack,
                    net.minecraft.world.item.ItemDisplayContext.GROUND,
                    false,
                    pose,
                    buffers,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    model);
            pose.popPose();
            return true;
        }
    }

    @Mixin(LlamaSpitRenderer.class)
    public abstract static class Spit implements VanillaEntityReplacement.Source {
        @Shadow @Final private LlamaSpitModel<LlamaSpit> model;
        @Shadow @Final private static ResourceLocation LLAMA_SPIT_LOCATION;

        public boolean radiance$supportsEntity(Entity e) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var entity = (LlamaSpit) value;
            pose.pushPose();
            pose.translate(0, .15F, 0);
            pose.mulPose(
                    Axis.YP.rotationDegrees(
                            Mth.lerp(partial, entity.yRotO, entity.getYRot()) - 90));
            pose.mulPose(
                    Axis.ZP.rotationDegrees(Mth.lerp(partial, entity.xRotO, entity.getXRot())));
            model.setupAnim(entity, partial, 0, -.1F, 0, 0);
            var type = model.renderType(LLAMA_SPIT_LOCATION);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(ThrownTridentRenderer.class)
    public abstract static class Tridents implements VanillaEntityReplacement.Source {
        @Shadow @Final private TridentModel model;

        public boolean radiance$supportsEntity(Entity e) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var entity = (ThrownTrident) value;
            pose.pushPose();
            pose.mulPose(
                    Axis.YP.rotationDegrees(
                            Mth.lerp(partial, entity.yRotO, entity.getYRot()) - 90));
            pose.mulPose(
                    Axis.ZP.rotationDegrees(
                            Mth.lerp(partial, entity.xRotO, entity.getXRot()) + 90));
            var type = model.renderType(ThrownTridentRenderer.TRIDENT_LOCATION);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    ItemRenderer.getFoilBufferDirect(buffers, type, false, entity.isFoil()),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(ShulkerBulletRenderer.class)
    public abstract static class Bullets implements VanillaEntityReplacement.Source {
        @Shadow @Final private ShulkerBulletModel<ShulkerBullet> model;
        @Shadow @Final private static ResourceLocation TEXTURE_LOCATION;
        @Shadow @Final private static RenderType RENDER_TYPE;

        public boolean radiance$supportsEntity(Entity e) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var entity = (ShulkerBullet) value;
            pose.pushPose();
            float time = entity.tickCount + partial;
            pose.translate(0, .15F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * .1F) * 180));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(time * .1F) * 180));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * .15F) * 360));
            pose.scale(-.5F, -.5F, .5F);
            model.setupAnim(
                    entity,
                    0,
                    0,
                    0,
                    Mth.rotLerp(partial, entity.yRotO, entity.getYRot()),
                    Mth.lerp(partial, entity.xRotO, entity.getXRot()));
            var type = model.renderType(TEXTURE_LOCATION);
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.scale(1.5F, 1.5F, 1.5F);
            requests.add(
                    VanillaModelPose.extract(model, pose, 654311423),
                    RENDER_TYPE,
                    buffers.getBuffer(RENDER_TYPE),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }

    @Mixin(WitherSkullRenderer.class)
    public abstract static class WitherSkulls implements VanillaEntityReplacement.Source {
        @Shadow @Final private SkullModel model;

        @Shadow
        public abstract ResourceLocation getTextureLocation(WitherSkull entity);

        public boolean radiance$supportsEntity(Entity e) {
            return VanillaModelPose.supports(model);
        }

        public boolean radiance$collectEntity(
                Entity value,
                float yaw,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var entity = (WitherSkull) value;
            pose.pushPose();
            pose.scale(-1, -1, 1);
            model.setupAnim(
                    0,
                    Mth.rotLerp(partial, entity.yRotO, entity.getYRot()),
                    Mth.lerp(partial, entity.xRotO, entity.getXRot()));
            var type = model.renderType(getTextureLocation(entity));
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return true;
        }
    }
}
