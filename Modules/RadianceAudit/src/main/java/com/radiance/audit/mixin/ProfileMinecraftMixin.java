package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=Minecraft.class, priority=2100)
public abstract class ProfileMinecraftMixin {
    @WrapMethod(method="runTick")
    private void audit$frame(boolean render, Operation<Void> original) {
        boolean enabled;
        try { enabled=FrameProfiler.beginFrame(); }
        catch (RuntimeException | LinkageError diagnostic) { audit$report(diagnostic); enabled=false; }
        if (!enabled) { original.call(render); return; }
        long cpu=FrameProfiler.cpuTime(), start=System.nanoTime(); boolean failed=true;
        try (var ignored=FrameProfiler.span(FrameTimings.Stage.FRAME_OTHER)) {
            original.call(render); failed=false;
        } finally {
            try { FrameProfiler.endFrame(start,cpu,failed); }
            catch (RuntimeException | LinkageError diagnostic) { audit$report(diagnostic); }
        }
    }
    @WrapMethod(method="tick")
    private void audit$tick(Operation<Void> original) {
        try(var ignored=FrameProfiler.span(FrameTimings.Stage.TICK)) { original.call(); }
    }
    @WrapMethod(method="close")
    private void audit$close(Operation<Void> original) {
        try { original.call(); } finally {
            try { FrameProfiler.finish(); } catch(RuntimeException | LinkageError diagnostic) { audit$report(diagnostic); }
        }
    }
    private static void audit$report(Throwable failure) {
        com.mojang.logging.LogUtils.getLogger().error("[Radiance Audit/profile] Observer failure; game error preserved",failure);
    }
}
