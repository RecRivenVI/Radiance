package com.radiance.mixins.compatibility.ponder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.compatibility.ponder.PonderPathTracer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Archived PT hook; intentionally absent from radiance.mixins.json. */
@Pseudo
@Mixin(targets = "net.createmod.ponder.foundation.PonderScene", remap = false)
public abstract class PonderScenePathTracingMixins implements com.radiance.compatibility.ponder.PonderSceneIdentity {
    @org.spongepowered.asm.mixin.Unique
    private final long radiance$sceneId =
        com.radiance.client.proxy.vulkan.UiPathTracingProxy.allocateScene();
    @Override public long radiance$sceneId() { return radiance$sceneId; }
    @WrapMethod(method = "renderScene")
    private void radiance$traceScene(SuperRenderTypeBuffer buffer, GuiGraphics graphics,
        float partialTick, Operation<Void> original) {
        try (PonderPathTracer capture = new PonderPathTracer(buffer, radiance$sceneId, graphics.pose().last().pose())) {
            original.call(capture, graphics, partialTick);
            capture.render(graphics);
        }
    }
}
