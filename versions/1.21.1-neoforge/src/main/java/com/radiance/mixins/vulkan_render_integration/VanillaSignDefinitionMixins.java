package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.adapter.vanilla.*;
import com.radiance.client.vertex.*;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Source-specific sign model selection; the part and text executors are shared. */
public final class VanillaSignDefinitionMixins {
    private VanillaSignDefinitionMixins() {}

    @Mixin(SignRenderer.class)
    public abstract static class Signs implements VanillaBlockReplacement.Source {
        @Shadow @Final private Map<WoodType, SignRenderer.SignModel> signModels;
        @Shadow @Final private Font font;

        @Shadow
        public abstract float getSignModelRenderScale();

        @Shadow
        public abstract float getSignTextRenderScale();

        @Invoker("translateSign")
        protected abstract void radiance$translate(PoseStack pose, float angle, BlockState state);

        @Invoker("getSignMaterial")
        protected abstract Material radiance$material(WoodType wood);

        @Invoker("getTextOffset")
        protected abstract Vec3 radiance$textOffset();

        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderSignText")
        private void radiance$textMaterial(
                net.minecraft.core.BlockPos pos,
                SignText text,
                PoseStack pose,
                MultiBufferSource buffers,
                int light,
                int lineHeight,
                int width,
                boolean front,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
            try (var material = VanillaSignRequests.material(text)) {
                original.call(pos, text, pose, buffers, light, lineHeight, width, front);
            }
        }

        public void radiance$collect(
                BlockEntity value,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay) {
            var sign = (SignBlockEntity) value;
            var state = sign.getBlockState();
            var block = (SignBlock) state.getBlock();
            var wood = SignBlock.getWoodType(block);
            ModelPart root;
            if (this instanceof VanillaSignRequests.Hanging hanging)
                root = hanging.radiance$signRoot(sign);
            else {
                var model = signModels.get(wood);
                model.stick.visible = block instanceof StandingSignBlock;
                root = model.root;
            }
            pose.pushPose();
            radiance$translate(pose, -block.getYRotationDegrees(state), state);
            pose.pushPose();
            float scale = getSignModelRenderScale();
            pose.scale(scale, -scale, -scale);
            VanillaBlockReplacement.add(
                    VanillaModelPose.extract(root, pose, -1),
                    radiance$material(wood),
                    RenderType::entityCutoutNoCull,
                    buffers,
                    requests,
                    light,
                    overlay);
            pose.popPose();
            VanillaSignRequests.text(
                    sign,
                    font,
                    pose,
                    buffers,
                    light,
                    getSignTextRenderScale(),
                    radiance$textOffset());
            pose.popPose();
        }
    }

    @Mixin(HangingSignRenderer.class)
    public abstract static class HangingSigns implements VanillaSignRequests.Hanging {
        @Shadow @Final
        private Map<WoodType, HangingSignRenderer.HangingSignModel> hangingSignModels;

        public ModelPart radiance$signRoot(SignBlockEntity sign) {
            var state = sign.getBlockState();
            var model = hangingSignModels.get(SignBlock.getWoodType((SignBlock) state.getBlock()));
            model.evaluateVisibleParts(state);
            return model.root;
        }
    }
}
