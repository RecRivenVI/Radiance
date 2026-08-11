package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.compatibility.simulated.SimulatedDiagramCompatibility;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramStickyNote;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DiagramStickyNote.class, remap = false)
public abstract class SimulatedDiagramStickyNoteMixins {
    @Shadow private float renderTime;

    @Redirect(method = "create", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;withSize(II)Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo$Builder;"))
    private AdvancedFbo.Builder radiance$physicalSize(int width, int height) {
        return SimulatedDiagramCompatibility.framebuffer(width, height);
    }

    @Inject(method = "populateFBO", at = @At("HEAD"))
    private void radiance$uncap(float tick, CallbackInfo ci) { renderTime = Float.MAX_VALUE; }

    @WrapOperation(method = "free", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;free()V"))
    private void radiance$release(AdvancedFbo fbo, Operation<Void> original) {
        SimulatedDiagramCompatibility.release(fbo, () -> original.call(fbo));
    }
}
