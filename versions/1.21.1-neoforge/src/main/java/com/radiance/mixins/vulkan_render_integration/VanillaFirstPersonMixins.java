package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.vanilla.*;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserve source hand/action and extension callbacks; submit their actual draw leaves as
 * resources.
 */
public final class VanillaFirstPersonMixins {
    private VanillaFirstPersonMixins() {}

    @Mixin(PlayerRenderer.class)
    public abstract static class Arms {
        @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void radiance$handParts(
                PoseStack pose,
                MultiBufferSource output,
                int light,
                AbstractClientPlayer player,
                ModelPart arm,
                ModelPart sleeve,
                CallbackInfo ci) {
            var renderer = (PlayerRenderer) (Object) this;
            if (!(output instanceof StorageVertexConsumerProvider storage)
                    || !storage.rigidBakedEnabled()
                    || !SourceCompatibility.producer(renderer.getClass())
                    || !PartModelCapture.sourceCompatible()) return;
            PlayerModel model = renderer.getModel();
            if (!VanillaModelPose.supports(model)) return;
            ((VanillaLivingReplacement.PlayerPoseAccess) this).radiance$preparePlayer(player);
            model.attackTime = 0;
            model.crouching = false;
            model.swimAmount = 0;
            model.setupAnim(player, 0, 0, 0, 0, 0);
            arm.xRot = 0;
            var texture = player.getSkin().texture();
            var requests = new BoundPartCapture(storage);
            var body = RenderType.entitySolid(texture);
            requests.add(
                    VanillaModelPose.extract(arm, pose, -1),
                    body,
                    output.getBuffer(body),
                    light,
                    OverlayTexture.NO_OVERLAY);
            sleeve.xRot = 0;
            var coating = RenderType.entityTranslucent(texture);
            requests.add(
                    VanillaModelPose.extract(sleeve, pose, -1),
                    coating,
                    output.getBuffer(coating),
                    light,
                    OverlayTexture.NO_OVERLAY);
            requests.submit();
            ci.cancel();
        }
    }

    @Mixin(ItemInHandRenderer.class)
    public abstract static class Maps {
        @Shadow @Final private static RenderType MAP_BACKGROUND;
        @Shadow @Final private static RenderType MAP_BACKGROUND_CHECKERBOARD;

        @Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
        private void radiance$handMap(
                PoseStack pose,
                MultiBufferSource output,
                int light,
                ItemStack stack,
                CallbackInfo ci) {
            if (!(output instanceof StorageVertexConsumerProvider storage)
                    || !storage.rigidBakedEnabled()
                    || !SourceCompatibility.producer(ItemInHandRenderer.class)) return;
            var minecraft = Minecraft.getInstance();
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(.38F, .38F, .38F);
            pose.translate(-.5F, -.5F, 0);
            pose.scale(.0078125F, .0078125F, .0078125F);
            var data = MapItem.getSavedData(stack, minecraft.level);
            VanillaMapRequests.image(
                    pose,
                    output,
                    data == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD,
                    light,
                    -7,
                    -7,
                    135,
                    135,
                    0,
                    UvTransform.IDENTITY);
            if (data != null)
                minecraft
                        .gameRenderer
                        .getMapRenderer()
                        .render(pose, output, stack.get(DataComponents.MAP_ID), data, false, light);
            ci.cancel();
        }
    }
}
