package com.radiance.audit.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import com.radiance.audit.AuditHooks;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the exact sampler state immediately before Radiance encodes Vulkan uniforms. */
@Mixin(value = ShaderProxy.class, remap = false)
public abstract class ShaderProxyAuditMixin {
    @Unique
    private static final ResourceLocation RADIANCE_AUDIT_UNDERWATER =
        ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    @Inject(method = "createUniform", at = @At("HEAD"), require = 0)
    private static void radianceAudit$sampleUnderwaterUniform(ShaderDefinition definition,
        ShaderInstance shader, MemoryStack stack,
        CallbackInfoReturnable<ShaderProxy.UniformHandle> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderCaptureContract.Scope scope = RenderCaptureContract.currentScope();
        boolean water = minecraft.player != null
            && minecraft.player.isEyeInFluid(FluidTags.WATER);
        if (scope.kind() != RenderCaptureContract.ScopeKind.CAMERA_OVERLAY) {
            return;
        }

        IShaderProgramExt extension = (IShaderProgramExt) (Object) shader;
        String shaderName = extension.radiance$getShaderName();

        AbstractTexture expectedTexture = minecraft.getTextureManager()
            .getTexture(RADIANCE_AUDIT_UNDERWATER);
        int expectedId = expectedTexture.getId();
        int slotId = RenderSystem.getShaderTexture(0);
        int legacyUnitId = TextureProxy.boundTexture(0);
        Map<String, Object> samplers = extension.radiance$getSamplerTexturesValue();
        Object sampler = samplers.containsKey("DiffuseSampler")
            ? samplers.get("DiffuseSampler") : samplers.get("Sampler0");
        int samplerId = radianceAudit$textureId(sampler);
        int effectiveId = samplerId != 0 ? samplerId
            : legacyUnitId > 0 ? legacyUnitId : slotId;
        TextureTracker.Texture metadata = TextureTracker.GLID2Texture.get(expectedId);

        String source = "camera.fluid.uniform/" + scope.kind() + "/" + shaderName;
        AuditHooks.continuousEvent("SCREEN_EFFECT", source,
            "scope=" + scope.kind() + '[' + scope.label() + ']'
                + "; fluid=" + (minecraft.player == null
                    ? "no-player" : minecraft.player.getEyeInFluidType())
                + "; waterTag=" + water
                + "; texture=" + RADIANCE_AUDIT_UNDERWATER
                + "; expectedId=" + expectedId
                + "; slot0=" + slotId
                + "; legacyUnit0=" + legacyUnitId
                + "; sampler0=" + samplerId
                + "; effective0=" + effectiveId
                + "; samplerType=" + (sampler == null ? "null" : sampler.getClass().getName())
                + "; shader=" + shaderName
                + "; definition=" + definition.key()
                + "; expectedAliases=" + radianceAudit$aliases(expectedId)
                + "; slotAliases=" + radianceAudit$aliases(slotId)
                + "; metadata=" + (metadata == null ? "missing"
                    : metadata.width() + "x" + metadata.height() + "/"
                        + metadata.format() + "/mips=" + metadata.maxLayer()),
            water && expectedId == effectiveId
                ? "STATE_MATCH" : "STATE_MISMATCH",
            "MCVR_SHADER_UNIFORM");
    }

    @Unique
    private static int radianceAudit$textureId(Object value) {
        if (value instanceof Integer id) return id;
        if (value instanceof AbstractTexture texture) return texture.getId();
        if (value instanceof RenderTarget target) return target.getColorTextureId();
        return 0;
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
