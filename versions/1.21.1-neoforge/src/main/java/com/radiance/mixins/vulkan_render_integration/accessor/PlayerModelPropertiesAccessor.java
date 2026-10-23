package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.compatibility.vanilla.VanillaLivingReplacement;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerModelPropertiesAccessor extends VanillaLivingReplacement.PlayerPoseAccess {
    @Invoker("setModelProperties")
    void radiance$preparePlayer(AbstractClientPlayer player);
}
