package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.client.proxy.world.ClientLightUpdates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class ClientLightingMixins {
    private ClientLightingMixins() {}

    @Mixin(ClientPacketListener.class)
    public abstract static class Packets {
        @WrapMethod(method = "handleLevelChunkWithLight")
        private void radiance$batchLightPasses(
                ClientboundLevelChunkWithLightPacket packet, Operation<Void> original) {
            if (Minecraft.getInstance().isSameThread())
                ClientLightUpdates.receive(() -> original.call(packet));
            else original.call(packet);
        }
    }

    @Mixin(LevelLightEngine.class)
    public abstract static class Engine {
        @WrapMethod(method = "runLightUpdates")
        private int radiance$publishBeforeRendering(Operation<Integer> original) {
            // Packet data and section enabling still happen immediately. Only repeated storage
            // publication is deferred to WorldRenderer's existing light pass in this frame.
            return ClientLightUpdates.defer((LevelLightEngine) (Object) this) ? 0 : original.call();
        }
    }

    @Mixin(ClientChunkCache.class)
    public abstract static class Notifications {
        @WrapOperation(
                method = "onLightUpdate",
                at =
                        @At(
                                value = "INVOKE",
                                target =
                                        "Lnet/minecraft/client/renderer/LevelRenderer;setSectionDirty(III)V"))
        private void radiance$keepLightSeparateFromGeometry(
                LevelRenderer renderer, int x, int y, int z, Operation<Void> original) {
            var level = Minecraft.getInstance().level;
            if (level == null || level.getChunkSource() != (Object) this)
                original.call(renderer, x, y, z);
            // Keep other light listeners and the published data. Only this raster-light-only
            // geometry invalidation is absent; enableChunkLight's content notifications remain.
        }
    }
}
