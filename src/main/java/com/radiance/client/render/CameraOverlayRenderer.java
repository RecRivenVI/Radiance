package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * Owns the camera-space overlay phase which vanilla executes after the hand and
 * before the GUI.  The actual overlay method is deliberately left intact: it
 * contains the NeoForge block, water, custom-fluid, and fire hooks.
 */
public final class CameraOverlayRenderer {

    private CameraOverlayRenderer() {
    }

    /**
     * Mirrors the vanilla renderItemInHand call-site gates and the outer world
     * render gate.  In particular, renderHand is not the same as the
     * hideGui option: vanilla can still render camera overlays while the GUI
     * is hidden.
     */
    public static boolean shouldRender(Minecraft minecraft, boolean worldReady,
        boolean panoramicMode, boolean renderHand) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }

        Entity cameraEntity = minecraft.getCameraEntity();
        return shouldRenderGates(worldReady, minecraft.options.getCameraType().isFirstPerson(),
            renderHand, panoramicMode,
            cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping());
    }

    static boolean shouldRenderGates(boolean worldReady, boolean firstPerson, boolean renderHand,
        boolean panoramicMode, boolean sleeping) {
        return worldReady && firstPerson && renderHand && !panoramicMode && !sleeping;
    }

    /**
     * Draws vanilla ScreenEffectRenderer content in the camera-overlay phase.
     * The screen effect implementation remains the authority for BLOCK,
     * WATER, custom fluid, and FIRE ordering and NeoForge hook dispatch.
     *
     * <p>The caller has already reached the post-world/pre-GUI boundary.  A
     * temporary perspective projection and identity model-view are required
     * because GameRenderer has installed the GUI projection before invoking
     * Gui.render.  Both matrices are restored even when a mod-provided fluid
     * overlay throws.</p>
     */
    public static void render(Minecraft minecraft, Matrix4f projection) {
        GlStateBackup state = new GlStateBackup();
        RenderSystem.backupGlState(state);
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            // The original call inherits the default state left after the
            // hand RenderTypes clear themselves.  This relocated phase must
            // establish that state instead of inheriting GUI state.
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            // ShaderProxy maps OpenGL clip space to Vulkan by flipping Y. That also reverses
            // fullscreen-quad winding, so retaining vanilla's cull state would reject camera
            // overlays such as the underwater texture. These quads are intrinsically two-sided.
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            // ScreenEffectRenderer.renderFluid only enables blending and intentionally inherits
            // the blend function left by the hand pass. This relocated phase must establish the
            // same source-over factors instead of inheriting an unrelated world/GUI draw state.
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(new Matrix4f(projection),
                VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            // Keep this call whole.  It invokes NeoForge's BLOCK/WATER/FIRE
            // hooks and IClientFluidTypeExtensions for custom fluids.
            try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
                RenderCaptureContract.ScopeKind.CAMERA_OVERLAY, "camera_overlay")) {
                ScreenEffectRenderer.renderScreenEffect(minecraft, new PoseStack());
            }
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.restoreGlState(state);
        }
    }
}
