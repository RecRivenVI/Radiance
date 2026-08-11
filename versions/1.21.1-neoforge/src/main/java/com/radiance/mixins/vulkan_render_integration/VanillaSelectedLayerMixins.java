package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.adapter.vanilla.*;
import com.radiance.adapter.vanilla.VanillaLayerReplacement.*;
import com.radiance.client.vertex.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection.Hat;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Selection state is sampled into part requests before the original reset point. */
public final class VanillaSelectedLayerMixins {
    private VanillaSelectedLayerMixins() {}

    @Mixin(targets = "net.minecraft.client.renderer.entity.IllusionerRenderer$1")
    public abstract static class IllusionHands implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem())
                    && VanillaItemReplacement.supportsWorldStack(entity.getOffhandItem());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var illusioner = (net.minecraft.world.entity.monster.Illusioner) entity;
            if (illusioner.isCastingSpell() || illusioner.isAggressive())
                VanillaLayerReplacement.heldItems(entity, parent, pose, buffers, light);
        }
    }

    @Mixin({ArrowLayer.class, BeeStingerLayer.class})
    public abstract static class StuckItems implements DirectLayer {
        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            VanillaStuckItemRequests.collect(
                    (Object) this instanceof ArrowLayer<?, ?>,
                    entity,
                    (PlayerModel<?>) parent,
                    a.partial(),
                    pose,
                    buffers,
                    requests,
                    light);
        }
    }

    @Mixin(SpinAttackEffectLayer.class)
    public abstract static class SpinAttack implements DirectLayer {
        @Shadow @Final private ModelPart box;

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            if (!entity.isAutoSpinAttack()) return;
            var type = RenderType.entityCutoutNoCull(SpinAttackEffectLayer.TEXTURE);
            var out = buffers.getBuffer(type);
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(a.age() * -(45 + i * 5)));
                float scale = .75F * i;
                pose.scale(scale, scale, scale);
                pose.translate(0, -.2F + .6F * i, 0);
                requests.add(
                        VanillaModelPose.extract(box, pose, -1),
                        type,
                        out,
                        light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
    }

    @Mixin(Deadmau5EarsLayer.class)
    public abstract static class Ears implements DirectLayer {
        public void radiance$collectLayer(
                LivingEntity value,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var player = (net.minecraft.client.player.AbstractClientPlayer) value;
            if (!"deadmau5".equals(player.getName().getString()) || player.isInvisible()) return;
            var type = RenderType.entitySolid(player.getSkin().texture());
            var out = buffers.getBuffer(type);
            var model = (PlayerModel<?>) parent;
            var ear = ((VanillaModelPose.PlayerExtras) model).radiance$ear();
            for (int i = 0; i < 2; i++) {
                float
                        yaw =
                                Mth.lerp(a.partial(), player.yRotO, player.getYRot())
                                        - Mth.lerp(a.partial(), player.yBodyRotO, player.yBodyRot),
                        pitch = Mth.lerp(a.partial(), player.xRotO, player.getXRot());
                pose.pushPose();
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                pose.translate(.375F * (i * 2 - 1), 0, 0);
                pose.translate(0, -.375F, 0);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
                pose.scale(1.3333334F, 1.3333334F, 1.3333334F);
                ear.copyFrom(model.head);
                ear.x = 0;
                ear.y = 0;
                requests.add(
                        VanillaModelPose.extract(ear, pose, -1),
                        type,
                        out,
                        light,
                        LivingEntityRenderer.getOverlayCoords(value, 0));
                pose.popPose();
            }
        }
    }

    @Mixin(CapeLayer.class)
    public abstract static class Cape implements DirectLayer {
        public void radiance$collectLayer(
                LivingEntity value,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var player = (net.minecraft.client.player.AbstractClientPlayer) value;
            if (player.isInvisible()
                    || !player.isModelPartShown(
                            net.minecraft.world.entity.player.PlayerModelPart.CAPE)) return;
            var texture = player.getSkin().capeTexture();
            if (texture == null
                    || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                            .is(net.minecraft.world.item.Items.ELYTRA)) return;
            pose.pushPose();
            pose.translate(0, 0, .125F);
            double x =
                    Mth.lerp((double) a.partial(), player.xCloakO, player.xCloak)
                            - Mth.lerp((double) a.partial(), player.xo, player.getX());
            double y =
                    Mth.lerp((double) a.partial(), player.yCloakO, player.yCloak)
                            - Mth.lerp((double) a.partial(), player.yo, player.getY());
            double z =
                    Mth.lerp((double) a.partial(), player.zCloakO, player.zCloak)
                            - Mth.lerp((double) a.partial(), player.zo, player.getZ());
            float yaw = Mth.rotLerp(a.partial(), player.yBodyRotO, player.yBodyRot);
            double sin = Mth.sin(yaw * (float) (Math.PI / 180)),
                    cos = -Mth.cos(yaw * (float) (Math.PI / 180));
            float vertical = Mth.clamp((float) y * 10, -6, 32),
                    forward = Mth.clamp((float) (x * sin + z * cos) * 100, 0, 150),
                    side = Mth.clamp((float) (x * cos - z * sin) * 100, -20, 20);
            if (forward < 0) forward = 0;
            float bob = Mth.lerp(a.partial(), player.oBob, player.bob);
            vertical +=
                    Mth.sin(Mth.lerp(a.partial(), player.walkDistO, player.walkDist) * 6)
                            * 32
                            * bob;
            if (player.isCrouching()) vertical += 25;
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6 + forward / 2 + vertical));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side / 2));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - side / 2));
            var type = RenderType.entitySolid(texture);
            requests.add(
                    VanillaModelPose.extract(
                            ((VanillaModelPose.PlayerExtras) parent).radiance$cloak(), pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Mixin(ParrotOnShoulderLayer.class)
    public abstract static class ShoulderParrots implements DirectLayer {
        @Shadow @Final private ParrotModel model;

        public boolean radiance$layerSupported() {
            return VanillaModelPose.supports(model);
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var player = (net.minecraft.world.entity.player.Player) entity;
            for (int i = 0; i < 2; i++) {
                var tag = i == 0 ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
                if (net.minecraft.world.entity.EntityType.byString(tag.getString("id")).orElse(null)
                        != net.minecraft.world.entity.EntityType.PARROT) continue;
                pose.pushPose();
                pose.translate(i == 0 ? .4F : -.4F, player.isCrouching() ? -1.3F : -1.5F, 0);
                var variant =
                        net.minecraft.world.entity.animal.Parrot.Variant.byId(
                                tag.getInt("Variant"));
                var type =
                        model.renderType(
                                net.minecraft.client.renderer.entity.ParrotRenderer
                                        .getVariantTexture(variant));
                ((VanillaModelPose.ShoulderPose) model)
                        .radiance$shoulder(
                                a.position(), a.speed(), a.yaw(), a.pitch(), entity.tickCount);
                requests.add(
                        VanillaModelPose.extract(model, pose, -1),
                        type,
                        buffers.getBuffer(type),
                        light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
    }

    @Mixin(FoxHeldItemLayer.class)
    public abstract static class FoxItem implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var fox = (net.minecraft.world.entity.animal.Fox) entity;
            boolean asleep = fox.isSleeping(), young = fox.isBaby();
            pose.pushPose();
            if (young) {
                pose.scale(.75F, .75F, .75F);
                pose.translate(0, .5F, .209375F);
            }
            var head = ((FoxModel<?>) parent).head;
            pose.translate(head.x / 16, head.y / 16, head.z / 16);
            pose.mulPose(com.mojang.math.Axis.ZP.rotation(fox.getHeadRollAngle(a.partial())));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(a.yaw()));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(a.pitch()));
            if (young) pose.translate(asleep ? .4F : .06F, .26F, asleep ? .15F : -.5F);
            else pose.translate(asleep ? .46F : .06F, asleep ? .26F : .27F, asleep ? .22F : -.5F);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            if (asleep) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
            var context = net.minecraft.world.item.ItemDisplayContext.GROUND;
            VanillaItemReplacement.livingItem(
                    entity,
                    entity.getMainHandItem(),
                    context,
                    false,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    entity.getId() + context.ordinal());
            pose.popPose();
        }
    }

    @Mixin(DolphinCarryingItemLayer.class)
    public abstract static class DolphinItem implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem())
                    && VanillaItemReplacement.supportsWorldStack(entity.getOffhandItem());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            pose.pushPose();
            float amount = Mth.abs(entity.getXRot()) / 60;
            if (entity.getXRot() < 0) pose.translate(0, 1 - amount * .5F, -1 + amount * .5F);
            else pose.translate(0, 1 + amount * .8F, -1 + amount * .2F);
            var item =
                    entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                            ? entity.getMainHandItem()
                            : entity.getOffhandItem();
            var context = net.minecraft.world.item.ItemDisplayContext.GROUND;
            VanillaItemReplacement.livingItem(
                    entity,
                    item,
                    context,
                    false,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    entity.getId() + context.ordinal());
            pose.popPose();
        }
    }

    @Mixin(PandaHoldsItemLayer.class)
    public abstract static class PandaItem implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var panda = (net.minecraft.world.entity.animal.Panda) entity;
            if (!panda.isSitting() || panda.isScared()) return;
            float z = -.6F, y = 1.4F;
            if (panda.isEating()) {
                z -= .2F * Mth.sin(a.age() * .6F) + .2F;
                y -= .09F * Mth.sin(a.age() * .6F);
            }
            pose.pushPose();
            pose.translate(.1F, y, z);
            var context = net.minecraft.world.item.ItemDisplayContext.GROUND;
            VanillaItemReplacement.livingItem(
                    entity,
                    entity.getMainHandItem(),
                    context,
                    false,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    entity.getId() + context.ordinal());
            pose.popPose();
        }
    }

    @Mixin(CarriedBlockLayer.class)
    public abstract static class CarriedBlock implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            var state = ((net.minecraft.world.entity.monster.EnderMan) entity).getCarriedBlock();
            return state == null || VanillaBlockModelRequests.supports(state);
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var state = ((net.minecraft.world.entity.monster.EnderMan) entity).getCarriedBlock();
            if (state == null) return;
            pose.pushPose();
            pose.translate(0, .6875F, -.75F);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));
            pose.translate(.25F, .1875F, .25F);
            pose.scale(-.5F, -.5F, .5F);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            VanillaBlockModelRequests.add(
                    state,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Mixin(SnowGolemHeadLayer.class)
    public abstract static class Pumpkin implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN));
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var snow = (net.minecraft.world.entity.animal.SnowGolem) entity;
            if (!snow.hasPumpkin()) return;
            boolean outline =
                    net.minecraft.client.Minecraft.getInstance().shouldEntityAppearGlowing(entity)
                            && entity.isInvisible();
            if (entity.isInvisible() && !outline) return;
            pose.pushPose();
            ((SnowGolemModel<?>) parent).getHead().translateAndRotate(pose);
            pose.translate(0, -.34375F, 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            pose.scale(.625F, -.625F, -.625F);
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
            if (outline) {
                pose.translate(-.5F, -.5F, -.5F);
                VanillaBlockModelRequests.outline(
                        net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN.defaultBlockState(),
                        pose,
                        buffers,
                        light,
                        overlay);
            } else
                VanillaItemReplacement.livingItem(
                        entity,
                        new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN),
                        net.minecraft.world.item.ItemDisplayContext.HEAD,
                        false,
                        pose,
                        buffers,
                        light,
                        overlay,
                        entity.getId());
            pose.popPose();
        }
    }

    @Mixin(MushroomCowMushroomLayer.class)
    public abstract static class Mushrooms implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaBlockModelRequests.supports(
                    ((net.minecraft.world.entity.animal.MushroomCow) entity)
                            .getVariant()
                            .getBlockState());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation a,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var cow = (net.minecraft.world.entity.animal.MushroomCow) entity;
            if (cow.isBaby()) return;
            boolean outline =
                    net.minecraft.client.Minecraft.getInstance().shouldEntityAppearGlowing(entity)
                            && entity.isInvisible();
            if (entity.isInvisible() && !outline) return;
            var state = cow.getVariant().getBlockState();
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                if (i < 2) {
                    pose.translate(.2F, -.35F, .5F);
                    if (i == 1) {
                        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(42));
                        pose.translate(.1F, 0, -.6F);
                    }
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-48));
                } else {
                    ((CowModel<?>) parent).getHead().translateAndRotate(pose);
                    pose.translate(0, -.7F, -.2F);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-78));
                }
                pose.scale(-1, -1, 1);
                pose.translate(-.5F, -.5F, -.5F);
                if (outline)
                    VanillaBlockModelRequests.outline(state, pose, buffers, light, overlay);
                else VanillaBlockModelRequests.add(state, pose, buffers, light, overlay);
                pose.popPose();
            }
        }
    }

    @Mixin(CustomHeadLayer.class)
    public abstract static class HeadItems implements DirectLayer {
        @Shadow @Final private float scaleX;
        @Shadow @Final private float scaleY;
        @Shadow @Final private float scaleZ;

        @Shadow @Final
        private java.util.Map<net.minecraft.world.level.block.SkullBlock.Type, SkullModelBase>
                skullModels;

        public boolean radiance$layerSupported(LivingEntity entity) {
            var stack = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (stack.isEmpty()
                    || stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor
                            && armor.getEquipmentSlot()
                                    == net.minecraft.world.entity.EquipmentSlot.HEAD) return true;
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem block
                    && block.getBlock()
                            instanceof net.minecraft.world.level.block.AbstractSkullBlock skull)
                return VanillaModelPose.supports(skullModels.get(skull.getType()));
            return VanillaItemReplacement.supportsWorldStack(stack);
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var stack = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (stack.isEmpty()) return;
            pose.pushPose();
            pose.scale(scaleX, scaleY, scaleZ);
            boolean villager =
                    entity instanceof net.minecraft.world.entity.npc.Villager
                            || entity instanceof net.minecraft.world.entity.monster.ZombieVillager;
            if (entity.isBaby() && !(entity instanceof net.minecraft.world.entity.npc.Villager)) {
                pose.translate(0, .03125F, 0);
                pose.scale(.7F, .7F, .7F);
                pose.translate(0, 1, 0);
            }
            ((HeadedModel) parent).getHead().translateAndRotate(pose);
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem block
                    && block.getBlock()
                            instanceof net.minecraft.world.level.block.AbstractSkullBlock skull) {
                pose.scale(1.1875F, -1.1875F, -1.1875F);
                if (villager) pose.translate(0, .0625F, 0);
                pose.translate(-.5, 0, -.5);
                var walk =
                        entity.getVehicle() instanceof LivingEntity vehicle
                                ? vehicle.walkAnimation
                                : entity.walkAnimation;
                VanillaSkullRequests.add(
                        null,
                        180,
                        walk.position(animation.partial()),
                        pose,
                        buffers,
                        requests,
                        light,
                        skullModels.get(skull.getType()),
                        net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(
                                skull.getType(),
                                stack.get(net.minecraft.core.component.DataComponents.PROFILE)));
            } else if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor)
                    || armor.getEquipmentSlot() != net.minecraft.world.entity.EquipmentSlot.HEAD) {
                CustomHeadLayer.translateToHead(pose, villager);
                var context = net.minecraft.world.item.ItemDisplayContext.HEAD;
                VanillaItemReplacement.livingItem(
                        entity,
                        stack,
                        context,
                        false,
                        pose,
                        buffers,
                        light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        entity.getId() + context.ordinal());
            }
            pose.popPose();
        }
    }

    @Mixin(VillagerProfessionLayer.class)
    public abstract static class Profession implements DirectLayer {
        @Shadow @Final private static Int2ObjectMap<ResourceLocation> LEVEL_LOCATIONS;
        @Shadow @Final private Object2ObjectMap<VillagerType, Hat> typeHatCache;
        @Shadow @Final private Object2ObjectMap<VillagerProfession, Hat> professionHatCache;

        @Shadow
        public abstract <K> Hat getHatData(
                Object2ObjectMap<K, Hat> cache,
                String category,
                DefaultedRegistry<K> registry,
                K key);

        @Invoker("getResourceLocation")
        protected abstract ResourceLocation radiance$resource(
                String category, ResourceLocation key);

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            if (entity.isInvisible()) return;
            var data = ((VillagerDataHolder) entity).getVillagerData();
            var type = data.getType();
            var profession = data.getProfession();
            var typeHat = getHatData(typeHatCache, "type", BuiltInRegistries.VILLAGER_TYPE, type);
            var jobHat =
                    getHatData(
                            professionHatCache,
                            "profession",
                            BuiltInRegistries.VILLAGER_PROFESSION,
                            profession);
            var head = (VillagerHeadModel) parent;
            head.hatVisible(jobHat == Hat.NONE || jobHat == Hat.PARTIAL && typeHat != Hat.FULL);
            var renderType =
                    RenderType.entityCutoutNoCull(
                            radiance$resource(
                                    "type", BuiltInRegistries.VILLAGER_TYPE.getKey(type)));
            requests.add(
                    VanillaModelPose.extract(parent, pose, -1),
                    renderType,
                    buffers.getBuffer(renderType),
                    light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0));
            head.hatVisible(true);
            if (profession != VillagerProfession.NONE && !entity.isBaby()) {
                renderType =
                        RenderType.entityCutoutNoCull(
                                radiance$resource(
                                        "profession",
                                        BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession)));
                requests.add(
                        VanillaModelPose.extract(parent, pose, -1),
                        renderType,
                        buffers.getBuffer(renderType),
                        light,
                        LivingEntityRenderer.getOverlayCoords(entity, 0));
                if (profession != VillagerProfession.NITWIT) {
                    renderType =
                            RenderType.entityCutoutNoCull(
                                    radiance$resource(
                                            "profession_level",
                                            LEVEL_LOCATIONS.get(
                                                    Mth.clamp(
                                                            data.getLevel(),
                                                            1,
                                                            LEVEL_LOCATIONS.size()))));
                    requests.add(
                            VanillaModelPose.extract(parent, pose, -1),
                            renderType,
                            buffers.getBuffer(renderType),
                            light,
                            LivingEntityRenderer.getOverlayCoords(entity, 0));
                }
            }
        }
    }

    @Mixin(WardenEmissiveLayer.class)
    public abstract static class WardenEmission implements DirectLayer {
        @Shadow @Final private ResourceLocation texture;
        @Shadow @Final private WardenEmissiveLayer.AlphaFunction<Warden> alphaFunction;

        @Invoker("onlyDrawSelectedParts")
        protected abstract void radiance$select();

        @Invoker("resetDrawForAllParts")
        protected abstract void radiance$reset();

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            if (entity.isInvisible()) return;
            radiance$select();
            var type = RenderType.entityTranslucentEmissive(texture);
            int color =
                    FastColor.ARGB32.color(
                            Mth.floor(
                                    alphaFunction.apply(
                                                    (Warden) entity,
                                                    animation.partial(),
                                                    animation.age())
                                            * 255),
                            255,
                            255,
                            255);
            requests.add(
                    VanillaModelPose.extract(parent, pose, color),
                    type,
                    buffers.getBuffer(type),
                    light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0));
            radiance$reset();
        }
    }

    @Mixin(ElytraLayer.class)
    public abstract static class Elytra implements DirectLayer {
        @Shadow @Final private ElytraModel<LivingEntity> elytraModel;

        @Shadow
        public abstract boolean shouldRender(
                net.minecraft.world.item.ItemStack stack, LivingEntity entity);

        @Shadow
        public abstract ResourceLocation getElytraTexture(
                net.minecraft.world.item.ItemStack stack, LivingEntity entity);

        public boolean radiance$layerSupported() {
            return VanillaModelPose.supports(elytraModel);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var stack = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (!shouldRender(stack, entity)) return;
            var texture = getElytraTexture(stack, entity);
            if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
                var skin = player.getSkin();
                if (skin.elytraTexture() != null) texture = skin.elytraTexture();
                else if (skin.capeTexture() != null
                        && player.isModelPartShown(
                                net.minecraft.world.entity.player.PlayerModelPart.CAPE))
                    texture = skin.capeTexture();
            }
            pose.pushPose();
            pose.translate(0, 0, .125F);
            ((EntityModel) parent).copyPropertiesTo(elytraModel);
            elytraModel.setupAnim(
                    entity,
                    animation.position(),
                    animation.speed(),
                    animation.age(),
                    animation.yaw(),
                    animation.pitch());
            var type = RenderType.armorCutoutNoCull(texture);
            var consumer =
                    net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(
                            buffers, type, stack.hasFoil());
            requests.add(
                    VanillaModelPose.extract(elytraModel, pose, -1),
                    type,
                    consumer,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Mixin(ShulkerHeadLayer.class)
    public abstract static class ShulkerHead implements DirectLayer {
        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var type =
                    RenderType.entitySolid(
                            net.minecraft.client.renderer.entity.ShulkerRenderer.getTextureLocation(
                                    ((net.minecraft.world.entity.monster.Shulker) entity)
                                            .getColor()));
            requests.add(
                    VanillaModelPose.extract(((ShulkerModel<?>) parent).getHead(), pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0));
        }
    }

    @Mixin(BreezeEyesLayer.class)
    public abstract static class BreezeEyes implements DirectLayer {
        @Shadow @Final private static RenderType BREEZE_EYES;

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var model = (BreezeModel) parent;
            net.minecraft.client.renderer.entity.BreezeRenderer.enable(
                    model, model.head(), model.eyes());
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    BREEZE_EYES,
                    buffers.getBuffer(BREEZE_EYES),
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    @Mixin(BreezeWindLayer.class)
    public abstract static class BreezeWind implements DirectLayer {
        @Shadow @Final private BreezeModel<net.minecraft.world.entity.monster.breeze.Breeze> model;
        @Shadow @Final private static ResourceLocation TEXTURE_LOCATION;

        @Invoker("xOffset")
        protected abstract float radiance$xOffset(float time);

        public boolean radiance$layerSupported() {
            return VanillaModelPose.supports(model);
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var type =
                    RenderType.breezeWind(
                            TEXTURE_LOCATION,
                            radiance$xOffset(entity.tickCount + animation.partial()) % 1,
                            0);
            model.setupAnim(
                    (net.minecraft.world.entity.monster.breeze.Breeze) entity,
                    animation.position(),
                    animation.speed(),
                    animation.age(),
                    animation.yaw(),
                    animation.pitch());
            net.minecraft.client.renderer.entity.BreezeRenderer.enable(model, model.wind());
            requests.add(
                    VanillaModelPose.extract(model, pose, -1),
                    type,
                    buffers.getBuffer(type),
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    @Mixin(CrossedArmsItemLayer.class)
    public abstract static class CrossedArms implements DirectLayer {
        public boolean radiance$layerSupported(LivingEntity entity) {
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem());
        }

        public void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light) {
            var stack = entity.getMainHandItem();
            pose.pushPose();
            if ((Object) this instanceof WitchItemLayer<?>
                    && stack.is(net.minecraft.world.item.Items.POTION)) {
                var model = (WitchModel<?>) parent;
                model.getHead().translateAndRotate(pose);
                model.getNose().translateAndRotate(pose);
                pose.translate(.0625F, .25F, 0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(140));
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(10));
                pose.translate(0, -.4F, .4F);
            }
            pose.translate(0, .4F, -.4F);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
            var context = net.minecraft.world.item.ItemDisplayContext.GROUND;
            VanillaItemReplacement.livingItem(
                    entity,
                    stack,
                    context,
                    false,
                    pose,
                    buffers,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    entity.getId() + context.ordinal());
            pose.popPose();
        }
    }
}
