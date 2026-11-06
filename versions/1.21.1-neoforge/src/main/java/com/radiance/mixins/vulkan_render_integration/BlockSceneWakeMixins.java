package com.radiance.mixins.vulkan_render_integration;

import com.radiance.api.render.BlockEntityPublications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block events and data packets address a position; the receiving source may be a pair. */
public final class BlockSceneWakeMixins {
    private BlockSceneWakeMixins() {}

    @Mixin(Level.class)
    public abstract static class Events {
        @Inject(method = "blockEvent", at = @At("HEAD"))
        private void radiance$blockEvent(
                BlockPos pos, Block block, int type, int data, CallbackInfo ci) {
            if (((Level) (Object) this).isClientSide) BlockEntityPublications.wake(pos);
        }
    }

    @Mixin(ClientPacketListener.class)
    public abstract static class Packets {
        @Inject(method = "handleBlockEntityData", at = @At("RETURN"))
        private void radiance$blockEntityData(
                ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
            if (Minecraft.getInstance().isSameThread())
                BlockEntityPublications.wake(packet.getPos());
        }
    }
}
