package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.PartModelCapture;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.SourceCompatibility;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Vanilla renderer state extraction, followed by a source-declared part request. */
public final class VanillaLivingReplacement {
    private VanillaLivingReplacement() {}

    public interface StateAccess {
        void radiance$selectModel(EntityModel<?> model);

        float radiance$attack(LivingEntity entity, float partialTick);

        float radiance$bob(LivingEntity entity, float partialTick);

        void radiance$rotations(
                LivingEntity entity,
                PoseStack pose,
                float age,
                float bodyYaw,
                float partialTick,
                float scale);

        void radiance$scale(LivingEntity entity, PoseStack pose, float partialTick);

        boolean radiance$bodyVisible(LivingEntity entity);

        float radiance$whiteOverlay(LivingEntity entity, float partialTick);

        RenderType radiance$renderType(
                LivingEntity entity, boolean visible, boolean translucent, boolean glowing);
    }

    public interface NameAccess {
        void radiance$shadowRadius(float value);

        void radiance$shadowStrength(float value);

        boolean radiance$showName(Entity entity);

        void radiance$name(
                Entity entity,
                net.minecraft.network.chat.Component content,
                PoseStack pose,
                MultiBufferSource buffers,
                int light,
                float partialTick);
    }

    public interface PlayerPoseAccess {
        void radiance$preparePlayer(net.minecraft.client.player.AbstractClientPlayer player);
    }

    public interface ModelSelection {
        EntityModel<?> radiance$selectedModel(LivingEntity entity);

        void radiance$prepareSelected(LivingEntity entity);

        void radiance$finishSelected();
    }

    private static final ClassValue<Boolean> BASE_RENDER =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    for (var current = type; current != null; current = current.getSuperclass()) {
                        for (var method : current.getDeclaredMethods()) {
                            var parameters = method.getParameterTypes();
                            if (method.getName().equals("render")
                                    && !method.isBridge()
                                    && parameters.length == 6
                                    && parameters[3] == PoseStack.class
                                    && parameters[4] == MultiBufferSource.class)
                                return current == LivingEntityRenderer.class
                                        || current == EndermanRenderer.class
                                        || current == WolfRenderer.class
                                        || current == SlimeRenderer.class
                                        || current == MagmaCubeRenderer.class
                                        || current
                                                == net.minecraft.client.renderer.entity.player
                                                        .PlayerRenderer.class
                                        || current == BreezeRenderer.class
                                        || current == WitchRenderer.class
                                        || current == GuardianRenderer.class
                                        || current == IllusionerRenderer.class
                                        || ModelSelection.class.isAssignableFrom(current);
                        }
                    }
                    return false;
                }
            };

    private static boolean inactive(Object layer, LivingEntity entity) {
        if (layer instanceof ItemInHandLayer<?, ?>)
            return entity.getMainHandItem().isEmpty() && entity.getOffhandItem().isEmpty();
        if (layer instanceof CrossedArmsItemLayer<?, ?>
                || layer instanceof DolphinCarryingItemLayer
                || layer instanceof FoxHeldItemLayer
                || layer instanceof PandaHoldsItemLayer) return entity.getMainHandItem().isEmpty();
        if (layer instanceof CustomHeadLayer<?, ?>) {
            var stack = entity.getItemBySlot(EquipmentSlot.HEAD);
            return stack.isEmpty()
                    || stack.getItem() instanceof ArmorItem armor
                            && armor.getEquipmentSlot() == EquipmentSlot.HEAD;
        }
        if (layer instanceof ArrowLayer<?, ?>) return entity.getArrowCount() == 0;
        if (layer instanceof BeeStingerLayer<?, ?>) return entity.getStingerCount() == 0;
        if (layer instanceof SpinAttackEffectLayer<?>) return !entity.isAutoSpinAttack();
        if (layer instanceof Deadmau5EarsLayer)
            return !"deadmau5".equals(entity.getName().getString()) || entity.isInvisible();
        if (layer instanceof ParrotOnShoulderLayer<?>) {
            var player = (net.minecraft.world.entity.player.Player) entity;
            return player.getShoulderEntityLeft().isEmpty()
                    && player.getShoulderEntityRight().isEmpty();
        }
        if (layer instanceof CapeLayer) {
            var player = (net.minecraft.client.player.AbstractClientPlayer) entity;
            return player.isInvisible()
                    || !player.isModelPartShown(
                            net.minecraft.world.entity.player.PlayerModelPart.CAPE)
                    || player.getSkin().capeTexture() == null
                    || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        }
        if (layer instanceof ElytraLayer<?, ?>)
            return !entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        if (layer instanceof CarriedBlockLayer)
            return ((net.minecraft.world.entity.monster.EnderMan) entity).getCarriedBlock() == null;
        if (layer instanceof IronGolemFlowerLayer)
            return ((net.minecraft.world.entity.animal.IronGolem) entity).getOfferFlowerTick() == 0;
        if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
            for (var slot :
                    List.of(
                            EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST,
                            EquipmentSlot.LEGS,
                            EquipmentSlot.FEET))
                if (entity.getItemBySlot(slot).getItem() instanceof ArmorItem armor
                        && armor.getEquipmentSlot() == slot) return false;
            return true;
        }
        return false;
    }

    public static boolean eligible(
            EntityRenderDispatcher dispatcher,
            Entity value,
            MultiBufferSource output,
            StorageVertexConsumerProvider storage) {
        if (!PartModelCapture.ENABLED
                || output != storage
                        && !(output
                                instanceof
                                com.radiance.client.vertex.StorageOutlineVertexConsumerProvider)
                || com.radiance.client.render.RasterPreviewScope.active()
                || !(value instanceof LivingEntity entity)
                || !(dispatcher.getRenderer(value) instanceof LivingEntityRenderer<?, ?> renderer)
                || !BASE_RENDER.get(renderer.getClass())
                || !SourceCompatibility.producer(renderer.getClass())
                || !PartModelCapture.sourceCompatible()
                || !SourceCompatibility.compatible(SourceCompatibility.Source.STATIC_STATE)
                || !VanillaModelPose.supports(
                        renderer instanceof ModelSelection selected
                                ? selected.radiance$selectedModel(entity)
                                : renderer.getModel())) return false;
        var layers =
                ((com.radiance.mixins.vulkan_render_integration.accessor
                                        .LivingRendererLayersAccessor)
                                renderer)
                        .radiance$getLayers();
        for (var layer : layers)
            if (!SourceCompatibility.producer(layer.getClass())
                    || (!VanillaLayerReplacement.supports(layer, entity)
                            && !inactive(layer, entity))) return false;
        return true;
    }

    /**
     * The arithmetic/order follows LivingEntityRenderer; no renderer or layer render is invoked.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            EntityRenderDispatcher dispatcher,
            LivingEntity entity,
            float partialTick,
            PoseStack pose,
            StorageVertexConsumerProvider storage,
            int light) {
        render(dispatcher, entity, partialTick, pose, storage, storage, light);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            EntityRenderDispatcher dispatcher,
            LivingEntity entity,
            float partialTick,
            PoseStack pose,
            StorageVertexConsumerProvider storage,
            MultiBufferSource output,
            int light) {
        if (entity instanceof net.minecraft.world.entity.monster.Illusioner illusioner
                && illusioner.isInvisible()) {
            float age =
                    ((StateAccess) dispatcher.getRenderer(entity))
                            .radiance$bob(entity, partialTick);
            var offsets = illusioner.getIllusionOffsets(partialTick);
            for (int i = 0; i < offsets.length; i++) {
                pose.pushPose();
                pose.translate(
                        offsets[i].x + Mth.cos(i + age * .5F) * .025,
                        offsets[i].y + Mth.cos(i + age * .75F) * .0125,
                        offsets[i].z + Mth.cos(i + age * .7F) * .025);
                renderOne(dispatcher, entity, partialTick, pose, storage, output, light);
                pose.popPose();
            }
        } else renderOne(dispatcher, entity, partialTick, pose, storage, output, light);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderOne(
            EntityRenderDispatcher dispatcher,
            LivingEntity entity,
            float partialTick,
            PoseStack pose,
            StorageVertexConsumerProvider storage,
            MultiBufferSource output,
            int light) {
        LivingEntityRenderer renderer = (LivingEntityRenderer) dispatcher.getRenderer(entity);
        if (renderer instanceof ModelSelection selected) selected.radiance$prepareSelected(entity);
        EntityModel model = renderer.getModel();
        if (model instanceof net.minecraft.client.model.WitchModel witch)
            witch.setHoldingItem(!entity.getMainHandItem().isEmpty());
        if (entity instanceof net.minecraft.world.entity.monster.Slime slime)
            ((NameAccess) renderer).radiance$shadowRadius(.25F * slime.getSize());
        if (model instanceof net.minecraft.client.model.BreezeModel breeze)
            BreezeRenderer.enable(breeze, breeze.head(), breeze.rods());
        if (entity instanceof net.minecraft.world.entity.monster.EnderMan enderman) {
            var endermanModel = (net.minecraft.client.model.EndermanModel) model;
            endermanModel.carrying = enderman.getCarriedBlock() != null;
            endermanModel.creepy = enderman.isCreepy();
        }
        if (entity instanceof net.minecraft.world.entity.animal.Wolf wolf && wolf.isWet()) {
            float shade = wolf.getWetShade(partialTick);
            ((net.minecraft.client.model.WolfModel) model)
                    .setColor(
                            net.minecraft.util.FastColor.ARGB32.colorFromFloat(
                                    1, shade, shade, shade));
        }
        var parts = new com.radiance.client.vertex.BoundPartCapture(storage);
        StateAccess state = (StateAccess) renderer;
        var offset = renderer.getRenderOffset(entity, partialTick);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        try (var evaluation =
                VanillaModelPose.source(
                        storage.sourceOwner != null ? storage.sourceOwner : entity)) {
            if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                    && renderer instanceof PlayerPoseAccess playerPose) {
                playerPose.radiance$preparePlayer(player);
                if (NeoForge.EVENT_BUS
                        .post(
                                new net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre(
                                        player,
                                        (net.minecraft.client.renderer.entity.player.PlayerRenderer)
                                                renderer,
                                        partialTick,
                                        pose,
                                        output,
                                        light))
                        .isCanceled()) return;
            }
            var pre = new RenderLivingEvent.Pre(entity, renderer, partialTick, pose, output, light);
            if (NeoForge.EVENT_BUS.post(pre).isCanceled()) {
                playerPost(entity, renderer, partialTick, pose, output, light);
                return;
            }
            model = renderer.getModel();
            pose.pushPose();
            model.attackTime = state.radiance$attack(entity, partialTick);
            boolean sit =
                    entity.isPassenger()
                            && entity.getVehicle() != null
                            && entity.getVehicle().shouldRiderSit();
            model.riding = sit;
            model.young = entity.isBaby();
            float body = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            float head = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
            float relative = head - body;
            if (sit && entity.getVehicle() instanceof LivingEntity vehicle) {
                body = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
                relative = head - body;
                float limited = Mth.wrapDegrees(relative);
                if (limited < -85) limited = -85;
                if (limited >= 85) limited = 85;
                body = head - limited;
                if (limited * limited > 2500) body += limited * .2F;
                relative = head - body;
            }
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                pitch *= -1;
                relative *= -1;
            }
            relative = Mth.wrapDegrees(relative);
            if (entity.hasPose(Pose.SLEEPING)) {
                com.radiance.api.render.SourceHooks.sleepingPose(entity, pose);
                Direction direction = entity.getBedOrientation();
                if (direction != null) {
                    float eye = entity.getEyeHeight(Pose.STANDING) - .1F;
                    pose.translate(-direction.getStepX() * eye, 0, -direction.getStepZ() * eye);
                }
            }
            float scale = entity.getScale();
            pose.scale(scale, scale, scale);
            float age = state.radiance$bob(entity, partialTick);
            state.radiance$rotations(entity, pose, age, body, partialTick, scale);
            pose.scale(-1, -1, 1);
            state.radiance$scale(entity, pose, partialTick);
            pose.translate(0, -1.501F, 0);
            float speed = 0, position = 0;
            if (!sit && entity.isAlive()) {
                speed = entity.walkAnimation.speed(partialTick);
                position = entity.walkAnimation.position(partialTick);
                if (entity.isBaby()) position *= 3;
                if (speed > 1) speed = 1;
            }
            model.prepareMobModel(entity, position, speed, partialTick);
            model.setupAnim(entity, position, speed, age, relative, pitch);
            boolean visible = state.radiance$bodyVisible(entity);
            boolean translucent = !visible && !entity.isInvisibleTo(Minecraft.getInstance().player);
            RenderType type =
                    state.radiance$renderType(
                            entity,
                            visible,
                            translucent,
                            Minecraft.getInstance().shouldEntityAppearGlowing(entity));
            var layers =
                    ((com.radiance.mixins.vulkan_render_integration.accessor
                                            .LivingRendererLayersAccessor)
                                    renderer)
                            .radiance$getLayers();
            boolean direct = VanillaModelPose.supports(model);
            for (var layer : layers)
                direct &=
                        SourceCompatibility.producer(layer.getClass())
                                && (VanillaLayerReplacement.supports(layer, entity)
                                        || inactive(layer, entity));
            if (type != null) {
                int overlay =
                        LivingEntityRenderer.getOverlayCoords(
                                entity, state.radiance$whiteOverlay(entity, partialTick));
                var consumer = output.getBuffer(type);
                int color = translucent ? 654311423 : -1;
                if (direct)
                    parts.add(
                            VanillaModelPose.extract(model, pose, color),
                            type,
                            consumer,
                            light,
                            overlay);
                else {
                    var actual = model;
                    storage.originalModel(
                            () -> actual.renderToBuffer(pose, consumer, light, overlay, color));
                }
            }
            if (!entity.isSpectator()) {
                var animation =
                        new VanillaLayerReplacement.Animation(
                                position, speed, partialTick, age, relative, pitch);
                if (direct)
                    for (var layer : layers)
                        VanillaLayerReplacement.collect(
                                layer, entity, model, animation, pose, output, parts, light);
                else
                    for (var layer : layers) {
                        var actual = (RenderLayer) layer;
                        storage.originalModel(
                                () ->
                                        actual.render(
                                                pose,
                                                output,
                                                light,
                                                entity,
                                                animation.position(),
                                                animation.speed(),
                                                animation.partial(),
                                                animation.age(),
                                                animation.yaw(),
                                                animation.pitch()));
                    }
            }
            parts.submit();
            pose.popPose();
            VanillaEntityReplacement.name(renderer, entity, partialTick, pose, output, light);
            NeoForge.EVENT_BUS.post(
                    new RenderLivingEvent.Post(entity, renderer, partialTick, pose, output, light));
            playerPost(entity, renderer, partialTick, pose, output, light);
            if (entity instanceof net.minecraft.world.entity.monster.Guardian guardian)
                VanillaGuardianRequests.collect(guardian, partialTick, pose, output);
        } finally {
            pose.popPose();
            if (entity instanceof net.minecraft.world.entity.animal.Wolf wolf && wolf.isWet())
                ((net.minecraft.client.model.WolfModel) model).setColor(-1);
            if (renderer instanceof ModelSelection selected) selected.radiance$finishSelected();
        }
    }

    private static void playerPost(
            LivingEntity entity,
            LivingEntityRenderer<?, ?> renderer,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                && renderer
                        instanceof
                        net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer)
            NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.client.event.RenderPlayerEvent.Post(
                            player, playerRenderer, partial, pose, buffers, light));
    }
}
