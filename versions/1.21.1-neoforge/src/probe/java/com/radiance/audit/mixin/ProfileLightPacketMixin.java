package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
public abstract class ProfileLightPacketMixin {
    @WrapMethod(method = "applyLightData")
    private void audit$packet(
            int x, int z, ClientboundLightUpdatePacketData data, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LIGHT_PACKET_APPLY)) {
            FrameProfiler.lightWork(Stage.LIGHT_PACKET_APPLY, 1);
            original.call(x, z, data);
        }
    }

    @WrapMethod(method = "enableChunkLight")
    private void audit$enable(LevelChunk chunk, int x, int z, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LIGHT_CHUNK_ENABLE)) {
            FrameProfiler.lightWork(Stage.LIGHT_CHUNK_ENABLE, 1);
            original.call(chunk, x, z);
        }
    }
}
