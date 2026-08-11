package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BoundPartCapture;
import java.util.List;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Current-version state/appearance adapters. All model requests use one part/layer executor. */
public final class VanillaLayerReplacement {
    private VanillaLayerReplacement() {}

    public record Animation(
            float position, float speed, float partial, float age, float yaw, float pitch) {}

    public interface EnergyAccess {
        EntityModel<?> radiance$energyModel();

        ResourceLocation radiance$energyTexture();

        float radiance$xOffset(float time);
    }

    public interface ArmorAccess {
        HumanoidModel<?> radiance$inner();

        HumanoidModel<?> radiance$outer();

        TextureAtlas radiance$trimAtlas();

        void radiance$visibility(HumanoidModel<?> model, EquipmentSlot slot);

        Model radiance$armorHook(
                LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> model);
    }

    public interface SurfaceAccess {
        EntityModel<?> radiance$surfaceModel();

        default boolean radiance$surfaceSupported() {
            return true;
        }

        List<Surface> radiance$surfaces(
                LivingEntity entity, EntityModel<?> parent, Animation animation, int light);
    }

    public interface DirectLayer {
        default boolean radiance$layerSupported() {
            return true;
        }

        default boolean radiance$layerSupported(LivingEntity entity) {
            return radiance$layerSupported();
        }

        void radiance$collectLayer(
                LivingEntity entity,
                EntityModel<?> parent,
                Animation animation,
                PoseStack pose,
                MultiBufferSource buffers,
                BoundPartCapture requests,
                int light);
    }

    public record Surface(
            EntityModel<?> model,
            RenderType type,
            int color,
            int overlay,
            boolean animate,
            boolean transmission) {
        public Surface(
                EntityModel<?> model, RenderType type, int color, int overlay, boolean animate) {
            this(model, type, color, overlay, animate, false);
        }
    }

    public static List<Surface> cutout(
            EntityModel<?> model,
            ResourceLocation texture,
            LivingEntity entity,
            int color,
            boolean animate) {
        if (entity.isInvisible()) return List.of();
        return List.of(
                new Surface(
                        model,
                        RenderType.entityCutoutNoCull(texture),
                        color,
                        LivingEntityRenderer.getOverlayCoords(entity, 0),
                        animate));
    }

    private static final List<EquipmentSlot> ARMOR_ORDER =
            List.of(
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET,
                    EquipmentSlot.HEAD);

    public static boolean supports(Object layer, LivingEntity entity) {
        if (layer instanceof EyesLayer<?, ?>) return true;
        if (layer instanceof DirectLayer direct) return direct.radiance$layerSupported(entity);
        if (layer.getClass() == ItemInHandLayer.class
                || layer.getClass() == PlayerItemInHandLayer.class)
            return VanillaItemReplacement.supportsWorldStack(entity.getMainHandItem())
                    && VanillaItemReplacement.supportsWorldStack(entity.getOffhandItem());
        if (layer instanceof SurfaceAccess surface)
            return surface.radiance$surfaceSupported()
                    && (surface.radiance$surfaceModel() == null
                            || VanillaModelPose.supports(surface.radiance$surfaceModel()));
        if (layer instanceof EnergyAccess energy)
            return entity instanceof PowerableMob power
                    && (!power.isPowered()
                            || VanillaModelPose.supports(energy.radiance$energyModel()));
        if (layer instanceof ArmorAccess armor) {
            for (var slot : ARMOR_ORDER) {
                var stack = entity.getItemBySlot(slot);
                if (stack.getItem() instanceof ArmorItem item
                        && item.getEquipmentSlot() == slot
                        && IClientItemExtensions.of(stack) != IClientItemExtensions.DEFAULT)
                    return false;
            }
            return VanillaModelPose.supports(armor.radiance$inner())
                    && VanillaModelPose.supports(armor.radiance$outer());
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void collect(
            Object layer,
            LivingEntity entity,
            EntityModel parent,
            Animation animation,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light) {
        if (layer instanceof EyesLayer<?, ?> eyes) {
            add(
                    parent,
                    eyes.renderType(),
                    pose,
                    buffers,
                    requests,
                    15728640,
                    OverlayTexture.NO_OVERLAY,
                    -1);
        } else if (layer.getClass() == ItemInHandLayer.class
                || layer.getClass() == PlayerItemInHandLayer.class) {
            heldItems(entity, parent, pose, buffers, light);
        } else if (layer instanceof DirectLayer direct) {
            direct.radiance$collectLayer(entity, parent, animation, pose, buffers, requests, light);
        } else if (layer instanceof SurfaceAccess surface) {
            for (var request : surface.radiance$surfaces(entity, parent, animation, light)) {
                EntityModel model = request.model() == null ? parent : request.model();
                if (request.animate()) {
                    parent.copyPropertiesTo(model);
                    model.prepareMobModel(
                            entity, animation.position, animation.speed, animation.partial);
                    model.setupAnim(
                            entity,
                            animation.position,
                            animation.speed,
                            animation.age,
                            animation.yaw,
                            animation.pitch);
                }
                try (var material =
                        com.radiance.client.vertex.PBRMaterialContext.pushEntityTransmission(
                                request.transmission())) {
                    add(
                            model,
                            request.type(),
                            pose,
                            buffers,
                            requests,
                            light,
                            request.overlay(),
                            request.color());
                }
            }
        } else if (layer instanceof EnergyAccess energy
                && entity instanceof PowerableMob power
                && power.isPowered()) {
            EntityModel model = energy.radiance$energyModel();
            float time = entity.tickCount + animation.partial;
            model.prepareMobModel(entity, animation.position, animation.speed, animation.partial);
            parent.copyPropertiesTo(model);
            var type =
                    RenderType.energySwirl(
                            energy.radiance$energyTexture(),
                            energy.radiance$xOffset(time) % 1,
                            time * .01F % 1);
            model.setupAnim(
                    entity,
                    animation.position,
                    animation.speed,
                    animation.age,
                    animation.yaw,
                    animation.pitch);
            add(model, type, pose, buffers, requests, light, OverlayTexture.NO_OVERLAY, -8355712);
        } else if (layer instanceof ArmorAccess armor) {
            for (var slot : ARMOR_ORDER) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!(stack.getItem() instanceof ArmorItem item) || item.getEquipmentSlot() != slot)
                    continue;
                HumanoidModel model =
                        slot == EquipmentSlot.LEGS
                                ? armor.radiance$inner()
                                : armor.radiance$outer();
                parent.copyPropertiesTo(model);
                armor.radiance$visibility(model, slot);
                Model actual = armor.radiance$armorHook(entity, stack, slot, model);
                var extension = IClientItemExtensions.of(stack);
                extension.setupModelAnimations(
                        entity,
                        stack,
                        slot,
                        actual,
                        animation.position,
                        animation.speed,
                        animation.partial,
                        animation.age,
                        animation.yaw,
                        animation.pitch);
                boolean inner = slot == EquipmentSlot.LEGS;
                var material = item.getMaterial().value();
                int fallback = extension.getDefaultDyeColor(stack);
                boolean glintMerged = false;
                for (int i = 0; i < material.layers().size(); i++) {
                    var surface = material.layers().get(i);
                    int color =
                            extension.getArmorLayerTintColor(stack, entity, surface, i, fallback);
                    if (color == 0) continue;
                    var texture =
                            net.neoforged.neoforge.client.ClientHooks.getArmorTexture(
                                    entity, stack, surface, inner, slot);
                    var type = RenderType.armorCutoutNoCull(texture);
                    com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(type);
                    if (stack.hasFoil()
                            && !glintMerged
                            && consumer
                                    instanceof com.radiance.client.vertex.PBRVertexConsumer pbr) {
                        consumer =
                                new com.radiance.client.vertex.PBRVertexConsumer.GLint(
                                        pbr, RenderType.armorEntityGlint());
                        glintMerged = true;
                    }
                    requests.add(
                            VanillaModelPose.extract(actual, pose, color),
                            type,
                            consumer,
                            light,
                            OverlayTexture.NO_OVERLAY);
                }
                ArmorTrim trim = stack.get(DataComponents.TRIM);
                if (trim != null) {
                    var sprite =
                            armor.radiance$trimAtlas()
                                    .getSprite(
                                            inner
                                                    ? trim.innerTexture(item.getMaterial())
                                                    : trim.outerTexture(item.getMaterial()));
                    var type = Sheets.armorTrimsSheet(trim.pattern().value().decal());
                    requests.add(
                            VanillaModelPose.extract(actual, pose, -1),
                            type,
                            sprite.wrap(buffers.getBuffer(type)),
                            light,
                            OverlayTexture.NO_OVERLAY);
                }
                if (stack.hasFoil() && !glintMerged)
                    add(
                            actual,
                            RenderType.armorEntityGlint(),
                            pose,
                            buffers,
                            requests,
                            light,
                            OverlayTexture.NO_OVERLAY,
                            -1);
            }
        }
    }

    private static void add(
            Model model,
            RenderType type,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light,
            int overlay,
            int color) {
        requests.add(
                VanillaModelPose.extract(model, pose, color),
                type,
                buffers.getBuffer(type),
                light,
                overlay);
    }

    public static void heldItems(
            LivingEntity entity,
            EntityModel<?> parent,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        var leftItem = right ? entity.getOffhandItem() : entity.getMainHandItem();
        var rightItem = right ? entity.getMainHandItem() : entity.getOffhandItem();
        if (leftItem.isEmpty() && rightItem.isEmpty()) return;
        pose.pushPose();
        if (parent.young) {
            pose.translate(0, .75F, 0);
            pose.scale(.5F, .5F, .5F);
        }
        heldItem(entity, parent, rightItem, HumanoidArm.RIGHT, pose, buffers, light);
        heldItem(entity, parent, leftItem, HumanoidArm.LEFT, pose, buffers, light);
        pose.popPose();
    }

    private static void heldItem(
            LivingEntity entity,
            EntityModel<?> parent,
            ItemStack stack,
            HumanoidArm arm,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        if (stack.isEmpty()) return;
        if (entity instanceof net.minecraft.world.entity.player.Player
                && stack.canPerformAction(
                        net.neoforged.neoforge.common.ItemAbilities.SPYGLASS_SCOPE)
                && entity.getUseItem() == stack
                && entity.swingTime == 0) {
            pose.pushPose();
            var head = ((HeadedModel) parent).getHead();
            float pitch = head.xRot;
            head.xRot =
                    net.minecraft.util.Mth.clamp(
                            head.xRot, (float) (-Math.PI / 6), (float) (Math.PI / 2));
            head.translateAndRotate(pose);
            head.xRot = pitch;
            CustomHeadLayer.translateToHead(pose, false);
            pose.translate((arm == HumanoidArm.LEFT ? -2.5F : 2.5F) / 16, -.0625F, 0);
            livingSpyglass(entity, stack, pose, buffers, light);
            pose.popPose();
            return;
        }
        pose.pushPose();
        ((ArmedModel) parent).translateToHand(arm, pose);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        boolean left = arm == HumanoidArm.LEFT;
        pose.translate((left ? -1F : 1F) / 16, .125F, -.625F);
        var context =
                left
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        VanillaItemReplacement.livingItem(
                entity,
                stack,
                context,
                left,
                pose,
                buffers,
                light,
                OverlayTexture.NO_OVERLAY,
                entity.getId() + context.ordinal());
        pose.popPose();
    }

    private static void livingSpyglass(
            LivingEntity entity,
            ItemStack stack,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        var context = ItemDisplayContext.HEAD;
        VanillaItemReplacement.livingItem(
                entity,
                stack,
                context,
                false,
                pose,
                buffers,
                light,
                OverlayTexture.NO_OVERLAY,
                entity.getId() + context.ordinal());
    }
}
