package com.radiance.mixins.compatibility.ponder;

import com.radiance.client.proxy.vulkan.PonderProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Archived PT collection/lifecycle hook; intentionally absent from radiance.mixins.json. */
@Pseudo
@Mixin(targets = "net.createmod.ponder.foundation.ui.PonderUI", remap = false)
public abstract class PonderUILifecycleMixins {
    @org.spongepowered.asm.mixin.Shadow private java.util.List<net.createmod.ponder.foundation.PonderScene> scenes;
    @org.spongepowered.asm.mixin.Shadow private int index;
    @org.spongepowered.asm.mixin.Shadow private net.createmod.catnip.animation.LerpedFloat lazyIndex;

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderVisibleScenes")
    private void radiance$collectFrame(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
        float partialTick, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        float interpolated = lazyIndex.getValue(partialTick);
        long first = ((com.radiance.compatibility.ponder.PonderSceneIdentity) scenes.get(index)).radiance$sceneId();
        long[] visible = Math.abs(interpolated - index) > 1f / 512f
            ? new long[] {first, ((com.radiance.compatibility.ponder.PonderSceneIdentity)
                scenes.get(index + (interpolated < index ? -1 : 1))).radiance$sceneId()}
            : new long[] {first};
        com.radiance.compatibility.ponder.PonderPathTracer.beginFrame(visible);
        try {
            original.call(graphics, mouseX, mouseY, partialTick);
        } catch (Throwable failure) {
            try { com.radiance.client.proxy.vulkan.UiPathTracingProxy.endFrame(false); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        com.radiance.client.proxy.vulkan.UiPathTracingProxy.endFrame(true);
    }
    @Inject(method = "removed", at = @At("RETURN"), remap = true)
    private void radiance$releaseScenes(CallbackInfo ci) {
        PonderProxy.releaseScenes();
        com.radiance.compatibility.ponder.PonderPathTracer.releaseOutputs();
    }
}
