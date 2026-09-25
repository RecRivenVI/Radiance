package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.radiance.bootstrap.BootstrapState;
import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.shader.ShaderRegistry;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.profiling.ProfileResults;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixins {

    @WrapMethod(method = "renderFpsMeter")
    private void radiance$scopeProfilerPie(GuiGraphics graphics, ProfileResults results,
        Operation<Void> original) {
        // Minecraft draws the profiler pie after GameRenderer's GUI scope ends.
        // Flush its deferred text here too; the caller's later flush is empty.
        try (var scope = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.GUI, "profiler_pie")) {
            original.call(graphics, results);
            graphics.flush();
        }
    }

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void beginSimulation(boolean tick, CallbackInfo ci) {
        RendererProxy.streamlineFrameEvent(1);
    }

    @Inject(method = "runTick(Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void beginRenderSubmission(boolean tick, CallbackInfo ci) {
        RendererProxy.streamlineFrameEvent(2);
    }
    @WrapMethod(method = "useAmbientOcclusion")
    private static boolean radiance$ambientOcclusion(Operation<Boolean> original) {
        return com.radiance.client.render.RasterPreviewScope.useAmbientOcclusion(original::call);
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"
        )
    )
    private void initRenderer(int debugVerbosity, boolean debugSync) {
        RadianceClient.initialize();

        long stackSize = 512L * 1024L * 1024L;
        AtomicReference<Throwable> initializationFailure = new AtomicReference<>();
        Runnable initializeRenderer = () -> {
            try {
                RendererProxy.initRenderer(this.window);
                Pipeline.collectNativeModules();
            } catch (Throwable throwable) {
                initializationFailure.set(throwable);
            }
        };

        Thread rendererThread = new Thread(null, initializeRenderer, "Radiance renderer initialization", stackSize);
        rendererThread.start();
        try {
            while (rendererThread.isAlive()) {
                BootstrapState.tickLoading();
                rendererThread.join(10L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Radiance renderer initialization was interrupted", exception);
        }

        if (initializationFailure.get() != null) {
            throw new RuntimeException("Radiance renderer initialization failed", initializationFailure.get());
        }

        var warmupProgress = StartupNotificationManager.prependProgressBar(
                "Preparing current render pipeline", 1);
        BootstrapState.tickLoading();
        try {
            Pipeline.loadPipeline();
            Pipeline.build();
            RendererProxy.warmupCurrentPipeline();
            warmupProgress.increment();
        } finally {
            warmupProgress.complete();
        }
        BootstrapState.tickLoading();
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(
            value = "FIELD",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;width:I",
            opcode = Opcodes.GETFIELD
        )
    )
    private int useWindowWidth(RenderTarget target) {
        return this.window.getWidth();
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(
            value = "FIELD",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;height:I",
            opcode = Opcodes.GETFIELD
        )
    )
    private int useWindowHeight(RenderTarget target) {
        return this.window.getHeight();
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;resourceManager:Lnet/minecraft/server/packs/resources/ReloadableResourceManager;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        )
    )
    private void registerAuxiliaryTextureReloader(GameConfig config, CallbackInfo ci) {
        this.resourceManager.registerReloadListener(new AuxiliaryTextureReloader());
    }

    @Redirect(
        method = "runTick(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V"
        )
    )
    private void presentNativeFrame(RenderTarget target) {
        RendererProxy.submitCommandAndPresent();
        RendererProxy.acquireContext();
    }

    @Redirect(
        method = "runTick(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"
        )
    )
    private void skipMainTargetBlit(RenderTarget target, int width, int height) {
    }

    @Redirect(
        method = "runTick(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"
        )
    )
    private void disableVanillaFpsLimit(int fps) {
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void saveOptions(CallbackInfo ci) {
        ShaderRegistry.saveWarmupHistory();
        Options.overwriteConfig();
    }

    @Inject(method = "close()V", at = @At("TAIL"))
    private void closeNativeRenderer(CallbackInfo ci) {
        com.radiance.client.vertex.RigidModelCapture.invalidate();
        RendererProxy.close();
    }

    @Inject(
        method = "crash(Lnet/minecraft/client/Minecraft;Ljava/io/File;Lnet/minecraft/CrashReport;)V",
        at = @At("HEAD")
    )
    private static void closeNativeRendererBeforeFatalExit(Minecraft client, File gameDirectory,
        CrashReport report, CallbackInfo ci) {
        RendererProxy.closeAfterFatalExit();
    }

    @Redirect(
        method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"
        )
    )
    private void skipRenderDuringDisconnect(Minecraft instance, boolean tick) {
    }

    @Inject(
        method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At("HEAD")
    )
    private void resetBuiltChunkCount(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("TAIL"))
    private void releaseLocalModelRecipes(Screen screen, boolean transferring, CallbackInfo ci) {
        // Java recipes are no longer queued; native instances own their separate in-flight resources.
        com.radiance.client.vertex.RigidModelCapture.invalidate();
    }
}
