package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GameRenderer.class, priority=2100)
public abstract class ProfileGameRendererMixin {
    @WrapMethod(method="pick(F)V")
    private void audit$pick(float partialTick, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.CAMERA_PICK)) { original.call(partialTick); }
    }
    @WrapMethod(method="render")
    private void audit$render(DeltaTracker time, boolean tick, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.GAME_RENDER_OTHER)) { original.call(time,tick); }
    }
    @WrapMethod(method="renderLevel")
    private void audit$world(DeltaTracker time, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.WORLD_RENDER_OTHER)) { original.call(time); }
    }
}
