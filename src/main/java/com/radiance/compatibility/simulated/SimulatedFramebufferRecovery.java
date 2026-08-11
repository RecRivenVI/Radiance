package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;

/** Snapshot of the actual Vulkan targets and viewport around one original offscreen consumer. */
public final class SimulatedFramebufferRecovery {
    private SimulatedFramebufferRecovery() {}

    public static void capture(SimulatedRenderRecovery recovery) {
        int read = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int draw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        int[] viewport = PipelineStateProxy.ViewportState.getViewport().clone();
        if (viewport.length != 4) throw new IllegalStateException("Invalid shadow/group viewport");
        recovery.add(() -> FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, read));
        recovery.add(() -> FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, draw));
        recovery.add(() -> RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]));
    }
}
