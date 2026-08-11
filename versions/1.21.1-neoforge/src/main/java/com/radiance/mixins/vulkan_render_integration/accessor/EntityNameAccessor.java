package com.radiance.mixins.vulkan_render_integration.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.adapter.vanilla.VanillaLivingReplacement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityNameAccessor extends VanillaLivingReplacement.NameAccess {
    @Accessor("shadowRadius")
    void radiance$shadowRadius(float value);

    @Accessor("shadowStrength")
    void radiance$shadowStrength(float value);

    @Invoker("shouldShowName")
    boolean radiance$showName(Entity entity);

    @Invoker("renderNameTag")
    void radiance$name(
            Entity entity,
            Component content,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            float partialTick);
}
