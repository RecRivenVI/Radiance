package com.radiance.audit.mixin;

import com.radiance.audit.AuditHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 2000)
public abstract class GuiScreenEffectAuditMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private boolean radianceAudit$sleepScope;

    @Inject(method = "renderVignette", at = @At("HEAD"))
    private void radianceAudit$beginVignette(GuiGraphics graphics, Entity camera,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.vignette",
            "camera=" + (camera == null ? "null" : camera.getType().toString()));
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void radianceAudit$endVignette(GuiGraphics graphics, Entity camera,
        CallbackInfo ci) {
        AuditHooks.exit("ui.vignette");
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"))
    private void radianceAudit$beginScope(GuiGraphics graphics, float scale, CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.scope", "scale=" + scale);
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("RETURN"))
    private void radianceAudit$endScope(GuiGraphics graphics, float scale, CallbackInfo ci) {
        AuditHooks.exit("ui.scope");
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"))
    private void radianceAudit$beginTextureOverlay(GuiGraphics graphics,
        ResourceLocation texture, float alpha, CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.texture_overlay",
            "texture=" + texture + "; alpha=" + alpha);
    }

    @Inject(method = "renderTextureOverlay", at = @At("RETURN"))
    private void radianceAudit$endTextureOverlay(GuiGraphics graphics,
        ResourceLocation texture, float alpha, CallbackInfo ci) {
        AuditHooks.exit("ui.texture_overlay");
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"))
    private void radianceAudit$beginPortal(GuiGraphics graphics, float intensity,
        CallbackInfo ci) {
        AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.portal_texture",
            "intensity=" + intensity);
    }

    @Inject(method = "renderPortalOverlay", at = @At("RETURN"))
    private void radianceAudit$endPortal(GuiGraphics graphics, float intensity,
        CallbackInfo ci) {
        AuditHooks.exit("ui.portal_texture");
    }

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"))
    private void radianceAudit$beginSleep(GuiGraphics graphics, DeltaTracker ticks,
        CallbackInfo ci) {
        this.radianceAudit$sleepScope = this.minecraft.player != null
            && this.minecraft.player.getSleepTimer() > 0;
        if (this.radianceAudit$sleepScope) {
            AuditHooks.enterContinuous("SCREEN_EFFECT", "ui.sleep",
                "sleepTimer=" + this.minecraft.player.getSleepTimer());
        }
    }

    @Inject(method = "renderSleepOverlay", at = @At("RETURN"))
    private void radianceAudit$endSleep(GuiGraphics graphics, DeltaTracker ticks,
        CallbackInfo ci) {
        if (this.radianceAudit$sleepScope) {
            AuditHooks.exit("ui.sleep");
            this.radianceAudit$sleepScope = false;
        }
    }
}
