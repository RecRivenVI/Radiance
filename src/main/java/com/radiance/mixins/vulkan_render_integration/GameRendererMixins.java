package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.RadianceClient;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.render.ScreenEffectCoordinator;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.client.render.AfterWorldRender;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    public ItemInHandRenderer itemInHandRenderer;
    @Final
    @Shadow
    private Minecraft minecraft;
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    @Shadow
    @Final
    private Camera mainCamera;
    @Unique
    private Matrix4f viewMatrix;
    @Unique
    private ScreenEffectCoordinator.EntityPostEffect radiance$entityPostEffect =
        ScreenEffectCoordinator.EntityPostEffect.NONE;
    @Unique
    private WorldMeshSink.FrameToken radiance$worldFrame;

    @Shadow
    private boolean effectActive;

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fovDegrees);

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Shadow
    private boolean renderHand;

    @Shadow
    private boolean panoramicMode;

    @Unique
    private RenderCaptureContract.ScopeToken radiance$guiScope;

    @Inject(method = "loadBlurEffect", at = @At("HEAD"), cancellable = true)
    private void radiance$disableVanillaBlur(
        net.minecraft.server.packs.resources.ResourceProvider resourceProvider,
        CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "loadEffect", at = @At("HEAD"), cancellable = true)
    private void radiance$disableVanillaPostEffect(
        ResourceLocation effect,
        CallbackInfo ci) {
        this.radiance$entityPostEffect = ScreenEffectCoordinator.EntityPostEffect.resolve(effect);
        this.effectActive = this.radiance$entityPostEffect.supported();
        if (!this.effectActive) {
            RadianceClient.LOGGER.warn(
                "Unsupported post effect {} was not submitted to the Vulkan backend", effect);
        }
        ci.cancel();
    }

    @Inject(method = "shutdownEffect", at = @At("HEAD"))
    private void radiance$clearEntityPostEffect(CallbackInfo ci) {
        this.radiance$entityPostEffect = ScreenEffectCoordinator.EntityPostEffect.NONE;
    }

    @Inject(method = "checkEntityPostEffect", at = @At("HEAD"))
    private void radiance$clearEntityPostEffectBeforeSelection(Entity entity, CallbackInfo ci) {
        this.radiance$entityPostEffect = ScreenEffectCoordinator.EntityPostEffect.NONE;
    }

    @Inject(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
            shift = At.Shift.AFTER))
    private void radiance$applyEntityPostEffect(DeltaTracker tickCounter, boolean tick,
        CallbackInfo ci) {
        if (this.effectActive) {
            ScreenEffectCoordinator.applyEntityPostEffect(this.radiance$entityPostEffect);
        }
    }

    @Inject(method = "processBlurEffect", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderBlur(float tickDelta, CallbackInfo ci) {
        ScreenEffectCoordinator.applyMenuBlur(this.minecraft);
        ci.cancel();
    }

    @Redirect(method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", remap = false))
    public Matrix4f cancelPTimesB(Matrix4f instance, Matrix4fc right) {
        return instance;
    }

    @Redirect(method = "renderLevel",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;"
                    + "ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
                    + "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(LevelRenderer instance,
        DeltaTracker tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        @Local PoseStack matrixStack) {
        Matrix4f
            B =
            new Matrix4f(matrixStack.last()
                .pose());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(B.mul(viewMatrix));
        // Camera.setup has completed here. Keep the frame alive through NeoForge's
        // AFTER_LEVEL dispatch and hand capture, which occur after LevelRenderer returns.
        this.radiance$worldFrame = WorldMeshSink.beginFrame(this.minecraft.level,
            camera.getPosition());
        AfterWorldRender.begin();
        instance.renderLevel(tickCounter, renderBlockOutline, camera, gameRenderer, lightTexture,
            viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At(value = "TAIL"))
    public void radiance$finishWorld(DeltaTracker renderTickCounter, CallbackInfo ci) {
        if (this.radiance$worldFrame != null) {
            this.radiance$worldFrame.commit();
            this.radiance$worldFrame.close();
            this.radiance$worldFrame = null;
        }
        // One hook makes build-before-fuse ordering explicit.
        EntityProxy.build();
        WorldMeshSink.flushNativeAuditOutcomes();
        RendererProxy.fuseWorld();
        AfterWorldRender.flush();
    }

    @WrapMethod(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
    private void radiance$closeWorldFrame(DeltaTracker tickCounter, Operation<Void> original) {
        if (this.radiance$worldFrame != null) {
            throw new IllegalStateException("GameRenderer world rendering is already active");
        }
        try {
            original.call(tickCounter);
        } finally {
            AfterWorldRender.cancel();
            WorldMeshSink.FrameToken frame = this.radiance$worldFrame;
            this.radiance$worldFrame = null;
            if (frame != null) frame.close();
        }
    }

    @Inject(method = "renderItemInHand", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        double worldFov = this.getFov(camera, tickDelta, true);
        double handFov = this.getFov(camera, tickDelta, false);
        float handProjectionScale =
            (float) (Math.tan(Math.toRadians(worldFov * 0.5F)) /
                Math.tan(Math.toRadians(handFov * 0.5F)));
        // 原版会在渲染手部前切换到手部 FOV。部分物品渲染器会读取当前投影矩阵，
        // 并在后续世界渲染中用它反算第一人称锚点，因此这里也必须保留这项状态。
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
            this.getProjectionMatrix(handFov), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityProxy.queueHandRebuild(renderBuffers, tickDelta, itemInHandRenderer,
            handProjectionScale);
        ci.cancel();
    }

    @Redirect(method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void shouldRenderWorld(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (!RendererProxy.hasAcquiredFrame() && !RendererProxy.acquireContext()) {
            RendererProxy.shouldRenderWorld(false);
            ci.cancel();
            return;
        }
        RendererProxy.shouldRenderWorld(
            !this.minecraft.noRender && minecraft.isGameLoadFinished() && tick
                && minecraft.level != null);
    }

    @Inject(method = "render",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
            ordinal = 0))
    public void beginCameraOverlayAndGuiPhase(DeltaTracker tickCounter,
        boolean tick, CallbackInfo ci) {
        // Vanilla reaches renderItemInHand only while the world frame is being
        // rendered.  The old relocation called ScreenEffectRenderer on title
        // and menu frames as well, where player/level can be null.
        boolean worldReady = tick && this.minecraft.isGameLoadFinished()
            && this.minecraft.level != null;
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
        ScreenEffectCoordinator.renderCameraOverlay(this.minecraft, worldReady,
            this.panoramicMode, this.renderHand,
            this.getProjectionMatrix(this.getFov(this.mainCamera, tickDelta, false)));

        // Everything after vanilla's first GUI depth clear belongs to the
        // orthographic UI phase: confusion, item activation, HUD, overlays,
        // screens, saving indicator, toasts and the final GuiGraphics flush.
        // Scoping only Gui.render/Screen.render would leave several of those
        // authoritative draws incorrectly classified as unscoped.
        if (this.radiance$guiScope != null) {
            this.radiance$guiScope.close();
        }
        this.radiance$guiScope = ScreenEffectCoordinator.beginGuiPhase();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void endGuiPhase(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (this.radiance$guiScope != null) {
            this.radiance$guiScope.close();
            this.radiance$guiScope = null;
        }
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V")
    private void radiance$closeGuiScopeOnExceptionalExit(DeltaTracker tickCounter, boolean tick,
        Operation<Void> original) {
        try {
            original.call(tickCounter, tick);
        } finally {
            if (this.radiance$guiScope != null) {
                this.radiance$guiScope.close();
                this.radiance$guiScope = null;
            }
        }
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

    @Redirect(method = "takeAutoScreenshot",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)"
                    + "Lcom/mojang/blaze3d/platform/NativeImage;"))
    public NativeImage redirectScreenshot(RenderTarget framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }
}
