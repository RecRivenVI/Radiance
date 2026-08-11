package com.radiance.mixins.vulkan_render_integration.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.adapter.vanilla.VanillaLivingReplacement;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererStateAccessor extends VanillaLivingReplacement.StateAccess {
    @Accessor("model")
    void radiance$selectModel(net.minecraft.client.model.EntityModel<?> model);

    @Invoker("getAttackAnim")
    float radiance$attack(LivingEntity entity, float partialTick);

    @Invoker("getBob")
    float radiance$bob(LivingEntity entity, float partialTick);

    @Invoker("setupRotations")
    void radiance$rotations(
            LivingEntity entity,
            PoseStack pose,
            float age,
            float bodyYaw,
            float partialTick,
            float scale);

    @Invoker("scale")
    void radiance$scale(LivingEntity entity, PoseStack pose, float partialTick);

    @Invoker("isBodyVisible")
    boolean radiance$bodyVisible(LivingEntity entity);

    @Invoker("getWhiteOverlayProgress")
    float radiance$whiteOverlay(LivingEntity entity, float partialTick);

    @Invoker("getRenderType")
    RenderType radiance$renderType(
            LivingEntity entity, boolean visible, boolean translucent, boolean glowing);
}
