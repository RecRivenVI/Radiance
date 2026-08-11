package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.neoforge.DimensionSpecialEffectsCompatibility;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.api.render.SourceHooks;
import com.radiance.api.render.WorldScene;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.CloudProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.UnboundedFrustum;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Tuple;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixins {

    private static final ResourceLocation RADIANCE_SUN_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation RADIANCE_MOON_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    @Shadow private ClientLevel level;

    @Final @Shadow private Minecraft minecraft;

    @Final @Shadow private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow @Final private RenderBuffers renderBuffers;

    @Final @Shadow private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow private ViewArea viewArea;

    // Leaving a world releases the vanilla section buffers; the native scene (chunk slots, build
    // batches, rigid-model caches, the last frames' TLAS) would otherwise stay resident until the
    // next
    // world or shutdown, because no world frame runs on the title screen.
    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("TAIL"))
    private void radiance$releaseSceneOnLeave(ClientLevel level, CallbackInfo ci) {
        if (level == null) {
            WorldScene.invalidateWorld();
            ChunkProxy.releaseScene();
        }
    }

    @Shadow private Frustum cullingFrustum;

    @Final @Shadow private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Final @Shadow private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Final @Shadow private Set<BlockEntity> globalBlockEntities;

    @Shadow private int ticks;

    @Shadow private int renderedEntities;

    @Shadow private int culledEntities;

    @Shadow
    private void setupRender(
            Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator) {
        throw new AssertionError();
    }

    @Shadow
    private void renderDebug(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        throw new AssertionError();
    }

    @Inject(method = "initOutline", at = @At("HEAD"), cancellable = true)
    private void radiance$disableVanillaEntityOutline(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "initTransparency", at = @At("HEAD"), cancellable = true)
    private void radiance$disableVanillaTransparency(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void radiance$renderLevel(
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f effectedRotationMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        if (this.level == null || this.viewArea == null || this.cullingFrustum == null) {
            // Never fall through to vanilla rendering: the active GLFW window has no OpenGL
            // context. A temporarily unavailable world renderer is represented by an empty
            // Vulkan world frame instead.
            ci.cancel();
            return;
        }

        com.radiance.api.render.WorldView.origin(
                camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        com.radiance.api.render.SourceHooks.frameStarted();

        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
        RenderSystem.setShaderGameTime(this.level.getGameTime(), tickDelta);
        this.blockEntityRenderDispatcher.prepare(this.level, camera, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, camera, this.minecraft.crosshairPickEntity);

        this.level.pollLightUpdates();
        this.level.getChunkSource().getLightEngine().runLightUpdates();

        Vec3 cameraPosition = camera.getPosition();
        double x = cameraPosition.x();
        double y = cameraPosition.y();
        double z = cameraPosition.z();
        try (WorldScene.FrameLease worldFrame =
                WorldScene.joinOrBeginFrame(this.level, cameraPosition)) {

            this.setupRender(
                    camera,
                    this.cullingFrustum,
                    false,
                    this.minecraft.player != null && this.minecraft.player.isSpectator());

            SourceHooks.update(this.level, camera, tickDelta);
            com.radiance.client.render.SectionRasterStorage.drain();

            Matrix4f viewMatrix =
                    new Matrix4f(((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
            Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);
            com.radiance.api.render.SourceIntegration.Frame sourceFrame =
                    com.radiance.api.render.SourceHooks.begin(
                            new com.radiance.api.render.SourceIntegration.FrameInput(
                                    (LevelRenderer) (Object) this,
                                    this.level,
                                    this.renderBuffers,
                                    effectedRotationMatrix,
                                    projectionMatrix,
                                    camera,
                                    tickCounter));

            boolean worldFog = false;
            FogRenderer.setupNoFog();

            TextureManager textureManager = this.minecraft.getTextureManager();
            OverlayTexture overlayTexture = gameRenderer.overlayTexture();
            int overlayTextureId =
                    ((IOverlayTextureExt) overlayTexture).radiance$getTexture().getId();
            int endSkyTextureId =
                    textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION).getId();
            int endPortalTextureId =
                    textureManager.getTexture(TheEndPortalRenderer.END_PORTAL_LOCATION).getId();
            ILightMapManagerExt lightMap = (ILightMapManagerExt) gameRenderer.lightTexture();
            com.radiance.adapter.vanilla.VanillaViewAdapter.describe(
                            camera,
                            viewMatrix,
                            effectedViewMatrix,
                            projectionMatrix,
                            overlayTextureId,
                            this.level,
                            endSkyTextureId,
                            endPortalTextureId,
                            lightMap.radiance$getTextureId())
                    .submit();

            float skyAngle = this.level.getTimeOfDay(tickDelta);
            Vec3 skyColor = this.level.getSkyColor(cameraPosition, tickDelta);
            DimensionSpecialEffects dimensionEffects = this.level.effects();
            float[] sunriseColor = dimensionEffects.getSunriseColor(skyAngle, tickDelta);

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(skyAngle * 360.0F));
            Vector3f sunDirection =
                    poseStack
                            .last()
                            .pose()
                            .transformPosition(0.0F, 1.0F, 0.0F, new Vector3f())
                            .normalize();

            int sunTextureId = textureManager.getTexture(RADIANCE_SUN_LOCATION).getId();
            int moonTextureId = textureManager.getTexture(RADIANCE_MOON_LOCATION).getId();
            DimensionSpecialEffectsCompatibility.InvocationResult<Boolean> customSky =
                    DimensionSpecialEffectsCompatibility.invoke(
                            dimensionEffects,
                            "renderSky",
                            () ->
                                    dimensionEffects.renderSky(
                                            this.level,
                                            this.ticks,
                                            tickDelta,
                                            effectedRotationMatrix,
                                            camera,
                                            projectionMatrix,
                                            worldFog,
                                            FogRenderer::setupNoFog));
            boolean customSkyRendered = customSky.value() && customSky.acceptedWorldMeshes() > 0;
            FogRenderer.setupNoFog();
            int cameraSubmersionType = radiance$cameraSubmersionType(camera);
            BufferProxy.updateSkyUniform(
                    (float) skyColor.x,
                    (float) skyColor.y,
                    (float) skyColor.z,
                    sunriseColor == null ? 0.0F : sunriseColor[0],
                    sunriseColor == null ? 0.0F : sunriseColor[1],
                    sunriseColor == null ? 0.0F : sunriseColor[2],
                    sunriseColor == null ? 0.0F : sunriseColor[3],
                    sunDirection,
                    customSkyRendered
                            ? DimensionSpecialEffects.SkyType.NONE.ordinal()
                            : dimensionEffects.skyType().ordinal(),
                    sunriseColor != null,
                    this.level.getThunderLevel(tickDelta),
                    cameraSubmersionType,
                    this.level.getMoonPhase(),
                    this.level.getRainLevel(tickDelta),
                    this.level.getStarBrightness(tickDelta),
                    sunTextureId,
                    moonTextureId);

            BufferProxy.updateMapping();

            LevelRenderer levelRenderer = (LevelRenderer) (Object) this;
            ClientHooks.dispatchRenderStage(
                    RenderLevelStageEvent.Stage.AFTER_SKY,
                    levelRenderer,
                    radiance$levelPoseStack(cameraPosition),
                    effectedRotationMatrix,
                    projectionMatrix,
                    this.ticks,
                    camera,
                    UnboundedFrustum.INSTANCE);

            ChunkProxy.setStorage(this.viewArea);
            ChunkProxy.rebuild(camera);
            SourceHooks.blocks(this.level, tickDelta);
            for (RenderType renderType : RenderType.chunkBufferLayers()) {
                ClientHooks.dispatchRenderStage(
                        renderType,
                        levelRenderer,
                        effectedRotationMatrix,
                        projectionMatrix,
                        this.ticks,
                        camera,
                        UnboundedFrustum.INSTANCE);
            }

            List<Entity> entities = new ArrayList<>();
            this.renderedEntities = 0;
            this.culledEntities = 0;
            for (Entity entity : this.level.entitiesForRendering()) {
                boolean flywheelVisual = SourceHooks.ownsEntity(this.level, entity);
                if (flywheelVisual) {
                    this.culledEntities++;
                    continue;
                }
                this.renderedEntities++;
                entities.add(entity);
            }

            EntityProxy.queueEntitiesBuild(
                    camera,
                    entities,
                    this.entityRenderDispatcher,
                    tickCounter,
                    !gameRenderer.isPanoramicMode() && this.minecraft.player != null);
            sourceFrame.afterEntities();
            ClientHooks.dispatchRenderStage(
                    RenderLevelStageEvent.Stage.AFTER_ENTITIES,
                    levelRenderer,
                    radiance$levelPoseStack(cameraPosition),
                    effectedRotationMatrix,
                    projectionMatrix,
                    this.ticks,
                    camera,
                    UnboundedFrustum.INSTANCE);

            Tuple<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList>
                    blockEntities =
                            EntityProxy.queueBlockEntitiesRebuild(
                                    this.viewArea,
                                    this.globalBlockEntities,
                                    this.destructionProgress,
                                    this.blockEntityRenderDispatcher,
                                    tickDelta);
            SourceHooks.blockEntities(
                    this.level, camera, this.blockEntityRenderDispatcher, tickDelta);
            sourceFrame.beforeCrumbling(this.destructionProgress);
            EntityProxy.queueCrumblingRebuild(
                    camera,
                    this.destructionProgress,
                    this.minecraft.getBlockRenderer(),
                    this.level,
                    blockEntities.getA(),
                    blockEntities.getB());
            ClientHooks.dispatchRenderStage(
                    RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES,
                    levelRenderer,
                    radiance$levelPoseStack(cameraPosition),
                    effectedRotationMatrix,
                    projectionMatrix,
                    this.ticks,
                    camera,
                    UnboundedFrustum.INSTANCE);

            if (renderBlockOutline) {
                EntityProxy.queueTargetBlockOutlineRebuild(
                        levelRenderer, camera, this.level, tickCounter);
            }

            EntityProxy.queueParticleRebuild(camera, tickDelta);
            ClientHooks.dispatchRenderStage(
                    RenderLevelStageEvent.Stage.AFTER_PARTICLES,
                    levelRenderer,
                    radiance$levelPoseStack(cameraPosition),
                    effectedRotationMatrix,
                    projectionMatrix,
                    this.ticks,
                    camera,
                    UnboundedFrustum.INSTANCE);

            com.radiance.api.render.SourceHooks.worldEffects(
                    camera, gameRenderer, effectedRotationMatrix, projectionMatrix);
            EntityProxy.queueDebugGeometry(this.minecraft.debugRenderer, camera);
            StorageVertexConsumerProvider levelDebugStorage =
                    new StorageVertexConsumerProvider(0, 0.0F);
            this.renderDebug(new PoseStack(), levelDebugStorage, camera);
            EntityProxy.queueLevelDebugGeometry(levelDebugStorage);
            if (this.minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
                SourceHooks.debug(this.level, camera, tickDelta);
            }

            if (this.minecraft.options.getCloudsType() != CloudStatus.OFF) {
                PoseStack cloudPoseStack = new PoseStack();
                DimensionSpecialEffectsCompatibility.InvocationResult<Boolean> customCloudResult =
                        DimensionSpecialEffectsCompatibility.invoke(
                                dimensionEffects,
                                "renderClouds",
                                () ->
                                        dimensionEffects.renderClouds(
                                                this.level,
                                                this.ticks,
                                                tickDelta,
                                                cloudPoseStack,
                                                x,
                                                y,
                                                z,
                                                projectionMatrix,
                                                effectedRotationMatrix));
                boolean customClouds =
                        customCloudResult.value() && customCloudResult.acceptedWorldMeshes() > 0;
                if (!customClouds) {
                    CloudProxy.queue(this.level, camera, this.ticks, tickDelta);
                }
            } else {
                CloudProxy.close();
            }

            DimensionSpecialEffectsCompatibility.InvocationResult<Boolean> customWeatherResult =
                    DimensionSpecialEffectsCompatibility.invoke(
                            dimensionEffects,
                            "renderSnowAndRain",
                            () ->
                                    dimensionEffects.renderSnowAndRain(
                                            this.level,
                                            this.ticks,
                                            tickDelta,
                                            lightTexture,
                                            x,
                                            y,
                                            z));
            boolean customWeather =
                    customWeatherResult.value() && customWeatherResult.acceptedWorldMeshes() > 0;
            EntityProxy.queueWeatherBuild(
                    this.level, camera, this.ticks, tickDelta, !customWeather);
            ClientHooks.dispatchRenderStage(
                    RenderLevelStageEvent.Stage.AFTER_WEATHER,
                    levelRenderer,
                    radiance$levelPoseStack(cameraPosition),
                    effectedRotationMatrix,
                    projectionMatrix,
                    this.ticks,
                    camera,
                    UnboundedFrustum.INSTANCE);

            FogRenderer.setupNoFog();
            worldFrame.commit();
            RenderAuditBridge.transition(
                    0L,
                    "REPLACED",
                    "RADIANCE_WORLD",
                    "LevelRenderer.renderLevel was fully handled by the Radiance takeover",
                    true);
            ci.cancel();
        }
    }

    /**
     * Camera.FogType only recognizes vanilla water, lava, and powder snow. NeoForge nevertheless
     * applies fog hooks for any FluidState covering the camera, so reserve the next enum value for
     * a third-party fluid and carry that fact into the native renderer.
     */
    private int radiance$cameraSubmersionType(Camera camera) {
        FogType vanillaType = camera.getFluidInCamera();
        if (vanillaType != FogType.NONE) {
            return vanillaType.ordinal();
        }
        BlockPos cameraBlock = camera.getBlockPosition();
        FluidState fluid = this.level.getFluidState(cameraBlock);
        boolean insideFluid =
                !fluid.isEmpty()
                        && camera.getPosition().y
                                < cameraBlock.getY() + fluid.getHeight(this.level, cameraBlock);
        return insideFluid ? FogType.values().length : FogType.NONE.ordinal();
    }

    private static PoseStack radiance$levelPoseStack(Vec3 cameraPosition) {
        return new PoseStack();
    }

    @Inject(
            method =
                    "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL"))
    private void radiance$trackOutgoingBlock(
            BlockPos position, BlockState previous, BlockState next, CallbackInfo ci) {
        SourceHooks.invalidateSectionShape(position);
        if (!previous.isAir() && previous != next) ChunkProxy.blockContentChanged(position);
    }

    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void radiance$trackOutgoingUpdate(
            BlockGetter world,
            BlockPos position,
            BlockState previous,
            BlockState next,
            int flags,
            CallbackInfo ci) {
        SourceHooks.invalidateSectionShape(position);
        if (!previous.isAir() && previous != next) ChunkProxy.blockContentChanged(position);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void radiance$closeCloudCache(CallbackInfo ci) {
        WorldScene.invalidateWorld();
        CloudProxy.close();
    }

    @Inject(
            method = "setupRender",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;setCamera(Lnet/minecraft/world/phys/Vec3;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void radiance$finishTerrainSetup(
            Camera camera,
            Frustum frustum,
            boolean capturedFrustum,
            boolean spectator,
            CallbackInfo ci) {
        if (!capturedFrustum) {
            SourceHooks.prepare(this.level, camera, UnboundedFrustum.INSTANCE, spectator);
        }
        ProfilerFiller profiler = this.level.getProfiler();
        profiler.pop();
        ci.cancel();
    }

    @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
    private void radiance$isSectionCompiled(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.viewArea == null) {
            cir.setReturnValue(false);
            return;
        }
        SectionRenderDispatcher.RenderSection section = this.viewArea.getRenderSectionAt(pos);
        if (section == null) {
            cir.setReturnValue(false);
        } else if (section.compiled.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (section.compiled.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(section));
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void radiance$wakeChunkBuilds(ChunkPos pos, CallbackInfo ci) {
        ChunkProxy.onChunkLoaded(pos);
    }
}
