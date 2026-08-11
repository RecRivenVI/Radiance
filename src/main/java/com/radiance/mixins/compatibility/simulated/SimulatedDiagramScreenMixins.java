package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.compatibility.simulated.SimulatedDiagramCompatibility;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DiagramScreen.class, remap = false)
public abstract class SimulatedDiagramScreenMixins {
    @Shadow private float renderTime;
    @Shadow private AdvancedFbo finalFbo;
    @Unique private SimulatedDiagramCompatibility.Coverage radiance$coverage;

    @Redirect(method = "init", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;withSize(II)Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo$Builder;"))
    private AdvancedFbo.Builder radiance$physicalSize(int width, int height) {
        return SimulatedDiagramCompatibility.framebuffer(width, height);
    }

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void radiance$uncap(SubLevel subLevel, float tick, CallbackInfo ci) {
        renderTime = Float.MAX_VALUE;
    }

    @WrapMethod(method = "draw")
    private static void radiance$draw(SubLevel level, float tick, Quaternionf orientation,
        Matrix4f projection, Vector3d camera, float width, float height, AdvancedFbo fbo,
        AdvancedFbo outline, AdvancedFbo result, float palette, float fade, int line,
        int shadow, Operation<Void> original) {
        SimulatedDiagramCompatibility.draw((ClientSubLevel) level, fbo, width, height,
            () -> original.call(level, tick, orientation, projection, camera, width, height,
                fbo, outline, result, palette, fade, line, shadow));
    }

    @WrapMethod(method = "addGreebles")
    private void radiance$coverage(int x, int y, Operation<Void> original) {
        try (var coverage = new SimulatedDiagramCompatibility.Coverage(finalFbo,
            DiagramScreen.DIAGRAM_TEXTURE.width, DiagramScreen.DIAGRAM_TEXTURE.height)) {
            radiance$coverage = coverage;
            original.call(x, y);
        } finally { radiance$coverage = null; }
    }

    @Inject(method = "aabbInFramebuffer", at = @At("HEAD"), cancellable = true)
    private void radiance$occupied(AABB box, CallbackInfoReturnable<Boolean> cir) {
        if (radiance$coverage == null) throw new IllegalStateException("Diagram coverage outside decoration pass");
        cir.setReturnValue(radiance$coverage.occupied(box));
    }

    @WrapOperation(method = "freeFramebuffers", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;free()V"))
    private void radiance$release(AdvancedFbo fbo, Operation<Void> original) {
        SimulatedDiagramCompatibility.release(fbo, () -> original.call(fbo));
    }
}
