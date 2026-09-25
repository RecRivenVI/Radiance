package com.radiance.audit.mixin;

import com.mojang.logging.LogUtils;
import com.radiance.audit.AuditHooks;
import com.radiance.audit.ChunkBuildCounter;
import com.radiance.audit.ChunkHoleTracker;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
public abstract class MinecraftAuditMixin {
    @Unique
    private static final Logger RADIANCE_AUDIT_LOGGER = LogUtils.getLogger();
    @Unique
    private static final boolean RADIANCE_AUDIT_AUTO_RELOAD =
        "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_AUDIT_AUTO_RELOAD_CHUNKS"));
    @Unique
    private static final boolean RADIANCE_AUDIT_GPU_PROFILE =
        "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_AUDIT_GPU_PROFILE"));
    @Unique
    private static final boolean RADIANCE_AUDIT_VISUAL_CAPTURE =
        "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_AUDIT_VISUAL_CAPTURE"));
    @Unique
    private static final long[] RADIANCE_AUDIT_CAPTURE_SECONDS = {15L, 17L, 19L, 21L, 27L, 30L, 35L, 40L};
    @Unique
    private static final float[] RADIANCE_AUDIT_CAPTURE_YAW_OFFSETS = {0.0F, 90.0F, 180.0F, 270.0F,
        0.0F, 90.0F, 180.0F, 270.0F};
    @Unique
    private static final String[] RADIANCE_AUDIT_CAPTURE_NAMES = {
        "radiance-audit-before-0.png", "radiance-audit-before-90.png",
        "radiance-audit-before-180.png", "radiance-audit-before-270.png",
        "radiance-audit-after-2s-0.png", "radiance-audit-after-5s-90.png",
        "radiance-audit-after-10s-180.png", "radiance-audit-after-15s-270.png"
    };
    @Unique
    private long radianceAudit$worldReadyNanos;
    @Unique
    private boolean radianceAudit$reloadTriggered;
    @Unique
    private long radianceAudit$frameStartNanos;
    @Unique
    private int radianceAudit$gpuProfileSequence;
    @Unique
    private int radianceAudit$gpuProfileSamples;
    @Unique
    private long radianceAudit$nextGpuProfileNanos;
    @Unique
    private int radianceAudit$captureIndex;
    @Unique
    private float radianceAudit$baseYaw;
    @Unique
    private String radianceAudit$pendingCapture;

    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void radianceAudit$beginFrameTiming(boolean renderLevel, CallbackInfo ci) {
        if (net.neoforged.fml.ModList.get().isLoaded("radiance")) {
            com.radiance.audit.NativeDiagnostics.initialize();
            com.radiance.audit.NativeDiagnostics.poll();
            com.radiance.audit.LifecycleAcceptance.initialize();
            com.radiance.audit.LifecycleAcceptance.poll();
        }
        radianceAudit$frameStartNanos = AuditHooks.accepts("CHUNK_PERF")
            ? System.nanoTime() : 0L;
    }

    @Inject(method = "runTick(Z)V", at = @At("RETURN"))
    private void radianceAudit$reportChunkProgress(boolean renderLevel, CallbackInfo ci) {
        if (radianceAudit$frameStartNanos != 0L) {
            ChunkBuildCounter.INSTANCE.recordPerformance("CLIENT_FRAME_CPU_NS",
                System.nanoTime() - radianceAudit$frameStartNanos);
            ChunkBuildCounter.INSTANCE.recordPerformance("CLIENT_FRAME_COUNT", 1);
        }
        if (AuditHooks.accepts("CHUNK_BUILD")) {
            ChunkBuildCounter.INSTANCE.maybeReport();
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        if (com.radiance.audit.ExperimentAccess.permitted()) {
        com.radiance.audit.SmokeProbe.poll(minecraft);
        com.radiance.audit.DiagramParityProbe.poll(minecraft);
        if (net.neoforged.fml.ModList.get().isLoaded("radiance")) {
        com.radiance.audit.RigidModelLifecycleProbe.poll(minecraft);
        com.radiance.audit.PartModelLifecycleProbe.poll(minecraft);
        com.radiance.audit.CatnipRasterProbe.poll(minecraft);
        com.radiance.audit.RasterSimulatedProbe.poll(minecraft);
        com.radiance.audit.ChunkLatencyProbe.poll(minecraft);
        com.radiance.audit.UnifiedAcceptanceProbe.poll(minecraft);
        }
        }
        if (AuditHooks.accepts("CHUNK_HOLE")
            && minecraft.level != null && minecraft.levelRenderer != null) {
            ChunkHoleTracker.INSTANCE.sample(minecraft.level,
                ((LevelRendererAccessor) minecraft.levelRenderer).radianceAudit$getViewArea());
        }
        if (minecraft.player == null || minecraft.levelRenderer == null) {
            radianceAudit$worldReadyNanos = 0L;
            radianceAudit$reloadTriggered = false;
            radianceAudit$captureIndex = 0;
            radianceAudit$pendingCapture = null;
            radianceAudit$gpuProfileSequence = 0;
            radianceAudit$gpuProfileSamples = 0;
            radianceAudit$nextGpuProfileNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (radianceAudit$worldReadyNanos == 0L) {
            radianceAudit$worldReadyNanos = now;
            radianceAudit$baseYaw = 0.0F;
            if (RADIANCE_AUDIT_VISUAL_CAPTURE) {
                minecraft.player.setXRot(10.0F);
                minecraft.player.xRotO = 10.0F;
                RADIANCE_AUDIT_LOGGER.info("Preparing fixed ground camera for visual chunk audit");
            }
            return;
        }

        if (radianceAudit$pendingCapture != null) {
            String captureName = radianceAudit$pendingCapture;
            radianceAudit$pendingCapture = null;
            Screenshot.grab(minecraft.gameDirectory, captureName, minecraft.getMainRenderTarget(),
                result -> RADIANCE_AUDIT_LOGGER.info("Visual audit capture {}: {}", captureName, result.getString()));
        }

        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(now - radianceAudit$worldReadyNanos);
        radianceAudit$sampleStableWorldGpuTime(now, elapsedSeconds);
        if (RADIANCE_AUDIT_VISUAL_CAPTURE
            && radianceAudit$pendingCapture == null
            && radianceAudit$captureIndex < RADIANCE_AUDIT_CAPTURE_SECONDS.length
            && elapsedSeconds >= RADIANCE_AUDIT_CAPTURE_SECONDS[radianceAudit$captureIndex]) {
            float captureYaw = radianceAudit$baseYaw
                + RADIANCE_AUDIT_CAPTURE_YAW_OFFSETS[radianceAudit$captureIndex];
            minecraft.player.setYRot(captureYaw);
            minecraft.player.yRotO = captureYaw;
            minecraft.player.setXRot(10.0F);
            minecraft.player.xRotO = 10.0F;
            radianceAudit$pendingCapture = RADIANCE_AUDIT_CAPTURE_NAMES[radianceAudit$captureIndex];
            RADIANCE_AUDIT_LOGGER.info("Scheduling visual audit capture {} at {} seconds",
                radianceAudit$pendingCapture, elapsedSeconds);
            radianceAudit$captureIndex++;
        }

        if (RADIANCE_AUDIT_AUTO_RELOAD && !radianceAudit$reloadTriggered
            && now - radianceAudit$worldReadyNanos >= TimeUnit.SECONDS.toNanos(25)) {
            radianceAudit$reloadTriggered = true;
            RADIANCE_AUDIT_LOGGER.info("Triggering diagnostic chunk reload (LevelRenderer.allChanged)");
            minecraft.levelRenderer.allChanged();
        }
    }

    @Unique
    private void radianceAudit$sampleStableWorldGpuTime(long now, long elapsedSeconds) {
        if (!RADIANCE_AUDIT_GPU_PROFILE || elapsedSeconds < 30L || radianceAudit$gpuProfileSamples >= 8) {
            return;
        }
        try {
            if (radianceAudit$gpuProfileSequence != 0) {
                if (!RendererProxy.isGpuProfileReady(radianceAudit$gpuProfileSequence)) {
                    return;
                }
                long gpuTimeNs = RendererProxy.gpuProfileTimeNs(radianceAudit$gpuProfileSequence);
                ChunkBuildCounter.INSTANCE.recordPerformance("WHOLE_FRAME_GPU_NS", gpuTimeNs);
                ChunkBuildCounter.INSTANCE.recordPerformance("WHOLE_FRAME_GPU_COUNT", 1);
                radianceAudit$gpuProfileSamples++;
                RADIANCE_AUDIT_LOGGER.info("Stable-world GPU profile sample {}/8: {} ns",
                    radianceAudit$gpuProfileSamples, gpuTimeNs);
                radianceAudit$gpuProfileSequence = 0;
                radianceAudit$nextGpuProfileNanos = now + TimeUnit.SECONDS.toNanos(2);
                return;
            }
            if (radianceAudit$nextGpuProfileNanos == 0L) {
                radianceAudit$nextGpuProfileNanos = now;
            }
            if (now >= radianceAudit$nextGpuProfileNanos) {
                radianceAudit$gpuProfileSequence = RendererProxy.beginGpuProfile();
                if (radianceAudit$gpuProfileSequence == 0) {
                    radianceAudit$nextGpuProfileNanos = now + TimeUnit.SECONDS.toNanos(2);
                }
            }
        } catch (RuntimeException | LinkageError error) {
            RADIANCE_AUDIT_LOGGER.warn("Stable-world GPU profile sample failed", error);
            radianceAudit$gpuProfileSequence = 0;
            radianceAudit$nextGpuProfileNanos = now + TimeUnit.SECONDS.toNanos(2);
        }
    }
}
