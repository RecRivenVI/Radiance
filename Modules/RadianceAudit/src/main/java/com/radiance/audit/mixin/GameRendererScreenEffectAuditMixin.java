package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.audit.AuditHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class GameRendererScreenEffectAuditMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack itemActivationItem;
    @Shadow private int itemActivationTicks;
    @Unique private boolean radianceAudit$itemActivationScope;

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"))
    private void radianceAudit$beginNauseaTexture(GuiGraphics graphics, float intensity,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.nausea_texture",
            "intensity=" + intensity);
    }

    @Inject(method = "renderConfusionOverlay", at = @At("RETURN"))
    private void radianceAudit$endNauseaTexture(GuiGraphics graphics, float intensity,
        CallbackInfo ci) {
        AuditHooks.exit("ui.nausea_texture");
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"))
    private void radianceAudit$beginItemActivation(GuiGraphics graphics, float tickDelta,
        CallbackInfo ci) {
        this.radianceAudit$itemActivationScope = this.itemActivationItem != null
            && this.itemActivationTicks > 0;
        if (this.radianceAudit$itemActivationScope) {
            AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.item_activation",
                "item=" + this.itemActivationItem.getItem() + "; ticks="
                    + this.itemActivationTicks + "; tickDelta=" + tickDelta);
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("RETURN"))
    private void radianceAudit$endItemActivation(GuiGraphics graphics, float tickDelta,
        CallbackInfo ci) {
        if (this.radianceAudit$itemActivationScope) {
            AuditHooks.exit("ui.item_activation");
            this.radianceAudit$itemActivationScope = false;
        }
    }

    @Inject(method = "processBlurEffect", at = @At("HEAD"))
    private void radianceAudit$beginMenuBlur(float tickDelta, CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "post.menu_blur", "tickDelta=" + tickDelta);
    }

    @Inject(method = "processBlurEffect", at = @At("RETURN"))
    private void radianceAudit$endMenuBlur(float tickDelta, CallbackInfo ci) {
        AuditHooks.exit("post.menu_blur");
    }

    @Inject(method = "loadEffect", at = @At("HEAD"))
    private void radianceAudit$postEffectRequested(ResourceLocation effect, CallbackInfo ci) {
        AuditHooks.event("SCREEN_EFFECT", "post.entity_effect", "effect=" + effect,
            "PRODUCER_TRIGGERED", "POST_EFFECT_REQUEST");
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void radianceAudit$worldCameraInputs(DeltaTracker ticks, CallbackInfo ci) {
        if (this.minecraft.player == null) return;
        float intensity = this.minecraft.player.spinningEffectIntensity;
        if (intensity > 0.0F || this.minecraft.player.oSpinningEffectIntensity > 0.0F) {
            AuditHooks.continuousEvent("SCREEN_EFFECT", "world.portal_or_nausea_projection",
                "previous=" + this.minecraft.player.oSpinningEffectIntensity
                    + "; current=" + intensity,
                "PRODUCER_TRIGGERED", "WORLD_CAMERA_INPUT");
        }
        if (this.minecraft.getCameraEntity() instanceof LivingEntity living
            && (living.hurtTime > 0 || living.deathTime > 0)) {
            AuditHooks.continuousEvent("SCREEN_EFFECT", "world.hurt_or_death_camera",
                "hurtTime=" + living.hurtTime + "; deathTime=" + living.deathTime,
                "PRODUCER_TRIGGERED", "WORLD_CAMERA_INPUT");
        }
    }
}
