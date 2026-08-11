package com.radiance.audit.mixin;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.audit.AuditHooks;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkProxy.class, remap = false)
public abstract class ChunkProxyAuditMixin {
    @Inject(method = "onChunkLoaded", at = @At("HEAD"), remap = false)
    private static void radianceAudit$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        AuditHooks.event("CHUNK_PIPELINE", "ChunkProxy.onChunkLoaded",
            "chunk=" + pos.x + ',' + pos.z, "ISSUED", "JAVA_SCHEDULER");
    }
}
