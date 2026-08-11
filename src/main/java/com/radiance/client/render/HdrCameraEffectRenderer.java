package com.radiance.client.render;

import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Converts known vanilla/NeoForge camera overlays into the HDR world-composite contract. */
public final class HdrCameraEffectRenderer {
    private HdrCameraEffectRenderer() {
    }

    public static boolean captureBlock(TextureAtlasSprite sprite) {
        if (!isCameraOverlayScope() || sprite == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;

        int textureId = minecraft.getTextureManager().getTexture(sprite.atlasLocation()).getId();
        // Vanilla uses a fixed 10% color multiplier for this overlay. Keep that baseline until
        // the HDR route itself has been confirmed in a real frame.
        float lightScale = 0.1F;
        boolean recorded = RendererProxy.cameraBlockEffect(textureId,
            sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), lightScale);
        audit("camera.block", textureId, recorded);
        return recorded;
    }

    public static boolean captureFluid(Minecraft minecraft, ResourceLocation texture) {
        if (!isCameraOverlayScope() || minecraft == null || texture == null
            || minecraft.player == null || minecraft.level == null) return false;

        int textureId = minecraft.getTextureManager().getTexture(texture).getId();
        BlockPos eye = BlockPos.containing(minecraft.player.getX(), minecraft.player.getEyeY(),
            minecraft.player.getZ());
        float brightness = LightTexture.getBrightness(minecraft.level.dimensionType(),
            minecraft.level.getMaxLocalRawBrightness(eye));
        boolean recorded = RendererProxy.cameraFluidEffect(textureId,
            -minecraft.player.getYRot() / 64.0F,
            minecraft.player.getXRot() / 64.0F,
            4.0F, 4.0F, 0.1F, brightness);
        audit("camera.fluid", textureId, recorded);
        return recorded;
    }

    /** Translates one Veil block-overlay draw after Veil supplied its real uniforms and texture. */
    public static boolean captureVeilBlock(int textureId, float uStart, float vStart,
        float uSpan, float vSpan, float lightScale) {
        if (!isCameraOverlayScope()) return false;
        boolean recorded = RendererProxy.cameraBlockEffect(textureId,
            uStart + uSpan, vStart + vSpan, uStart, vStart, lightScale);
        audit("camera.block.veil_call", textureId, recorded);
        return recorded;
    }

    /** Translates one Veil water-overlay draw from its actual TexOffset/ColorModulator values. */
    public static boolean captureVeilFluid(int textureId, float uStart, float vStart,
        float uSpan, float vSpan, float alpha, float brightness) {
        if (!isCameraOverlayScope()) return false;
        boolean recorded = RendererProxy.cameraFluidEffect(textureId,
            uStart + uSpan, vStart + vSpan, -uSpan, -vSpan, alpha, brightness);
        audit("camera.fluid.veil_call", textureId, recorded);
        return recorded;
    }

    public static boolean captureFire(TextureAtlasSprite sprite) {
        if (!isCameraOverlayScope() || sprite == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        int textureId = minecraft.getTextureManager().getTexture(sprite.atlasLocation()).getId();

        float uMid = (sprite.getU0() + sprite.getU1()) * 0.5F;
        float vMid = (sprite.getV0() + sprite.getV1()) * 0.5F;
        float shrink = sprite.uvShrinkRatio();
        float u0 = Mth.lerp(shrink, sprite.getU0(), uMid);
        float u1 = Mth.lerp(shrink, sprite.getU1(), uMid);
        float v0 = Mth.lerp(shrink, sprite.getV0(), vMid);
        float v1 = Mth.lerp(shrink, sprite.getV1(), vMid);
        boolean recorded = RendererProxy.cameraFireEffect(textureId, u0, v0, u1, v1,
            0.9F, 1.0F);
        audit("camera.fire", textureId, recorded);
        return recorded;
    }

    private static boolean isCameraOverlayScope() {
        return RenderCaptureContract.currentScope().kind()
            == RenderCaptureContract.ScopeKind.CAMERA_OVERLAY;
    }

    private static void audit(String source, int textureId, boolean recorded) {
        if (!RenderAuditBridge.accepts("SCREEN_EFFECT")) return;
        long id = RenderAuditBridge.beginIntent("SCREEN_EFFECT", source,
            "texture=" + textureId);
        RenderAuditBridge.transition(id,
            recorded ? "BACKEND_RECORDED" : "BACKEND_UNAVAILABLE",
            recorded ? "MCVR_HDR_CAMERA" : "VULKAN_UI_FALLBACK",
            recorded ? "camera effect recorded before tone mapping"
                : "HDR camera contract unavailable; vanilla draw retained",
            true);
    }
}
