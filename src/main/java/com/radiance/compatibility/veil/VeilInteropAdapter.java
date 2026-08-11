package com.radiance.compatibility.veil;

import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.forge.impl.ForgeRenderTypeStageHandler;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;

/** Veil-linked implementation kept out of the always-loadable public facade. */
final class VeilInteropAdapter {
    private VeilInteropAdapter() {
    }

    static Runnable captureCameraMatrices() {
        CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();
        CameraMatrices backup = new CameraMatrices();
        matrices.backup(backup);
        return () -> matrices.restore(backup);
    }

    @SuppressWarnings("unchecked")
    static <T> T createRenderBridge(Frustum frustum) {
        return (T) VeilRenderBridge.create(frustum);
    }

    static void setBlockLayers(Set<RenderType> layers) {
        ForgeRenderTypeStageHandler.setBlockLayers(layers);
    }
}
