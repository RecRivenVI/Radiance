package com.radiance.audit.mixin;

import com.radiance.audit.AuditHooks;
import com.radiance.audit.ChunkBuildCounter;
import com.radiance.audit.ChunkHoleTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public abstract class LevelRendererAuditMixin {
    private static final String SOURCE = "LevelRenderer.renderLevel";

    @Shadow
    private ClientLevel level;

    @Shadow
    private ViewArea viewArea;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void radianceAudit$begin(DeltaTracker deltaTracker, boolean renderBlockOutline,
        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
        Matrix4f viewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        AuditHooks.enter("TAKEOVER_ROOT", SOURCE, "method entered");
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void radianceAudit$end(DeltaTracker deltaTracker, boolean renderBlockOutline,
        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
        Matrix4f viewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        AuditHooks.exit(SOURCE);
        if (AuditHooks.accepts("CHUNK_BUILD")) {
            ChunkBuildCounter.INSTANCE.maybeReport();
        }
        if (AuditHooks.accepts("CHUNK_HOLE")) {
            ChunkHoleTracker.INSTANCE.sample(level, viewArea);
        }
    }
}
