package com.radiance.compatibility.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Coordinates Veil's processed vanilla-shader reload with Radiance's Vulkan registry. */
public final class VeilVanillaShaderAdapter {
    private VeilVanillaShaderAdapter() {
    }

    public static boolean isVeilWrapper(ShaderInstance shader) {
        return shader instanceof ShaderProgramImpl.Wrapper;
    }

    public static int activeBuffers(ShaderInstance shader) {
        return VeilVanillaShaderReloadBridge.activeBuffers(shader);
    }

    public static void begin(ShaderInstance shader, int activeBuffers) {
        VeilVanillaShaderReloadBridge.begin(shader, activeBuffers);
    }

    public static void executeStage(Minecraft minecraft, Runnable callback) {
        minecraft.execute(VeilVanillaShaderReloadBridge.wrapStageCallback(callback));
    }

    public static void capture(ShaderInstance shader, boolean vertex, String source,
        int activeBuffers) {
        VeilVanillaShaderReloadBridge.capture(shader, vertex, source, activeBuffers);
    }

    public static void finishStage(DynamicBufferManager manager, ShaderInstance shader) {
        VeilVanillaShaderReloadBridge.finishStage(manager, shader);
    }

    public static void finish(ShaderInstance shader) {
        Minecraft.getInstance().execute(VeilVanillaShaderReloadBridge.wrapStageCallback(
            () -> VeilVanillaShaderReloadBridge.finishShader(
                VeilRenderSystem.renderer().getDynamicBufferManger(), shader)));
        VeilVanillaShaderReloadBridge.finishScheduling();
    }
}
