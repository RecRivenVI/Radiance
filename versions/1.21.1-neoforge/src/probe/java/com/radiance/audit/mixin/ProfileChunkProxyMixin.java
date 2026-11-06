package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FlowProfileCounters;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkProxy.class, remap = false)
public abstract class ProfileChunkProxyMixin {
    @WrapMethod(method = "onChunkLoaded")
    private static void audit$received(
            net.minecraft.world.level.ChunkPos pos, Operation<Void> original) {
        boolean flow = FrameProfiler.flowAuditEnabled();
        if (flow) {
            FlowProfileCounters.event("LOADED_COLUMN", 1);
            FlowProfileCounters.neighbor(true);
        }
        try {
            original.call(pos);
        } finally {
            if (flow) FlowProfileCounters.neighbor(false);
        }
        com.radiance.audit.LoadingChainProbe.received();
    }

    @WrapMethod(method = "rebuild")
    private static void audit$rebuild(Camera camera, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.CHUNK_SCHEDULE)) {
            original.call(camera);
        }
    }

    @WrapMethod(
            method =
                    "enqueueRebuild(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;ZZ)V")
    private static void audit$dirty(
            SectionRenderDispatcher.RenderSection section,
            boolean interactive,
            boolean direct,
            Operation<Void> original) {
        if (FrameProfiler.flowAuditEnabled()) FlowProfileCounters.dirty(interactive);
        original.call(section, interactive, direct);
    }
}
