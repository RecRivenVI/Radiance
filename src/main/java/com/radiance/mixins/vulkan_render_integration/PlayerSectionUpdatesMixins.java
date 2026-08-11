package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.PlayerSectionUpdates;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class PlayerSectionUpdatesMixins {
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"))
    private void beginDestroy(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> ci) {
        PlayerSectionUpdates.begin(pos, pos);
    }
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void beginUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
        CallbackInfoReturnable<InteractionResult> ci) {
        PlayerSectionUpdates.begin(
            hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()));
    }
}
