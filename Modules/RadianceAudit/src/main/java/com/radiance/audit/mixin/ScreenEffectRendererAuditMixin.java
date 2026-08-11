package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.apache.commons.lang3.tuple.Pair;
import com.radiance.audit.AuditHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScreenEffectRenderer.class, priority = 2000)
public abstract class ScreenEffectRendererAuditMixin {
    private static final String FAMILY = "camera.effects";
    private static final String BLOCK = "camera.block";
    private static final String FLUID = "camera.fluid";
    private static final String FIRE = "camera.fire";
    @Unique private static final ThreadLocal<Boolean> RADIANCE_AUDIT_FAMILY_SCOPE =
        ThreadLocal.withInitial(() -> false);

    @Shadow
    private static Pair<BlockState, BlockPos> getOverlayBlock(Player player) {
        throw new AssertionError();
    }

    @Shadow
    @Final
    private static ResourceLocation UNDERWATER_LOCATION;

    @Inject(method = "renderScreenEffect", at = @At("HEAD"))
    private static void radianceAudit$beginFamily(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        Player player = minecraft.player;
        boolean block = player != null && !player.noPhysics && getOverlayBlock(player) != null;
        boolean fluid = player != null && !player.isSpectator()
            && !player.getEyeInFluidType().isAir();
        boolean fire = player != null && !player.isSpectator() && player.isOnFire();
        boolean active = block || fluid || fire;
        RADIANCE_AUDIT_FAMILY_SCOPE.set(active);
        if (active) {
            AuditHooks.enterContinuous("SCREEN_EFFECT", FAMILY,
                "block=" + block + "; fluid="
                    + (fluid ? player.getEyeInFluidType() : "none") + "; fire=" + fire);
        }
    }

    @Inject(method = "renderScreenEffect", at = @At("RETURN"))
    private static void radianceAudit$endFamily(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        if (RADIANCE_AUDIT_FAMILY_SCOPE.get()) {
            AuditHooks.exit(FAMILY);
            RADIANCE_AUDIT_FAMILY_SCOPE.set(false);
        }
    }

    @Inject(method = "renderTex", at = @At("HEAD"))
    private static void radianceAudit$beginBlock(TextureAtlasSprite sprite, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", BLOCK,
            "texture=" + sprite.atlasLocation());
    }

    @Inject(method = "renderTex", at = @At("RETURN"))
    private static void radianceAudit$endBlock(TextureAtlasSprite sprite, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.exit(BLOCK);
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), require = 0)
    private static void radianceAudit$beginFluid(Minecraft minecraft, PoseStack pose,
        ResourceLocation texture, CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", FLUID, "texture=" + texture);
    }

    @Inject(method = "renderWater", at = @At("HEAD"), require = 0)
    private static void radianceAudit$beginWater(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", FLUID,
            "path=vanilla-water; texture=" + UNDERWATER_LOCATION);
    }

    /**
     * Samples the exact state consumed by the Vulkan bridge, after vanilla has selected the
     * fluid texture and immediately before BufferUploader submits the fullscreen quad. This
     * distinguishes a bad Minecraft slot, a stale ShaderInstance sampler, and a later native
     * descriptor/data error without changing the draw.
     */
    @Inject(method = "renderFluid",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
        require = 0)
    private static void radianceAudit$sampleFluidSampler(Minecraft minecraft, PoseStack pose,
        ResourceLocation texture, CallbackInfo ci) {
        radianceAudit$sampleSampler(minecraft, texture, "custom-fluid");
    }

    @Inject(method = "renderWater",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
        require = 0)
    private static void radianceAudit$sampleWaterSampler(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        radianceAudit$sampleSampler(minecraft, UNDERWATER_LOCATION, "vanilla-water");
    }

    @Unique
    private static void radianceAudit$sampleSampler(Minecraft minecraft,
        ResourceLocation texture, String path) {
        AbstractTexture expectedTexture = minecraft.getTextureManager().getTexture(texture);
        int expectedId = expectedTexture.getId();
        int slotId = RenderSystem.getShaderTexture(0);
        ShaderInstance shader = RenderSystem.getShader();
        Object samplerValue = shader instanceof IShaderProgramExt extension
            ? extension.radiance$getSamplerTexturesValue().get("Sampler0") : null;
        int samplerId = radianceAudit$textureId(samplerValue);
        TextureTracker.Texture metadata = TextureTracker.GLID2Texture.get(expectedId);

        AuditHooks.continuousEvent("SCREEN_EFFECT", "camera.fluid.sampler",
            "path=" + path
                + "; texture=" + texture
                + "; expectedId=" + expectedId
                + "; slot0=" + slotId
                + "; sampler0=" + samplerId
                + "; shader=" + radianceAudit$shaderName(shader)
                + "; expectedAliases=" + radianceAudit$aliases(expectedId)
                + "; slotAliases=" + radianceAudit$aliases(slotId)
                + "; metadata=" + (metadata == null ? "missing"
                    : metadata.width() + "x" + metadata.height() + "/"
                        + metadata.format() + "/mips=" + metadata.maxLayer()),
            expectedId == slotId && expectedId == samplerId ? "STATE_MATCH" : "STATE_MISMATCH",
            "MCVR_SHADER_DRAW");
    }

    @Inject(method = "renderFluid", at = @At("RETURN"), require = 0)
    private static void radianceAudit$endFluid(Minecraft minecraft, PoseStack pose,
        ResourceLocation texture, CallbackInfo ci) {
        AuditHooks.exit(FLUID);
    }

    @Inject(method = "renderWater", at = @At("RETURN"), require = 0)
    private static void radianceAudit$endWater(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.exit(FLUID);
    }

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void radianceAudit$beginFire(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", FIRE, "vanilla fire camera quads");
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void radianceAudit$endFire(Minecraft minecraft, PoseStack pose,
        CallbackInfo ci) {
        AuditHooks.exit(FIRE);
    }

    @Unique
    private static int radianceAudit$textureId(Object value) {
        if (value instanceof Integer id) return id;
        if (value instanceof AbstractTexture texture) return texture.getId();
        if (value instanceof RenderTarget target) return target.getColorTextureId();
        return 0;
    }

    @Unique
    private static String radianceAudit$shaderName(ShaderInstance shader) {
        return shader instanceof IShaderProgramExt extension
            ? extension.radiance$getShaderName() : String.valueOf(shader);
    }

    @Unique
    private static String radianceAudit$aliases(int textureId) {
        StringBuilder aliases = new StringBuilder();
        TextureTracker.textureID2GLID.forEach((name, id) -> {
            if (id == textureId) {
                if (!aliases.isEmpty()) aliases.append(',');
                aliases.append(name);
            }
        });
        return aliases.isEmpty() ? "none" : aliases.toString();
    }
}
