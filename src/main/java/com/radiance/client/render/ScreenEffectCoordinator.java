package com.radiance.client.render;

import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Defines the ownership and ordering boundaries for gameplay screen effects.
 *
 * <p>This coordinator deliberately keeps vanilla/NeoForge producers intact. It assigns each
 * family to the Radiance phase that consumes it and owns the native replacement calls for the
 * post effects that vanilla cannot execute after the Vulkan takeover.</p>
 */
public final class ScreenEffectCoordinator {
    public enum Stage {
        WORLD_CAMERA,
        CAMERA_OVERLAY,
        GUI_OVERLAY,
        UI_POST,
        WORLD_SHADING
    }

    public enum EntityPostEffect {
        NONE(null, -1),
        CREEPER("shaders/post/creeper.json", 0),
        SPIDER("shaders/post/spider.json", 1),
        INVERT("shaders/post/invert.json", 2);

        private final String resourcePath;
        private final int nativeId;

        EntityPostEffect(String resourcePath, int nativeId) {
            this.resourcePath = resourcePath;
            this.nativeId = nativeId;
        }

        public int nativeId() {
            return nativeId;
        }

        public boolean supported() {
            return this != NONE;
        }

        public static EntityPostEffect resolve(ResourceLocation resource) {
            if (resource == null) return NONE;
            String path = resource.getPath();
            for (EntityPostEffect effect : values()) {
                if (effect.resourcePath != null && effect.resourcePath.equals(path)) {
                    return effect;
                }
            }
            return NONE;
        }
    }

    private ScreenEffectCoordinator() {
    }

    public static void renderCameraOverlay(Minecraft minecraft, boolean worldReady,
        boolean panoramicMode, boolean renderHand, Matrix4f projection) {
        if (CameraOverlayRenderer.shouldRender(minecraft, worldReady, panoramicMode, renderHand)) {
            CameraOverlayRenderer.render(minecraft, projection);
        }
    }

    public static RenderCaptureContract.ScopeToken beginGuiPhase() {
        return RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.GUI,
            "game_renderer_gui_phase");
    }

    public static boolean applyEntityPostEffect(EntityPostEffect effect) {
        if (effect == null || !effect.supported()) return false;
        boolean auditing = RenderAuditBridge.accepts("SCREEN_EFFECT");
        long auditId = auditing ? RenderAuditBridge.beginIntent("SCREEN_EFFECT",
            "post.entity_effect", "effect=" + effect.name().toLowerCase()
                + "; nativeId=" + effect.nativeId()) : 0L;
        boolean recorded = RendererProxy.postEntityEffect(effect.nativeId());
        if (auditing) {
            reportBackendResult(auditId, recorded, "entity post effect " + effect.name());
        }
        return recorded;
    }

    public static boolean applyMenuBlur(Minecraft minecraft) {
        float radius = minecraft.options.getMenuBackgroundBlurriness();
        if (radius < 1.0F) return false;
        BufferProxy.updateOverlayPostUniform(radius);
        boolean recorded = RendererProxy.postBlur();
        if (RenderAuditBridge.accepts("SCREEN_EFFECT")) {
            RenderAuditBridge.transition(0L,
                recorded ? "BACKEND_RECORDED" : "BACKEND_UNAVAILABLE",
                "MCVR_UI_POST",
                recorded ? "menu blur command recorded"
                    : "renderer, frame context or UI module was unavailable",
                true);
        }
        return recorded;
    }

    private static void reportBackendResult(long auditId, boolean recorded, String effect) {
        RenderAuditBridge.transition(auditId,
            recorded ? "BACKEND_RECORDED" : "BACKEND_UNAVAILABLE",
            "MCVR_UI_POST",
            recorded ? effect + " command recorded"
                : effect + " could not be recorded because the backend context was unavailable",
            true);
    }
}
