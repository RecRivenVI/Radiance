package com.radiance.client.proxy.world;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.util.ARGB;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageOutlineVertexConsumerProvider;
import com.radiance.client.vertex.StorageRoutingBufferSource;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.compatibility.flywheel.FlywheelRenderBridge;
import com.radiance.compatibility.sable.SableSubLevelBridge;
import com.radiance.compatibility.simulated.SimulatedEntityCompatibility;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import com.radiance.mixins.compatibility.simulated.LevelRendererInvoker;
import com.radiance.mixins.vulkan_render_integration.accessor.LineStateShardAccessor;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Deque;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import com.radiance.client.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3dc;
import net.neoforged.neoforge.client.ClientHooks;

public class EntityProxy {
    private static final boolean DIRECT_ENTITY_INPUT_ENABLED =
        Boolean.parseBoolean(System.getProperty("radiance.directEntityInput", "true"));

    private static final ThreadLocal<Integer> transformedBlockEntityRenderDepth =
        ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<StorageVertexConsumerProvider> debugLineCapture =
        new ThreadLocal<>();
    /**
     * Line-extrusion frame for the entity currently being processed. Ordinary geometry leaves it
     * unset; rotated owners (Sable sub-levels) set their own orthonormal axes here.
     */
    private static final ThreadLocal<float[]> lineFrameCapture = new ThreadLocal<>();
    /** World-space thickness per unit of the vanilla {@code debugLineStrip(width)} value. */
    private static final float DEBUG_LINE_WORLD_SCALE = 0.0075F;
    /**
     * Fixed world-space equivalent of vanilla's ordinary {@code lines()/lineStrip()} width.
     * Vanilla treats an empty line width as 2.5 pixels at the reference window size. Radiance
     * deliberately keeps a physical world-space width instead of compensating for distance or
     * window size, while preserving that nominal 2.5-to-1 relationship.
     */
    private static final double DEFAULT_LINE_WIDTH_UNITS = 2.5D;
    private static final float DEFAULT_WORLD_LINE_WIDTH =
        (float) (DEFAULT_LINE_WIDTH_UNITS * DEBUG_LINE_WORLD_SCALE);
    /** Identity line-extrusion frame (axisX, axisY, axisZ) used when no rotated owner is set. */
    private static final float[] IDENTITY_LINE_FRAME = {1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F,
        0.0F, 1.0F};
    /*
     * CUSTOM particles are allowed to render nested entities and to call endBatch() on the
     * source they obtain from RenderBuffers.  A stack keeps the outer capture alive across
     * such nested renders; a single ThreadLocal value used to lose the outer destination.
     */
    private static final ThreadLocal<Deque<StorageVertexConsumerProvider>> customParticleCapture =
        ThreadLocal.withInitial(ArrayDeque::new);

    public static MultiBufferSource postTextVertexConsumerProvider;
    private static final ResourceLocation SUN_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/sun.png");
    private static final ResourceLocation MOON_PHASES_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/moon_phases.png");
    private static final ResourceLocation WEATHER_RAIN_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/rain.png");
    private static final ResourceLocation WEATHER_SNOW_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/snow.png");
    private static final ResourceLocation WORLD_BORDER_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/misc/forcefield.png");
    private static final String WEATHER_DEFAULT_CONTENT = "/weather/default";
    private static final String WEATHER_RAIN_CONTENT = "/weather/rain";
    private static final String WEATHER_SNOW_CONTENT = "/weather/snow";
    private static final String PARTICLE_DEFAULT_CONTENT = "/particle/default";
    /**
     * Single content namespace for every ray-traced world debug/overlay line. All lines share one
     * capture, submission, native extrusion and TLAS path; the concrete source (entity hitbox,
     * block outline, chunk border, ...) is appended purely as a diagnostic tag.
     */
    private static final String WORLD_DEBUG_LINE_CONTENT = "radiance:debug/line";
    private static final String TEXT_DEFAULT_CONTENT = "/text/default";
    private static final String NAME_TAG_DEFAULT_CONTENT = "/name_tag/default";
    private static final String PRIORITY_OUTLINE_CONTENT = "radiance:priority/outline";
    private static final OutlinePaletteColor[] OUTLINE_PALETTE = {
        new OutlinePaletteColor(0x000000, "black"),
        new OutlinePaletteColor(0x0000AA, "dark_blue"),
        new OutlinePaletteColor(0x00AA00, "dark_green"),
        new OutlinePaletteColor(0x00AAAA, "dark_aqua"),
        new OutlinePaletteColor(0xAA0000, "dark_red"),
        new OutlinePaletteColor(0xAA00AA, "dark_purple"),
        new OutlinePaletteColor(0xFFAA00, "gold"),
        new OutlinePaletteColor(0xAAAAAA, "gray"),
        new OutlinePaletteColor(0x555555, "dark_gray"),
        new OutlinePaletteColor(0x5555FF, "blue"),
        new OutlinePaletteColor(0x55FF55, "green"),
        new OutlinePaletteColor(0x55FFFF, "aqua"),
        new OutlinePaletteColor(0xFF5555, "red"),
        new OutlinePaletteColor(0xFF55FF, "light_purple"),
        new OutlinePaletteColor(0xFFFF55, "yellow"),
        new OutlinePaletteColor(0xFFFFFF, "")
    };
    private static final int IDENTITY_TEXT_WORLD = 0x1f57a101;
    private static final int IDENTITY_TEXT_PRIORITY = 0x1f57a102;
    private static final int IDENTITY_NAME_TAG_PRIORITY = 0x1f57a203;
    private static final int IDENTITY_OUTLINE = 0x1f57a301;
    private static final float[] WEATHER_SIZE_X = new float[1024];
    private static final float[] WEATHER_SIZE_Z = new float[1024];

    static {
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                float offsetX = x - 16;
                float offsetZ = z - 16;
                float length = Mth.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                int index = z << 5 | x;
                if (length == 0.0F) {
                    WEATHER_SIZE_X[index] = 0.0F;
                    WEATHER_SIZE_Z[index] = 0.0F;
                } else {
                    WEATHER_SIZE_X[index] = -offsetZ / length;
                    WEATHER_SIZE_Z[index] = offsetX / length;
                }
            }
        }
    }

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rayTracingFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            rayTracingFlag.getValue(),
            0,
            -1,
            reflect,
            null,
            false,
            entityRenderDataList);
    }

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rayTracingFlag,
        boolean reflect,
        String contentName,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            rayTracingFlag.getValue(),
            0,
            -1,
            reflect,
            renderLayer -> contentName,
            false,
            entityRenderDataList);
    }

    private static void processEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        int rayTracingFlag,
        int postRenderFlag,
        int prebuiltBLAS,
        boolean reflect,
        Function<RenderType, String> contentNameResolver,
        boolean post,
        EntityRenderDataList entityRenderDataList) {
        Map<RenderType, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
        var rigidDraws = storageVertexConsumerProvider.takeRigidModels();
        for (var draw : rigidDraws) {
            if (post || prebuiltBLAS >= 0 || contentNameResolver != null)
                throw new IllegalStateException("Rigid model reached a non-world consumer");
            draw.locate(entityPosX, entityPosY, entityPosZ, rayTracingFlag, reflect, rigidDraws.size());
            entityRenderDataList.rigidDraws.add(draw);
        }
        EntityRenderData
            entityRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                rayTracingFlag, postRenderFlag, prebuiltBLAS, post);
        entityRenderData.setLineFrame(lineFrameCapture.get());
        EntityRenderData
            waterMaskRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                RayTracingFlags.BOAT_WATER_MASK.getValue(), 0, prebuiltBLAS, post);
        for (Map.Entry<RenderType, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
            RenderType layer = layerBuffer.getKey();
            // Vanilla's blob shadow and the dragon-ray depth prepass compensate for raster
            // limitations. Ray-traced visibility supplies the former, and the colored ray mesh
            // itself supplies depth/occlusion without a duplicate depth-only surface.
            if (layer.name.equals("entity_shadow") || layer.name.equals("dragon_rays_depth")) {
                continue;
            }
            if (layer.mode() != QUADS && layer.mode() != TRIANGLES
                && layer.mode() != TRIANGLE_STRIP
                && layer.mode() != LINE_STRIP && layer.mode() != LINES
                && layer.mode() != DEBUG_LINE_STRIP && layer.mode() != DEBUG_LINES) {
                continue;
            }
            MeshData buffer = null;

            VertexConsumer vertexConsumer = layerBuffer.getValue();
            if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                buffer = bufferBuilder.build();
            } else if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                buffer = pbrVertexConsumer.endNullable();
            }

            if (buffer == null) {
                continue;
            }

            String contentName = contentNameResolver == null
                ? ""
                : Objects.requireNonNullElse(contentNameResolver.apply(layer), "");
            if (layer.name.contains("water_mask")) {
                waterMaskRenderData.add(new EntityRenderLayer(layer, buffer, reflect, contentName));
            } else {
                entityRenderData.add(new EntityRenderLayer(layer, buffer, reflect, contentName));
            }
        }

        if (!entityRenderData.isEmpty()) {
            entityRenderDataList.add(entityRenderData);
        }
        if (!waterMaskRenderData.isEmpty()) {
            entityRenderDataList.add(waterMaskRenderData);
        }

    }

    public static void queueEntitiesBuild(Camera camera,
        List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher,
        DeltaTracker tickCounter,
        boolean canDrawEntityOutlines) {
        Minecraft client = Minecraft.getInstance();
        TickRateManager
            tickManager =
            Objects.requireNonNull(client.level)
                .tickRateManager();

        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        com.radiance.client.vertex.RigidModelCapture.beginFrame(client.level);
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> debugLineStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList debugLineRenderDataList = new EntityRenderDataList();
        for (Entity entity : renderedEntities) {

            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
            entityStorageVertexConsumerProvider.enableRigidModels(entity.getId());
            StorageVertexConsumerProvider cameraRelativeStorageVertexConsumerProvider =
                new StorageVertexConsumerProvider(16384);

            MultiBufferSource baseVertexConsumerProvider = renderLayer ->
                SimulatedEntityCompatibility.isCameraRelativeLayer(renderLayer)
                    ? cameraRelativeStorageVertexConsumerProvider.getBuffer(renderLayer)
                    : entityStorageVertexConsumerProvider.getBuffer(renderLayer);
            boolean cameraEntity = entity == camera.getEntity();
            boolean sleepingCameraEntity = cameraEntity
                && entity instanceof LivingEntity livingEntity
                && livingEntity.isSleeping();
            RayTracingFlags entityWorldVisibility = worldVisibilityFor(cameraEntity,
                camera.isDetached(), sleepingCameraEntity);
            RayTracingFlags entityPriorityOnlyVisibility = priorityOnlyVisibilityFor(cameraEntity,
                camera.isDetached(), sleepingCameraEntity);
            StorageVertexConsumerProvider outlineStorageVertexConsumerProvider = null;
            MultiBufferSource vertexConsumerProvider = baseVertexConsumerProvider;
            boolean customEntityOutline = canDrawEntityOutlines
                && client.player != null
                && entity.hasCustomOutlineRendering(client.player);
            if (canDrawEntityOutlines
                && (client.shouldEntityAppearGlowing(entity) || customEntityOutline)) {
                outlineStorageVertexConsumerProvider = new StorageVertexConsumerProvider(16384, 1.0F);
                StorageOutlineVertexConsumerProvider outlineVertexConsumerProvider =
                    new StorageOutlineVertexConsumerProvider(baseVertexConsumerProvider,
                        outlineStorageVertexConsumerProvider);
                int outlineColor = entity.getTeamColor();
                outlineVertexConsumerProvider.setColor(ARGB.red(outlineColor),
                    ARGB.green(outlineColor), ARGB.blue(outlineColor), 255);
                vertexConsumerProvider = outlineVertexConsumerProvider;
            }

            float tickDelta = tickCounter.getGameTimeDeltaPartialTick(!tickManager.isEntityFrozen(entity));
            double entityPosX = Mth.lerp(tickDelta, entity.xOld,
                entity.getX());
            double entityPosY = Mth.lerp(tickDelta, entity.yOld,
                entity.getY());
            double entityPosZ = Mth.lerp(tickDelta, entity.zOld,
                entity.getZ());
            SableSubLevelBridge.EntityRenderTransform subLevelTransform =
                SableSubLevelBridge.entityRenderTransform(entity, tickDelta,
                    entityPosX, entityPosY, entityPosZ);
            PoseStack matrixStack = new PoseStack();
            if (subLevelTransform != null) {
                entityPosX = subLevelTransform.position().x;
                entityPosY = subLevelTransform.position().y;
                entityPosZ = subLevelTransform.position().z;
                if (subLevelTransform.rotatesModel()) {
                    matrixStack.mulPose(subLevelTransform.orientation());
                }
            }
            // Lines captured from this entity follow the host's rendered frame so a Sable
            // sub-level does not make their square section roll about the line's own axis.
            // Contained entities rotate their model through {@code orientation}; tracking
            // entities are only repositioned by the host, so they keep the world-origin frame.
            float[] lineFrame = subLevelTransform != null && subLevelTransform.rotatesModel()
                ? lineFrameFromOrientation(subLevelTransform.orientation())
                : null;
            int light = entityRenderDispatcher.getPackedLightCoords(entity, tickDelta);
            StorageVertexConsumerProvider debugLineStorage =
                entityRenderDispatcher.shouldRenderHitBoxes()
                    ? new StorageVertexConsumerProvider(4096, 1.0F)
                    : null;

            if (entity instanceof Display.TextDisplay) {
                // Text displays use two vanilla RenderType families. Normal glyphs remain
                // primary-visible world surfaces. See-through glyphs use a priority-only TLAS
                // instance: secondary/shadow rays still see the physical surface, while the
                // dedicated post-world pass supplies their deliberate global visibility.
                StorageVertexConsumerProvider worldTextStorageVertexConsumerProvider =
                    new StorageVertexConsumerProvider(786432);
                StorageVertexConsumerProvider priorityTextStorageVertexConsumerProvider =
                    new StorageVertexConsumerProvider(786432);
                StorageRoutingBufferSource textRoutingBufferSource =
                    StorageRoutingBufferSource.ofConsumers(renderLayer ->
                        isTextSeeThroughLayer(renderLayer)
                            ? priorityTextStorageVertexConsumerProvider.getBuffer(renderLayer)
                            : worldTextStorageVertexConsumerProvider.getBuffer(renderLayer));
                MultiBufferSource textVertexConsumerProvider = textRoutingBufferSource;
                if (outlineStorageVertexConsumerProvider != null) {
                    StorageOutlineVertexConsumerProvider textOutlineVertexConsumerProvider =
                        new StorageOutlineVertexConsumerProvider(textRoutingBufferSource,
                            outlineStorageVertexConsumerProvider);
                    int outlineColor = entity.getTeamColor();
                    textOutlineVertexConsumerProvider.setColor(ARGB.red(outlineColor),
                        ARGB.green(outlineColor), ARGB.blue(outlineColor), 255);
                    textVertexConsumerProvider = textOutlineVertexConsumerProvider;
                }
                if (debugLineStorage != null) {
                    debugLineCapture.set(debugLineStorage);
                }
                try {
                    entityRenderDispatcher.render(entity,
                        0,
                        0,
                        0,
                        entity.getYRot(),
                        tickDelta,
                        matrixStack,
                        textVertexConsumerProvider,
                        light);
                } finally {
                    debugLineCapture.remove();
                }
                textRoutingBufferSource.close();
                if (!worldTextStorageVertexConsumerProvider.getLayers().isEmpty()) {
                    entityStorageVertexConsumerProviders.add(worldTextStorageVertexConsumerProvider);
                    processWorldEntityRenderData(worldTextStorageVertexConsumerProvider,
                        semanticIdentity(entity, IDENTITY_TEXT_WORLD),
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        entityWorldVisibility,
                        false,
                        TEXT_DEFAULT_CONTENT,
                        entityRenderDataList);
                } else {
                    worldTextStorageVertexConsumerProvider.close();
                }
                if (!priorityTextStorageVertexConsumerProvider.getLayers().isEmpty()) {
                    entityStorageVertexConsumerProviders.add(priorityTextStorageVertexConsumerProvider);
                    processWorldEntityRenderData(priorityTextStorageVertexConsumerProvider,
                        semanticIdentity(entity, IDENTITY_TEXT_PRIORITY),
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        entityPriorityOnlyVisibility,
                        false,
                        TEXT_DEFAULT_CONTENT + "/see_through",
                        entityRenderDataList);
                } else {
                    priorityTextStorageVertexConsumerProvider.close();
                }
                if (outlineStorageVertexConsumerProvider != null) {
                    addWorldLines(outlineStorageVertexConsumerProvider,
                        semanticIdentity(entity, IDENTITY_OUTLINE), entityPosX, entityPosY,
                        entityPosZ, entityPriorityOnlyVisibility, false,
                        priorityOutlineContent(entity.getTeamColor()),
                        lineFrame, entityStorageVertexConsumerProviders, entityRenderDataList);
                }
                finishDebugLineCapture(debugLineStorage, entity, entityPosX, entityPosY,
                    entityPosZ, debugLineStorageVertexConsumerProviders,
                    debugLineRenderDataList, entityWorldVisibility, lineFrame);
                cameraRelativeStorageVertexConsumerProvider.close();
                continue;
            }

            StorageVertexConsumerProvider priorityNameTagStorageVertexConsumerProvider =
                new StorageVertexConsumerProvider(16384);
            StorageVertexConsumerProvider discardedNameTagSeeThroughStorageVertexConsumerProvider =
                new StorageVertexConsumerProvider(16384);
            StorageRoutingBufferSource postTextRoutingBufferSource =
                StorageRoutingBufferSource.ofConsumers(renderLayer -> {
                    boolean seeThrough = isTextSeeThroughLayer(renderLayer);
                    if (isTextBackgroundLayer(renderLayer)) {
                        return priorityNameTagStorageVertexConsumerProvider.getBuffer(renderLayer);
                    }
                    if (seeThrough) {
                        // Vanilla's first name-tag pass contributes a faint glyph below the
                        // final normal glyph. The priority compositor owns the final ordering,
                        // so retain only the normal glyph and the translucent background.
                        return discardedNameTagSeeThroughStorageVertexConsumerProvider.getBuffer(
                            renderLayer);
                    }
                    return priorityNameTagStorageVertexConsumerProvider.getBuffer(renderLayer);
                });
            postTextVertexConsumerProvider = postTextRoutingBufferSource;
            if (debugLineStorage != null) {
                debugLineCapture.set(debugLineStorage);
            }
            try {
                entityRenderDispatcher.render(entity,
                    0,
                    0,
                    0,
                    entity.getYRot(),
                    tickDelta,
                    matrixStack,
                    vertexConsumerProvider,
                    light);
            } finally {
                debugLineCapture.remove();
                postTextVertexConsumerProvider = null;
                postTextRoutingBufferSource.close();
                discardedNameTagSeeThroughStorageVertexConsumerProvider.close();
            }

            finishDebugLineCapture(debugLineStorage, entity, entityPosX, entityPosY,
                entityPosZ, debugLineStorageVertexConsumerProviders,
                debugLineRenderDataList, entityWorldVisibility, lineFrame);

            if (!cameraRelativeStorageVertexConsumerProvider.getLayers().isEmpty()) {
                entityStorageVertexConsumerProviders.add(
                    cameraRelativeStorageVertexConsumerProvider);
                Vec3 cameraPosition = camera.getPosition();
                processWorldEntityRenderData(cameraRelativeStorageVertexConsumerProvider,
                    SimulatedEntityCompatibility.cameraRelativeIdentity(entity),
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z,
                    entityWorldVisibility,
                    true,
                    entityRenderDataList);
            } else {
                cameraRelativeStorageVertexConsumerProvider.close();
            }

            if (!priorityNameTagStorageVertexConsumerProvider.getLayers().isEmpty()) {
                entityStorageVertexConsumerProviders.add(priorityNameTagStorageVertexConsumerProvider);
                processWorldEntityRenderData(priorityNameTagStorageVertexConsumerProvider,
                    semanticIdentity(entity, IDENTITY_NAME_TAG_PRIORITY),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    entityPriorityOnlyVisibility,
                    false,
                    NAME_TAG_DEFAULT_CONTENT,
                    entityRenderDataList);
            } else {
                priorityNameTagStorageVertexConsumerProvider.close();
            }

            if (outlineStorageVertexConsumerProvider != null) {
                addWorldLines(outlineStorageVertexConsumerProvider,
                    semanticIdentity(entity, IDENTITY_OUTLINE), entityPosX, entityPosY,
                    entityPosZ, entityPriorityOnlyVisibility, false,
                    priorityOutlineContent(entity.getTeamColor()),
                    lineFrame, entityStorageVertexConsumerProviders, entityRenderDataList);
            }

            if (cameraEntity) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    entityWorldVisibility,
                    true,
                    entityRenderDataList);
            } else if (entity instanceof FishingHook) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.WORLD,
                    true,
                    entityRenderDataList);
            } else {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.WORLD,
                    true,
                    entityRenderDataList);
            }
        }


        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
        queueBuild(debugLineStorageVertexConsumerProviders, debugLineRenderDataList,
            DEFAULT_WORLD_LINE_WIDTH,
            Constants.Coordinates.WORLD, false);
    }

    /**
     * Mirrors LevelRenderer's camera-entity visibility gate without suppressing capture. The
     * renderer keeps an attached, awake camera entity out of the primary image, but renders the
     * complete entity when the camera is detached or the living camera entity is sleeping.
     */
    static RayTracingFlags worldVisibilityFor(boolean cameraEntity, boolean cameraDetached,
        boolean sleeping) {
        return cameraEntity && !cameraDetached && !sleeping
            ? RayTracingFlags.PLAYER
            : RayTracingFlags.WORLD;
    }

    static RayTracingFlags priorityOnlyVisibilityFor(boolean cameraEntity, boolean cameraDetached,
        boolean sleeping) {
        return cameraEntity && !cameraDetached && !sleeping
            ? RayTracingFlags.PLAYER
            : RayTracingFlags.PRIORITY_ONLY;
    }

    public static VertexConsumer captureDebugLineConsumer(MultiBufferSource original,
        RenderType renderType) {
        StorageVertexConsumerProvider capture = debugLineCapture.get();
        return capture == null ? original.getBuffer(renderType) : capture.getBuffer(renderType);
    }

    /** Builds the line-extrusion frame (axisX, axisY, axisZ) for a rotated owner. */
    public static float[] lineFrameFromOrientation(Quaternionf orientation) {
        if (orientation == null) {
            return null;
        }
        Vector3f x = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f y = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f z = orientation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return new float[] {x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z};
    }

    static DebugLineCaptureScope beginDebugLineCapture(StorageVertexConsumerProvider capture) {
        StorageVertexConsumerProvider previous = debugLineCapture.get();
        debugLineCapture.set(capture);
        return new DebugLineCaptureScope(previous);
    }

    static StorageVertexConsumerProvider activeDebugLineCapture() {
        return debugLineCapture.get();
    }

    static final class DebugLineCaptureScope implements AutoCloseable {
        private final StorageVertexConsumerProvider previous;
        private boolean closed;

        private DebugLineCaptureScope(StorageVertexConsumerProvider previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                debugLineCapture.remove();
            } else {
                debugLineCapture.set(previous);
            }
        }
    }

    public static MultiBufferSource.BufferSource captureCustomParticleBufferSource(
        MultiBufferSource.BufferSource original) {
        StorageVertexConsumerProvider capture = customParticleCapture.get().peek();
        return capture == null ? original : capture;
    }

    private static void finishDebugLineCapture(StorageVertexConsumerProvider storage,
        Entity entity, double x, double y, double z,
        List<StorageVertexConsumerProvider> storages,
        EntityRenderDataList renderData, RayTracingFlags visibility, float[] lineFrame) {
        if (storage == null) {
            return;
        }
        if (storage.getLayers().isEmpty()) {
            storage.close();
            return;
        }
        storages.add(storage);
        float[] previousFrame = lineFrameCapture.get();
        if (lineFrame != null) {
            lineFrameCapture.set(lineFrame);
        }
        try {
            // The local camera entity's own hitbox uses the same visibility as its body (PLAYER in
            // first person), so it hides together with the hidden player model.
            processWorldEntityRenderData(storage, System.identityHashCode(entity), x, y, z,
                visibility, true, worldDebugLineContent("entity_hitbox"), renderData);
        } finally {
            if (previousFrame == null) {
                lineFrameCapture.remove();
            } else {
                lineFrameCapture.set(previousFrame);
            }
        }
    }

    /**
     * Submits one entity render data set while a rotated owner's line-extrusion frame is active.
     * Only line geometry consumes the frame; all other geometry is unaffected.
     */
    private static void processWorldEntityRenderDataWithFrame(
        StorageVertexConsumerProvider storage, int hashCode, double x, double y, double z,
        RayTracingFlags rayTracingFlag, boolean reflect, String contentName,
        EntityRenderDataList renderData, float[] lineFrame) {
        float[] previousFrame = lineFrameCapture.get();
        if (lineFrame != null) {
            lineFrameCapture.set(lineFrame);
        }
        try {
            processWorldEntityRenderData(storage, hashCode, x, y, z, rayTracingFlag, reflect,
                contentName, renderData);
        } finally {
            if (previousFrame == null) {
                lineFrameCapture.remove();
            } else {
                lineFrameCapture.set(previousFrame);
            }
        }
    }

    /** Content tag for one source of the unified world debug-line system. */
    static String worldDebugLineContent(String source) {
        return WORLD_DEBUG_LINE_CONTENT + "/" + source;
    }

    /**
     * Shared semantics for every ray-traced world line/outline family. Adds one render data set
     * with an explicit visibility mask, reflection participation, content/hit-group name and an
     * optional rotated-owner line frame, and records the storage so the caller can either build it
     * standalone or together with its own batch.
     *
     * <p>{@link #queueWorldDebugLines} is the {@link RayTracingFlags#WORLD}/emissive debug-line
     * specialisation. The entity and block-entity glow outlines use
     * {@link RayTracingFlags#PRIORITY_ONLY}: they are excluded from the primary camera, still take
     * part in reflections, GI and shadows, and a dedicated depth-ignoring priority pass draws the
     * parts hidden behind world geometry.
     */
    static void addWorldLines(StorageVertexConsumerProvider storage, int hashCode, double x,
        double y, double z, RayTracingFlags visibility, boolean reflect, String contentName,
        float[] lineFrame, List<StorageVertexConsumerProvider> storages,
        EntityRenderDataList renderData) {
        if (storage == null) {
            return;
        }
        if (storage.getLayers().isEmpty()) {
            storage.close();
            return;
        }
        processWorldEntityRenderDataWithFrame(storage, hashCode, x, y, z, visibility, reflect,
            contentName, renderData, lineFrame);
        storages.add(storage);
    }

    /**
     * The standalone submission entry point for every ray-traced world debug/overlay line family
     * (entity hitboxes, block outline, vanilla debug lines, chunk borders, Sable boxes, ...).
     *
     * <p>Callers supply only explicit parameters: the captured line storage, the origin used by the
     * native extrusion, the line width, a diagnostic source tag, the coordinate mode and an optional
     * rotated-owner frame. Capture, layout, extrusion and TLAS semantics are shared: every call is
     * real world geometry ({@link RayTracingFlags#WORLD}, emission 1.0, full reflection
     * participation, first-person visible).
     */
    public static void queueWorldDebugLines(StorageVertexConsumerProvider storage, double x,
        double y, double z, float lineWidth, String source, Constants.Coordinates coordinate,
        float[] lineFrame) {
        EntityRenderDataList renderData = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> storages = new ArrayList<>(1);
        addWorldLines(storage, System.identityHashCode(WORLD_DEBUG_LINE_CONTENT + "/" + source),
            x, y, z, RayTracingFlags.WORLD, true, worldDebugLineContent(source), lineFrame, storages,
            renderData);
        if (!storages.isEmpty()) {
            queueBuild(storages, renderData, lineWidth, coordinate, false);
        }
    }

    /** Submits an ordinary vanilla {@code lines()/lineStrip()} family at its fixed PT width. */
    public static void queueDefaultWorldDebugLines(StorageVertexConsumerProvider storage, double x,
        double y, double z, String source, Constants.Coordinates coordinate, float[] lineFrame) {
        queueWorldDebugLines(storage, x, y, z, DEFAULT_WORLD_LINE_WIDTH, source, coordinate,
            lineFrame);
    }

    public static synchronized Tuple<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        ViewArea chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();
        var activeSections = ChunkProxy.blockEntitySections(chunks);
        if (RenderAuditBridge.accepts("CHUNK_PERF")) {
            RenderAuditBridge.counter("CHUNK_PERF", "BLOCK_ENTITY_SECTION_VISITS", activeSections.size(), "");
        }
        for (var active : activeSections) {
            // A worker may publish a replacement or the slot may reset after snapshot acquisition.
            // Never render the cached list on behalf of a different compiled owner.
            if (active.section().getCompiled() != active.compiled()) continue;
            List<BlockEntity> list = active.compiled().getRenderableBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    if (FlywheelRenderBridge.shouldSkipVanillaBlockEntity(
                        Minecraft.getInstance().level, blockEntity)) {
                        RenderAuditBridge.counter("FLYWHEEL", "BLOCK_ENTITY_REPLACED", 1,
                            blockEntity.getType().toString());
                        continue;
                    }
                    StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        786432);
                    entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                    StorageVertexConsumerProvider crumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        0);
                    crumblingStorageVertexConsumerProviders.add(
                        crumblingStorageVertexConsumerProvider);

                    StorageVertexConsumerProvider outlineStorageVertexConsumerProvider = null;
                    MultiBufferSource worldVertexConsumerProvider = entityStorageVertexConsumerProvider;
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null
                        && client.levelRenderer.shouldShowEntityOutlines()
                        && blockEntity.hasCustomOutlineRendering(client.player)) {
                        outlineStorageVertexConsumerProvider = new StorageVertexConsumerProvider(16384, 1.0F);
                        StorageOutlineVertexConsumerProvider outlineVertexConsumerProvider =
                            new StorageOutlineVertexConsumerProvider(entityStorageVertexConsumerProvider,
                                outlineStorageVertexConsumerProvider);
                        outlineVertexConsumerProvider.setColor(ARGB.red(CommonColors.WHITE),
                            ARGB.green(CommonColors.WHITE), ARGB.blue(CommonColors.WHITE), 255);
                        worldVertexConsumerProvider = outlineVertexConsumerProvider;
                    }
                    final MultiBufferSource baseWorldVertexConsumerProvider = worldVertexConsumerProvider;

                    BlockPos blockPos = blockEntity.getBlockPos();
                    double entityPosX = blockPos.getX();
                    double entityPosY = blockPos.getY();
                    double entityPosZ = blockPos.getZ();

                    matrixStack.pushPose();
                    SortedSet<BlockDestructionProgress> sortedSet = blockBreakingProgressions.get(
                        blockPos.asLong());
                    if (sortedSet != null && !sortedSet.isEmpty()) {
                        int
                            stage =
                            sortedSet.last()
                                .getProgress();
                        if (stage >= 0) {
                            PoseStack.Pose entry = matrixStack.last();
                            VertexConsumer
                                vertexConsumer =
                                new SheetedDecalTextureGenerator(
                                    crumblingStorageVertexConsumerProvider.getBuffer(
                                        ModelBakery.DESTROY_TYPES.get(
                                            stage)), entry, 1.0F);
                            worldVertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = baseWorldVertexConsumerProvider.getBuffer(renderLayer);
                                return renderLayer.affectsCrumbling() ? VertexMultiConsumer.create(
                                    vertexConsumer,
                                    vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }
                    }

                    blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                        worldVertexConsumerProvider);
                    matrixStack.popPose();

                    processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                        System.identityHashCode(blockEntity),
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        entityRenderDataList);
                    processWorldEntityRenderData(crumblingStorageVertexConsumerProvider,
                        System.identityHashCode(blockEntity) + 1,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        crumblingRenderDataList);
                    if (outlineStorageVertexConsumerProvider != null) {
                        addWorldLines(outlineStorageVertexConsumerProvider,
                            System.identityHashCode(blockEntity), entityPosX, entityPosY,
                            entityPosZ, Constants.RayTracingFlags.PRIORITY_ONLY, false,
                            PRIORITY_OUTLINE_CONTENT, null, entityStorageVertexConsumerProviders,
                            entityRenderDataList);
                    }
                }
            }
        }

        List<BlockEntity> noCullingBlockEntitySnapshot;
        synchronized (noCullingBlockEntities) {
            noCullingBlockEntitySnapshot = new ArrayList<>(noCullingBlockEntities);
        }

        for (BlockEntity blockEntity : noCullingBlockEntitySnapshot) {
            if (FlywheelRenderBridge.shouldSkipVanillaBlockEntity(
                Minecraft.getInstance().level, blockEntity)) {
                RenderAuditBridge.counter("FLYWHEEL", "BLOCK_ENTITY_REPLACED", 1,
                    blockEntity.getType().toString());
                continue;
            }
            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
            StorageVertexConsumerProvider outlineStorageVertexConsumerProvider = null;
            MultiBufferSource worldVertexConsumerProvider = entityStorageVertexConsumerProvider;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null
                && client.levelRenderer.shouldShowEntityOutlines()
                && blockEntity.hasCustomOutlineRendering(client.player)) {
                outlineStorageVertexConsumerProvider = new StorageVertexConsumerProvider(16384, 1.0F);
                StorageOutlineVertexConsumerProvider outlineVertexConsumerProvider =
                    new StorageOutlineVertexConsumerProvider(entityStorageVertexConsumerProvider,
                        outlineStorageVertexConsumerProvider);
                outlineVertexConsumerProvider.setColor(ARGB.red(CommonColors.WHITE),
                    ARGB.green(CommonColors.WHITE), ARGB.blue(CommonColors.WHITE), 255);
                worldVertexConsumerProvider = outlineVertexConsumerProvider;
            }
            BlockPos blockPos = blockEntity.getBlockPos();
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            matrixStack.pushPose();
            blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                worldVertexConsumerProvider);
            matrixStack.popPose();

            processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                System.identityHashCode(blockEntity),
                entityPosX,
                entityPosY,
                entityPosZ,
                Constants.RayTracingFlags.WORLD,
                true,
                entityRenderDataList);
            if (outlineStorageVertexConsumerProvider != null) {
                addWorldLines(outlineStorageVertexConsumerProvider,
                    System.identityHashCode(blockEntity), entityPosX, entityPosY, entityPosZ,
                    Constants.RayTracingFlags.PRIORITY_ONLY, false, PRIORITY_OUTLINE_CONTENT, null,
                    entityStorageVertexConsumerProviders, entityRenderDataList);
            }
        }

        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);

        return new Tuple<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
    }

    public static synchronized void queueTransformedBlockEntities(
        Collection<BlockEntity> blockEntities,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta,
        Matrix4f localTransform,
        Vector3dc rotationPoint,
        Vector3dc worldPosition,
        int identitySalt) {
        if (blockEntities.isEmpty()) {
            return;
        }

        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();
        for (BlockEntity blockEntity : blockEntities) {
            if (FlywheelRenderBridge.shouldSkipVanillaBlockEntity(
                Minecraft.getInstance().level, blockEntity)) {
                RenderAuditBridge.counter("FLYWHEEL", "TRANSFORMED_BLOCK_ENTITY_REPLACED", 1,
                    blockEntity.getType().toString());
                continue;
            }
            StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(786432);
            storageVertexConsumerProviders.add(storage);
            BlockPos blockPos = blockEntity.getBlockPos();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(localTransform);
            poseStack.translate(blockPos.getX() - rotationPoint.x(),
                blockPos.getY() - rotationPoint.y(),
                blockPos.getZ() - rotationPoint.z());

            transformedBlockEntityRenderDepth.set(transformedBlockEntityRenderDepth.get() + 1);
            try {
                blockEntityRenderDispatcher.render(blockEntity, tickDelta, poseStack, storage);
            } finally {
                int depth = transformedBlockEntityRenderDepth.get() - 1;
                if (depth == 0) {
                    transformedBlockEntityRenderDepth.remove();
                } else {
                    transformedBlockEntityRenderDepth.set(depth);
                }
            }
            int hashCode = 31 * System.identityHashCode(blockEntity) + identitySalt;
            processWorldEntityRenderData(storage,
                hashCode,
                worldPosition.x(), worldPosition.y(), worldPosition.z(),
                Constants.RayTracingFlags.WORLD, true, renderDataList);
        }

        queueBuild(storageVertexConsumerProviders, renderDataList);
    }

    public static boolean isRenderingTransformedBlockEntity() {
        return transformedBlockEntityRenderDepth.get() > 0;
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockRenderDispatcher blockRenderManager,
        ClientLevel world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

        Vec3 vec3d = camera.getPosition();
        double d = vec3d.x();
        double e = vec3d.y();
        double f = vec3d.z();
        double renderDistance = Minecraft.getInstance().options
            .getEffectiveRenderDistance() * 16.0;

        for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> blockBreakingProgression :
            blockBreakingProgressions.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.of(blockBreakingProgression.getLongKey());
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            if (blockPos.distToCenterSqr(d, e, f) <= renderDistance * renderDistance) {
                SortedSet<BlockDestructionProgress> sortedSet = blockBreakingProgression.getValue();
                if (sortedSet != null && !sortedSet.isEmpty()) {
                    int
                        stage =
                        sortedSet.last()
                            .getProgress();

                    StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        786432);
                    blockCrumblingStorageVertexConsumerProviders.add(
                        blockCrumblingStorageVertexConsumerProvider);

                    matrixStack.pushPose();
                    PoseStack.Pose entry = matrixStack.last();
                    VertexConsumer
                        vertexConsumer =
                        new SheetedDecalTextureGenerator(
                            blockCrumblingStorageVertexConsumerProvider.getBuffer(
                                ModelBakery.DESTROY_TYPES.get(
                                    stage)), entry, 1.0F);
                    blockRenderManager.renderBreakingTexture(world.getBlockState(blockPos), blockPos, world,
                        matrixStack, vertexConsumer, world.getModelData(blockPos));
                    matrixStack.popPose();

                    processWorldEntityRenderData(blockCrumblingStorageVertexConsumerProvider,
                        0,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        blockCrumblingRenderDataList);
                }
            }
        }

        List<StorageVertexConsumerProvider>
            storageVertexConsumerProviders =
            Stream.concat(crumblingStorageVertexConsumerProviders.stream(),
                    blockCrumblingStorageVertexConsumerProviders.stream())
                .toList();

        EntityRenderDataList
            renderDataList =
            Stream.concat(crumblingRenderDataList.stream(), blockCrumblingRenderDataList.stream())
                .collect(EntityRenderDataList::new, EntityRenderDataList::add,
                    EntityRenderDataList::addAll);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.WORLD,
            true);
    }

    public static void queueHandRebuild(RenderBuffers buffers, float tickDelta,
        ItemInHandRenderer firstPersonRenderer, float handProjectionScale) {
        Minecraft client = Minecraft.getInstance();
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            8192);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.pushPose();

        boolean bl = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getCameraType()
            .isFirstPerson() && !bl && !client.options.hideGui &&
            client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            matrixStack.scale(handProjectionScale, handProjectionScale, 1.0F);
            ((IHeldItemRendererExt) firstPersonRenderer).radiance$renderItem(tickDelta,
                matrixStack,
                storageVertexConsumerProvider,
                client.player,
                client.getEntityRenderDispatcher()
                    .getPackedLightCoords(client.player, tickDelta));
        }

        matrixStack.popPose();

        if (client.options.getCameraType()
            .isFirstPerson() && !bl && !client.options.hideGui &&
            client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            processWorldEntityRenderData(storageVertexConsumerProvider,
                System.identityHashCode(Constants.RayTracingFlags.HAND),
                0,
                0,
                0,
                Constants.RayTracingFlags.HAND,
                true,
                renderDataList);
            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA,
                false);
        }
    }

    public static void queueParticleRebuild(Camera camera, float tickDelta) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> debugLineStorageVertexConsumerProviders =
            new ArrayList<>();
        EntityRenderDataList debugLineRenderDataList = new EntityRenderDataList();

        Map<ParticleMaterialKey, StorageVertexConsumerProvider> worldStorageVertexConsumerProviders =
            new LinkedHashMap<>();

        ParticleEngine particleManager = Minecraft.getInstance().particleEngine;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleRenderType, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();

        for (Map.Entry<ParticleRenderType, Queue<Particle>> particleEntry : particles.entrySet()) {
            ParticleRenderType particleTextureSheet = particleEntry.getKey();
            Queue<Particle> particleQueue = particleEntry.getValue();
            if (particleTextureSheet == ParticleRenderType.NO_RENDER) {
                continue;
            }
            if (!radiance$isBufferedParticleRenderType(particleTextureSheet)) {
                radiance$captureCustomParticleType(particleTextureSheet, particleQueue, camera,
                    tickDelta, storageVertexConsumerProviders, renderDataList,
                    debugLineStorageVertexConsumerProviders, debugLineRenderDataList);
                continue;
            }
            if (particleQueue != null && !particleQueue.isEmpty()) {
                for (Particle particle : particleQueue) {
                    String contentName = normalizeParticleContentName(
                        ((IParticleExt) particle).radiance$getContentName());
                    int emissionStep = radiance$particleEmissionStep(particle, tickDelta);
                    ParticleMaterialKey materialKey = new ParticleMaterialKey(contentName,
                        particleTextureSheet, emissionStep);
                    StorageVertexConsumerProvider worldStorageVertexConsumerProvider =
                        worldStorageVertexConsumerProviders.computeIfAbsent(materialKey, key -> {
                            StorageVertexConsumerProvider provider = new StorageVertexConsumerProvider(
                                0, key.emissionStep() / 15.0F);
                            storageVertexConsumerProviders.add(provider);
                            return provider;
                        });

                    VertexConsumer vertexConsumer = worldStorageVertexConsumerProvider.getBuffer(
                        radiance$particleRenderType(contentName, particleTextureSheet));

                    try {
                        particle.render(vertexConsumer, camera, tickDelta);
                    } catch (Throwable var11) {
                        CrashReport crashReport = CrashReport.forThrowable(var11, "Rendering Particle");
                        CrashReportCategory crashReportSection = crashReport.addCategory(
                            "Particle being rendered");
                        crashReportSection.setDetail("Particle", particle);
                        crashReportSection.setDetail("Particle Type", particleTextureSheet);
                        throw new ReportedException(crashReport);
                    }
                }
            }
        }

        for (Map.Entry<ParticleMaterialKey, StorageVertexConsumerProvider> entry :
            worldStorageVertexConsumerProviders.entrySet()) {
            processWorldEntityRenderData(entry.getValue(), entry.getKey().hashCode(), 0, 0, 0,
                Constants.RayTracingFlags.PARTICLE, true, entry.getKey().contentName(),
                renderDataList);
        }

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
        queueBuild(debugLineStorageVertexConsumerProviders, debugLineRenderDataList,
            DEFAULT_WORLD_LINE_WIDTH,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    private static boolean radiance$isBufferedParticleRenderType(ParticleRenderType renderType) {
        return renderType == ParticleRenderType.TERRAIN_SHEET
            || renderType == ParticleRenderType.PARTICLE_SHEET_OPAQUE
            || renderType == ParticleRenderType.PARTICLE_SHEET_LIT
            || renderType == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static void radiance$captureCustomParticleType(ParticleRenderType renderType,
        Queue<Particle> particles,
        Camera camera,
        float tickDelta,
        List<StorageVertexConsumerProvider> storages,
        EntityRenderDataList renderDataList,
        List<StorageVertexConsumerProvider> debugLineStorages,
        EntityRenderDataList debugLineRenderDataList) {
        if (particles == null || particles.isEmpty()) {
            return;
        }

        try (com.radiance.client.render.ParticleTypeCapture capture =
                 com.radiance.client.render.ParticleTypeCapture.begin(renderType)) {
            if (capture.layer() == null) return;
            for (Particle particle : particles) {
                String contentName = normalizeParticleContentName(
                    ((IParticleExt) particle).radiance$getContentName());
                float emission = radiance$particleEmissionStep(particle, tickDelta) / 15.0F;
                StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(0, emission);
                StorageVertexConsumerProvider debugLineStorage =
                    new StorageVertexConsumerProvider(4096, 1.0F);
                Deque<StorageVertexConsumerProvider> captureStack = customParticleCapture.get();
                captureStack.push(storage);
                try (DebugLineCaptureScope ignored = beginDebugLineCapture(debugLineStorage)) {
                    particle.render(
                        storage.getBuffer(capture.layer()),
                        camera, tickDelta);
                    // Match ParticleEngine's CUSTOM lifecycle.  Storage providers intentionally
                    // make this a no-op, but invoking it lets custom renderers finish their batch
                    // without prematurely drawing or invalidating captured buffers.
                    storage.endBatch();
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Particle");
                    CrashReportCategory crashReportSection = crashReport.addCategory(
                        "Particle being rendered");
                    crashReportSection.setDetail("Particle", particle);
                    crashReportSection.setDetail("Particle Type", renderType);
                    throw new ReportedException(crashReport);
                } finally {
                    captureStack.pop();
                    if (captureStack.isEmpty()) {
                        customParticleCapture.remove();
                    }
                }
                if (debugLineStorage.getLayers().isEmpty()) {
                    debugLineStorage.close();
                } else {
                    debugLineStorages.add(debugLineStorage);
                    String particleHitboxContent = worldDebugLineContent("particle_hitbox");
                    processWorldEntityRenderData(debugLineStorage,
                        31 * System.identityHashCode(particle) + particleHitboxContent.hashCode(),
                        0, 0, 0, Constants.RayTracingFlags.WORLD, true,
                        particleHitboxContent, debugLineRenderDataList);
                }
                if (storage.getLayers().isEmpty()) {
                    storage.close();
                    continue;
                }
                storages.add(storage);
                processWorldEntityRenderData(storage, System.identityHashCode(particle), 0, 0, 0,
                    Constants.RayTracingFlags.PARTICLE, true, contentName, renderDataList);
            }
        }
    }

    private static int radiance$particleEmissionStep(Particle particle, float tickDelta) {
        return com.radiance.client.render.ParticleEmissionCapture.emissionStep(particle,
            () -> ((IParticleExt) particle).radiance$getLightColor(tickDelta));
    }

    public static void queueDebugGeometry(DebugRenderer debugRenderer, Camera camera) {
        Map<Double, StorageVertexConsumerProvider> worldByWidth = new LinkedHashMap<>();
        Map<Double, StorageVertexConsumerProvider> chunkByWidth = new LinkedHashMap<>();
        try (StorageRoutingBufferSource routing = new StorageRoutingBufferSource(renderType -> {
            double width = lineWidthOf(renderType);
            boolean chunk = com.radiance.client.render.DebugEmissionScope.isChunkBorder();
            Map<Double, StorageVertexConsumerProvider> byWidth = chunk ? chunkByWidth : worldByWidth;
            return byWidth.computeIfAbsent(width,
                key -> new StorageVertexConsumerProvider(0, 1.0F));
        })) {
            Vec3 cameraPosition = camera.getPosition();
            debugRenderer.render(new PoseStack(), routing, cameraPosition.x, cameraPosition.y,
                cameraPosition.z);
        } catch (RuntimeException | Error failure) {
            for (StorageVertexConsumerProvider storage : worldByWidth.values()) {
                try { storage.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            for (StorageVertexConsumerProvider storage : chunkByWidth.values()) {
                try { storage.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        for (Map.Entry<Double, StorageVertexConsumerProvider> entry : worldByWidth.entrySet()) {
            queueDebugStorage(entry.getValue(), debugLineWorldWidth(entry.getKey()), "world");
        }
        for (Map.Entry<Double, StorageVertexConsumerProvider> entry : chunkByWidth.entrySet()) {
            queueDebugStorage(entry.getValue(), debugLineWorldWidth(entry.getKey()),
                "chunk_border");
        }
    }

    /**
     * Reads the explicit GL line width declared by the render type's line state. An empty width is
     * vanilla's ordinary 2.5-unit line class; Radiance keeps that value fixed rather than applying
     * vanilla's window-size scaling because the native geometry already has a physical world width.
     */
    private static double lineWidthOf(RenderType renderType) {
        if (renderType instanceof RenderType.CompositeRenderType composite) {
            RenderStateShard.LineStateShard lineState =
                ((RenderTypeCompositeStateAccessor) (Object) composite.state).radiance$getLineState();
            if (lineState != null) {
                OptionalDouble width = ((LineStateShardAccessor) (Object) lineState)
                    .radiance$getWidth();
                if (width != null && width.isPresent()) {
                    return width.getAsDouble();
                }
            }
        }
        return DEFAULT_LINE_WIDTH_UNITS;
    }

    private static float debugLineWorldWidth(double width) {
        return (float) (width * DEBUG_LINE_WORLD_SCALE);
    }

    public static void queueLevelDebugGeometry(StorageVertexConsumerProvider storage) {
        queueDebugStorage(storage, DEFAULT_WORLD_LINE_WIDTH, "level_renderer");
    }

    private static void queueDebugStorage(StorageVertexConsumerProvider storage, float lineWidth,
        String source) {
        queueWorldDebugLines(storage, 0.0, 0.0, 0.0, lineWidth, source,
            Constants.Coordinates.CAMERA_SHIFT, null);
    }

    public static void queueTargetBlockOutlineRebuild(LevelRenderer levelRenderer,
        Camera camera,
        ClientLevel world,
        DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult == null) {
            return;
        }

        StorageVertexConsumerProvider eventStorage = new StorageVertexConsumerProvider(0, 1.0F);
        PoseStack eventPoseStack = new PoseStack();
        Vec3 cameraPosition = camera.getPosition();
        SableSubLevelBridge.BlockOutlineContext outlineContext = null;
        if (client.hitResult instanceof BlockHitResult blockHitResult
            && blockHitResult.getType() != HitResult.Type.MISS) {
            outlineContext = SableSubLevelBridge.blockOutlineContext(world, camera,
                blockHitResult.getBlockPos(), tickCounter.getGameTimeDeltaPartialTick(false));
        }

        Camera eventCamera = camera;
        if (outlineContext == null) {
            eventPoseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        } else {
            outlineContext.applyEventTransform(eventPoseStack, cameraPosition);
            eventCamera = outlineContext.camera();
        }

        boolean handled;
        try {
            handled = ClientHooks.onDrawHighlight(levelRenderer, eventCamera, client.hitResult,
                tickCounter, eventPoseStack, eventStorage);
        } finally {
            SableSubLevelBridge.finishBlockOutlineContext(outlineContext);
        }
        queueWorldDebugLines(eventStorage, 0.0, 0.0, 0.0, DEFAULT_WORLD_LINE_WIDTH,
            "block_outline",
            Constants.Coordinates.CAMERA_SHIFT,
            outlineContext == null ? null
                : lineFrameFromOrientation(outlineContext.orientation()));

        if (handled) {
            return;
        }

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0, 1.0F);
        PoseStack matrixStack = new PoseStack();

        if (client.hitResult instanceof BlockHitResult blockHitResult
            && blockHitResult.getType() != HitResult.Type.MISS) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            if (!blockState.isAir() && world.getWorldBorder().isWithinBounds(blockPos)) {
                double outlinePosX = blockPos.getX();
                double outlinePosY = blockPos.getY();
                double outlinePosZ = blockPos.getZ();
                if (outlineContext != null) {
                    outlineContext.applyLocalTransform(matrixStack, blockPos);
                    outlinePosX = outlineContext.worldPosition().x;
                    outlinePosY = outlineContext.worldPosition().y;
                    outlinePosZ = outlineContext.worldPosition().z;
                }

                VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                    RenderType.lines());
                LevelRendererInvoker.radiance$renderShape(matrixStack, vertexConsumer,
                    blockState.getShape(world, blockPos,
                        CollisionContext.of(camera.getEntity())),
                    0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 0.4F);

                queueWorldDebugLines(storageVertexConsumerProvider, outlinePosX, outlinePosY,
                    outlinePosZ, DEFAULT_WORLD_LINE_WIDTH, "block_outline",
                    Constants.Coordinates.WORLD,
                    outlineContext == null ? null
                        : lineFrameFromOrientation(outlineContext.orientation()));
                return;
            }
        }
        storageVertexConsumerProvider.close();
    }

    public static void queueWeatherBuild(ClientLevel world, Camera camera, int ticks,
        float tickDelta, boolean renderVanillaPrecipitation) {
        StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(262144);
        if (renderVanillaPrecipitation) {
            buildPrecipitation(storage, world, camera, ticks, tickDelta);
        }
        buildWorldBorder(storage, world, camera);

        if (storage.getLayers().isEmpty()) {
            storage.close();
            return;
        }

        EntityRenderDataList renderDataList = new EntityRenderDataList();
        processEntityRenderData(storage, 0, 0, 0, 0,
            RayTracingFlags.WEATHER.getValue(), 0, -1, true,
            EntityProxy::resolveWeatherContentName, false, renderDataList);
        queueBuild(List.of(storage), renderDataList, 0.0F,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    private static void buildPrecipitation(StorageVertexConsumerProvider storage,
        ClientLevel world, Camera camera, int ticks, float tickDelta) {
        float rainLevel = world.getRainLevel(tickDelta);
        if (rainLevel <= 0.0F) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        int cameraX = Mth.floor(cameraPosition.x);
        int cameraY = Mth.floor(cameraPosition.y);
        int cameraZ = Mth.floor(cameraPosition.z);
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        float animationTime = ticks + tickDelta;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int z = cameraZ - radius; z <= cameraZ + radius; z++) {
            for (int x = cameraX - radius; x <= cameraX + radius; x++) {
                int index = (z - cameraZ + 16) * 32 + x - cameraX + 16;
                double sizeX = WEATHER_SIZE_X[index] * 0.5;
                double sizeZ = WEATHER_SIZE_Z[index] * 0.5;
                mutablePos.set((double) x, cameraPosition.y, (double) z);
                Biome biome = world.getBiome(mutablePos).value();
                if (!biome.hasPrecipitation()) {
                    continue;
                }

                int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int minY = Math.max(cameraY - radius, surfaceY);
                int maxY = Math.max(cameraY + radius, surfaceY);
                int lightY = Math.max(surfaceY, cameraY);
                if (minY == maxY) {
                    continue;
                }

                RandomSource random = RandomSource.create(
                    x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
                mutablePos.set(x, minY, z);
                Biome.Precipitation precipitation = biome.getPrecipitationAt(mutablePos);
                if (precipitation == Biome.Precipitation.RAIN) {
                    VertexConsumer builder = storage.getBuffer(
                        RenderType.entityTranslucent(WEATHER_RAIN_TEXTURE));
                    int animationTick = ticks & 0x1FFFF;
                    int randomOffset = x * x * 3121 + x * 45238971
                        + z * z * 418711 + z * 13761 & 0xFF;
                    float speed = 3.0F + random.nextFloat();
                    float verticalOffset = -((animationTick + randomOffset) + tickDelta)
                        / 32.0F * speed % 32.0F;
                    double distanceX = x + 0.5 - cameraPosition.x;
                    double distanceZ = z + 0.5 - cameraPosition.z;
                    float normalizedDistance = (float) Math.sqrt(
                        distanceX * distanceX + distanceZ * distanceZ) / radius;
                    float alpha = ((1.0F - normalizedDistance * normalizedDistance) * 0.5F
                        + 0.5F) * rainLevel;
                    mutablePos.set(x, lightY, z);
                    int light = LevelRenderer.getLightColor(world, mutablePos);
                    weatherQuad(builder, cameraPosition, x, z, minY, maxY, sizeX, sizeZ,
                        0.0F, minY * 0.25F + verticalOffset,
                        1.0F, maxY * 0.25F + verticalOffset, alpha, light);
                } else if (precipitation == Biome.Precipitation.SNOW) {
                    VertexConsumer builder = storage.getBuffer(
                        RenderType.entityTranslucent(WEATHER_SNOW_TEXTURE));
                    float verticalOffset = -((ticks & 0x1FF) + tickDelta) / 512.0F;
                    float horizontalOffset = (float) (random.nextDouble()
                        + animationTime * 0.01 * random.nextGaussian());
                    float drift = (float) (random.nextDouble()
                        + animationTime * random.nextGaussian() * 0.001);
                    double distanceX = x + 0.5 - cameraPosition.x;
                    double distanceZ = z + 0.5 - cameraPosition.z;
                    float normalizedDistance = (float) Math.sqrt(
                        distanceX * distanceX + distanceZ * distanceZ) / radius;
                    float alpha = ((1.0F - normalizedDistance * normalizedDistance) * 0.3F
                        + 0.5F) * rainLevel;
                    mutablePos.set(x, lightY, z);
                    int packedLight = LevelRenderer.getLightColor(world, mutablePos);
                    int sky = packedLight >> 16 & 0xFFFF;
                    int block = packedLight & 0xFFFF;
                    int boostedSky = (sky * 3 + 240) / 4;
                    int boostedBlock = (block * 3 + 240) / 4;
                    weatherQuad(builder, cameraPosition, x, z, minY, maxY, sizeX, sizeZ,
                        horizontalOffset, minY * 0.25F + verticalOffset + drift,
                        1.0F + horizontalOffset, maxY * 0.25F + verticalOffset + drift,
                        alpha, boostedBlock, boostedSky);
                }
            }
        }
    }

    private static void weatherQuad(VertexConsumer builder, Vec3 cameraPosition,
        int x, int z, int minY, int maxY, double sizeX, double sizeZ,
        float minU, float minV, float maxU, float maxV, float alpha, int packedLight) {
        weatherVertex(builder, x - cameraPosition.x - sizeX + 0.5,
            maxY - cameraPosition.y, z - cameraPosition.z - sizeZ + 0.5,
            minU, minV, alpha, packedLight);
        weatherVertex(builder, x - cameraPosition.x + sizeX + 0.5,
            maxY - cameraPosition.y, z - cameraPosition.z + sizeZ + 0.5,
            maxU, minV, alpha, packedLight);
        weatherVertex(builder, x - cameraPosition.x + sizeX + 0.5,
            minY - cameraPosition.y, z - cameraPosition.z + sizeZ + 0.5,
            maxU, maxV, alpha, packedLight);
        weatherVertex(builder, x - cameraPosition.x - sizeX + 0.5,
            minY - cameraPosition.y, z - cameraPosition.z - sizeZ + 0.5,
            minU, maxV, alpha, packedLight);
    }

    private static void weatherQuad(VertexConsumer builder, Vec3 cameraPosition,
        int x, int z, int minY, int maxY, double sizeX, double sizeZ,
        float minU, float minV, float maxU, float maxV, float alpha,
        int lightU, int lightV) {
        weatherVertex(builder, x - cameraPosition.x - sizeX + 0.5,
            maxY - cameraPosition.y, z - cameraPosition.z - sizeZ + 0.5,
            minU, minV, alpha, lightU, lightV);
        weatherVertex(builder, x - cameraPosition.x + sizeX + 0.5,
            maxY - cameraPosition.y, z - cameraPosition.z + sizeZ + 0.5,
            maxU, minV, alpha, lightU, lightV);
        weatherVertex(builder, x - cameraPosition.x + sizeX + 0.5,
            minY - cameraPosition.y, z - cameraPosition.z + sizeZ + 0.5,
            maxU, maxV, alpha, lightU, lightV);
        weatherVertex(builder, x - cameraPosition.x - sizeX + 0.5,
            minY - cameraPosition.y, z - cameraPosition.z - sizeZ + 0.5,
            minU, maxV, alpha, lightU, lightV);
    }

    private static void weatherVertex(VertexConsumer builder, double x, double y, double z,
        float u, float v, float alpha, int packedLight) {
        builder.addVertex((float) x, (float) y, (float) z)
            .setUv(u, v)
            .setColor(1.0F, 1.0F, 1.0F, alpha)
            .setLight(packedLight);
    }

    private static void weatherVertex(VertexConsumer builder, double x, double y, double z,
        float u, float v, float alpha, int lightU, int lightV) {
        builder.addVertex((float) x, (float) y, (float) z)
            .setUv(u, v)
            .setColor(1.0F, 1.0F, 1.0F, alpha)
            .setUv2(lightU, lightV);
    }

    private static void buildWorldBorder(StorageVertexConsumerProvider storage,
        ClientLevel world, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        WorldBorder border = world.getWorldBorder();
        Vec3 cameraPosition = camera.getPosition();
        double renderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0;
        if (cameraPosition.x < border.getMaxX() - renderDistance
            && cameraPosition.x > border.getMinX() + renderDistance
            && cameraPosition.z < border.getMaxZ() - renderDistance
            && cameraPosition.z > border.getMinZ() + renderDistance) {
            return;
        }

        double alpha = 1.0 - border.getDistanceToBorder(cameraPosition.x, cameraPosition.z)
            / renderDistance;
        alpha = Mth.clamp(Math.pow(alpha, 4.0), 0.0, 1.0);
        double farPlane = minecraft.gameRenderer.getDepthFar();
        int color = border.getStatus().getColor();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float animation = (float) (Util.getMillis() % 3000L) / 3000.0F;
        float minV = (float) -Mth.frac(cameraPosition.y * 0.5);
        float maxV = minV + (float) farPlane;
        VertexConsumer builder = storage.getBuffer(
            RenderType.entityTranslucent(WORLD_BORDER_TEXTURE));

        double start = Math.max(Mth.floor(cameraPosition.z - renderDistance), border.getMinZ());
        double end = Math.min(Mth.ceil(cameraPosition.z + renderDistance), border.getMaxZ());
        float initialU = (Mth.floor(start) & 1) * 0.5F;
        if (cameraPosition.x > border.getMaxX() - renderDistance) {
            borderXQuad(builder, border.getMaxX() - cameraPosition.x, start, end,
                cameraPosition.z, farPlane, animation, initialU, minV, maxV,
                red, green, blue, (float) alpha, false);
        }
        if (cameraPosition.x < border.getMinX() + renderDistance) {
            borderXQuad(builder, border.getMinX() - cameraPosition.x, start, end,
                cameraPosition.z, farPlane, animation, initialU, minV, maxV,
                red, green, blue, (float) alpha, true);
        }

        start = Math.max(Mth.floor(cameraPosition.x - renderDistance), border.getMinX());
        end = Math.min(Mth.ceil(cameraPosition.x + renderDistance), border.getMaxX());
        initialU = (Mth.floor(start) & 1) * 0.5F;
        if (cameraPosition.z > border.getMaxZ() - renderDistance) {
            borderZQuad(builder, border.getMaxZ() - cameraPosition.z, start, end,
                cameraPosition.x, farPlane, animation, initialU, minV, maxV,
                red, green, blue, (float) alpha, true);
        }
        if (cameraPosition.z < border.getMinZ() + renderDistance) {
            borderZQuad(builder, border.getMinZ() - cameraPosition.z, start, end,
                cameraPosition.x, farPlane, animation, initialU, minV, maxV,
                red, green, blue, (float) alpha, false);
        }
    }

    private static void borderXQuad(VertexConsumer builder, double x, double start, double end,
        double cameraZ, double farPlane, float animation, float initialU,
        float minV, float maxV, float red, float green, float blue, float alpha,
        boolean positiveU) {
        float u = initialU;
        for (double z = start; z < end; z += 1.0) {
            double width = Math.min(1.0, end - z);
            float uWidth = (float) width * 0.5F;
            float u0 = positiveU ? animation + u : animation - u;
            float u1 = positiveU ? animation + u + uWidth : animation - (u + uWidth);
            borderVertex(builder, x, -farPlane, z - cameraZ, u0, animation + maxV,
                red, green, blue, alpha);
            borderVertex(builder, x, -farPlane, z + width - cameraZ, u1,
                animation + maxV, red, green, blue, alpha);
            borderVertex(builder, x, farPlane, z + width - cameraZ, u1,
                animation + minV, red, green, blue, alpha);
            borderVertex(builder, x, farPlane, z - cameraZ, u0, animation + minV,
                red, green, blue, alpha);
            u += 0.5F;
        }
    }

    private static void borderZQuad(VertexConsumer builder, double z, double start, double end,
        double cameraX, double farPlane, float animation, float initialU,
        float minV, float maxV, float red, float green, float blue, float alpha,
        boolean positiveU) {
        float u = initialU;
        for (double x = start; x < end; x += 1.0) {
            double width = Math.min(1.0, end - x);
            float uWidth = (float) width * 0.5F;
            float u0 = positiveU ? animation + u : animation - u;
            float u1 = positiveU ? animation + u + uWidth : animation - (u + uWidth);
            borderVertex(builder, x - cameraX, -farPlane, z, u0, animation + maxV,
                red, green, blue, alpha);
            borderVertex(builder, x + width - cameraX, -farPlane, z, u1,
                animation + maxV, red, green, blue, alpha);
            borderVertex(builder, x + width - cameraX, farPlane, z, u1,
                animation + minV, red, green, blue, alpha);
            borderVertex(builder, x - cameraX, farPlane, z, u0, animation + minV,
                red, green, blue, alpha);
            u += 0.5F;
        }
    }

    private static void borderVertex(VertexConsumer builder, double x, double y, double z,
        float u, float v, float red, float green, float blue, float alpha) {
        builder.addVertex((float) x, (float) y, (float) z)
            .setUv(u, v)
            .setColor(red, green, blue, alpha);
    }

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList) {
        queueBuild(storageVertexConsumerProviders, entityRenderDataList, 0.0125f,
            Constants.Coordinates.WORLD, false);
    }

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        queueBuildInternal(storageVertexConsumerProviders, entityRenderDataList, lineWidth,
            coordinate, normalOffset, true);
    }

    /** Submits Radiance Outliner line geometry (camera-relative) as path-traced lines. */
    public static void queueOutlinerLines(StorageVertexConsumerProvider storage, float lineWidth,
        String contentName) {
        EntityRenderDataList renderData = new EntityRenderDataList();
        processWorldEntityRenderData(storage, 0, 0.0, 0.0, 0.0, Constants.RayTracingFlags.WORLD,
            true, contentName, renderData);
        queueBuild(List.of(storage), renderData, lineWidth, Constants.Coordinates.CAMERA_SHIFT,
            false);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList) {
        queueBuildWithoutClose(entityRenderDataList, 0.0125f, Constants.Coordinates.WORLD, false);
    }

    static native boolean beginCachedCloud(long revision, double x, double y, double z);
    static native void endCachedCloud(boolean success);

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        queueBuildInternal(null, entityRenderDataList, lineWidth, coordinate, normalOffset, false);
    }

    private static void queueBuildInternal(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset,
        boolean closeAfterBuild) {
        TextureManager
            textureManager =
            Minecraft.getInstance()
                .getTextureManager();
        // Native queueBuild copies names before returning. Reuse equal strings only within
        // this submission; a later frame/reload or nested call owns a separate table.
        SubmissionStrings submissionStrings = new SubmissionStrings();
        ByteBuffer entityHashCodeBB = null;
        ByteBuffer entityPosXBB = null;
        ByteBuffer entityPosYBB = null;
        ByteBuffer entityPosZBB = null;
        ByteBuffer entityRayTracingFlagBB = null;
        ByteBuffer entityPostRenderFlagBB = null;
        ByteBuffer entityPrebuiltBLASBB = null;
        ByteBuffer entityPostBB = null;
        ByteBuffer entityLayerCountBB = null;
        ByteBuffer entityLineFrameBB = null;
        ByteBuffer geometryTypeBB = null;
        ByteBuffer geometryGroupNameBB = null;
        ByteBuffer geometryContentNameBB = null;
        ByteBuffer geometryTextureBB = null;
        ByteBuffer vertexFormatBB = null;
        ByteBuffer indexFormatBB = null;
        ByteBuffer vertexCountBB = null;
        ByteBuffer verticesBB = null;
        ByteBuffer vertexByteCountsBB = null;
        final boolean directEntityInput = closeAfterBuild && DIRECT_ENTITY_INPUT_ENABLED;

        try {
            int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            for (var draw : entityRenderDataList.rigidDraws) draw.submit();
            entityHashCodeBB = MemoryUtil.memAlloc(entityHashCodeSize);
            long entityHashCodeAddr = memAddress(entityHashCodeBB);
            int entityHashCodeBaseAddr = 0;

            int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosXBB = MemoryUtil.memAlloc(entityPosXSize);
            long entityPosXAddr = memAddress(entityPosXBB);
            int entityPosXBaseAddr = 0;

            int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosYBB = MemoryUtil.memAlloc(entityPosYSize);
            long entityPosYAddr = memAddress(entityPosYBB);
            int entityPosYBaseAddr = 0;

            int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosZBB = MemoryUtil.memAlloc(entityPosZSize);
            long entityPosZAddr = memAddress(entityPosZBB);
            int entityPosZBaseAddr = 0;

            int entityRayTracingFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityRayTracingFlagBB = MemoryUtil.memAlloc(entityRayTracingFlagSize);
            long entityRayTracingFlagAddr = memAddress(entityRayTracingFlagBB);
            int entityRayTracingFlagBaseAddr = 0;

            int entityPostRenderFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostRenderFlagBB = MemoryUtil.memAlloc(entityPostRenderFlagSize);
            long entityPostRenderFlagAddr = memAddress(entityPostRenderFlagBB);
            int entityPostRenderFlagBaseAddr = 0;

            int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPrebuiltBLASBB = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
            long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
            int entityPrebuiltBLASBaseAddr = 0;

            int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostBB = MemoryUtil.memAlloc(entityPostSize);
            long entityPostAddr = memAddress(entityPostBB);
            int entityPostBaseAddr = 0;

            int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityLayerCountBB = MemoryUtil.memAlloc(entityLayerCountSize);
            long entityLayerCountAddr = memAddress(entityLayerCountBB);
            int entityLayerCountBaseAddr = 0;

            int entityLineFrameSize = entityRenderDataList.getTotalEntityCount() * 9 * Float.BYTES;
            entityLineFrameBB = MemoryUtil.memAlloc(entityLineFrameSize);
            long entityLineFrameAddr = memAddress(entityLineFrameBB);
            int entityLineFrameBaseAddr = 0;

            int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
            long geometryTypeAddr = memAddress(geometryTypeBB);
            int geometryTypeBaseAddr = 0;

            int geometryGroupNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
            long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
            int geometryGroupNameBaseAddr = 0;

            int geometryContentNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryContentNameBB = MemoryUtil.memAlloc(geometryContentNameSize);
            long geometryContentNameAddr = memAddress(geometryContentNameBB);
            int geometryContentNameBaseAddr = 0;

            int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
            long geometryTextureAddr = memAddress(geometryTextureBB);
            int geometryTextureBaseAddr = 0;

            int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
            long vertexFormatAddr = memAddress(vertexFormatBB);
            int vertexFormatBaseAddr = 0;

            int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            indexFormatBB = MemoryUtil.memAlloc(indexFormatSize);
            long indexFormatAddr = memAddress(indexFormatBB);
            int indexFormatBaseAddr = 0;

            int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
            long vertexCountAddr = memAddress(vertexCountBB);
            int vertexCountBaseAddr = 0;

            int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            verticesBB = MemoryUtil.memAlloc(verticesSize);
            long verticesAddr = memAddress(verticesBB);
            int verticesBaseAddr = 0;

            long vertexByteCountsAddr = 0;
            int vertexByteCountsBaseAddr = 0;
            {
                int geometryByteCountSize = Math.multiplyExact(
                    entityRenderDataList.getTotalLayersCount(), Long.BYTES);
                if (geometryByteCountSize != 0) {
                    vertexByteCountsBB = MemoryUtil.memAlloc(geometryByteCountSize);
                    vertexByteCountsAddr = memAddress(vertexByteCountsBB);
                }
            }

            for (EntityRenderData entityRenderData : entityRenderDataList) {
                entityHashCodeBB.putInt(entityHashCodeBaseAddr, entityRenderData.hashCode);
                entityHashCodeBaseAddr += Integer.BYTES;

                entityPosXBB.putDouble(entityPosXBaseAddr, entityRenderData.x);
                entityPosXBaseAddr += Double.BYTES;

                entityPosYBB.putDouble(entityPosYBaseAddr, entityRenderData.y);
                entityPosYBaseAddr += Double.BYTES;

                entityPosZBB.putDouble(entityPosZBaseAddr, entityRenderData.z);
                entityPosZBaseAddr += Double.BYTES;

                entityRayTracingFlagBB.putInt(entityRayTracingFlagBaseAddr,
                    entityRenderData.rayTracingFlag);
                entityRayTracingFlagBaseAddr += Integer.BYTES;

                entityPostRenderFlagBB.putInt(entityPostRenderFlagBaseAddr,
                    entityRenderData.postRenderFlag);
                entityPostRenderFlagBaseAddr += Integer.BYTES;

                entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr, entityRenderData.prebuiltBLAS);
                entityPrebuiltBLASBaseAddr += Integer.BYTES;

                entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
                entityPostBaseAddr += Integer.BYTES;

                entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
                entityLayerCountBaseAddr += Integer.BYTES;

                float[] lineFrame = entityRenderData.getLineFrame();
                if (lineFrame == null) {
                    lineFrame = IDENTITY_LINE_FRAME;
                }
                for (int k = 0; k < 9; k++) {
                    entityLineFrameBB.putFloat(entityLineFrameBaseAddr + k * Float.BYTES, lineFrame[k]);
                }
                entityLineFrameBaseAddr += 9 * Float.BYTES;

                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    RenderType renderLayer = entityRenderLayer.renderLayer;
                    MeshData vertexBuffer = entityRenderLayer.builtBuffer;

                    ResourceLocation
                        identifier =
                        ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                            .orElse(MissingTextureAtlasSprite.getLocation());
                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(identifier)
                            .getId();
                    int
                        vertexFormatID =
                        Constants.VertexFormats.getValue(vertexBuffer.drawState()
                            .format());
                    int
                        indexFormatID =
                        Constants.DrawModes.getValue(vertexBuffer.drawState()
                            .mode());

                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.vertexBuffer());
                    geometryTypeBB.putInt(geometryTypeBaseAddr, com.radiance.client.render.MaterialFaces.encode(geometryTypeID,
                        com.radiance.client.render.MaterialFaces.capture(renderLayer)));
                    geometryTypeBaseAddr += Integer.BYTES;

                    String normalizedLayerName =
                        PBRVertexConsumer.normalizeTextLayerName(renderLayer.name);
                    String geometryGroupName = geometryGroupName(
                        entityRenderLayer.contentName(), normalizedLayerName);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        submissionStrings.address(geometryGroupName));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    geometryContentNameBB.putLong(geometryContentNameBaseAddr,
                        submissionStrings.address(entityRenderLayer.contentName()));
                    geometryContentNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                    indexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.drawState()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                    {
                        vertexByteCountsBB.putLong(vertexByteCountsBaseAddr, vertexBufferInfo.size());
                        vertexByteCountsBaseAddr += Long.BYTES;
                    }
                }
            }

            queueBuildSourcesV1(1, directEntityInput,
                lineWidth,
                coordinate.getValue(),
                normalOffset,
                entityRenderDataList.getTotalEntityCount(),
                entityHashCodeAddr,
                entityPosXAddr,
                entityPosYAddr,
                entityPosZAddr,
                entityRayTracingFlagAddr,
                entityPostRenderFlagAddr,
                entityPrebuiltBLASAddr,
                entityPostAddr,
                entityLayerCountAddr,
                entityLineFrameAddr,
                geometryTypeAddr,
                geometryGroupNameAddr,
                geometryContentNameAddr,
                geometryTextureAddr,
                vertexFormatAddr,
                indexFormatAddr,
                vertexCountAddr,
                verticesAddr,
                vertexByteCountsAddr);
        } finally {
            freeDirectBuffer(entityHashCodeBB);
            freeDirectBuffer(entityPosXBB);
            freeDirectBuffer(entityPosYBB);
            freeDirectBuffer(entityPosZBB);
            freeDirectBuffer(entityRayTracingFlagBB);
            freeDirectBuffer(entityPostRenderFlagBB);
            freeDirectBuffer(entityPrebuiltBLASBB);
            freeDirectBuffer(entityPostBB);
            freeDirectBuffer(entityLayerCountBB);
            freeDirectBuffer(entityLineFrameBB);
            freeDirectBuffer(geometryTypeBB);
            freeDirectBuffer(geometryGroupNameBB);
            freeDirectBuffer(geometryContentNameBB);
            freeDirectBuffer(geometryTextureBB);
            freeDirectBuffer(vertexFormatBB);
            freeDirectBuffer(indexFormatBB);
            freeDirectBuffer(vertexCountBB);
            freeDirectBuffer(verticesBB);
            freeDirectBuffer(vertexByteCountsBB);
            submissionStrings.close();

            if (closeAfterBuild) {
                closeBuiltBuffers(entityRenderDataList);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            }
        }
    }

    private static void freeDirectBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void closeBuiltBuffers(EntityRenderDataList entityRenderDataList) {
        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                MeshData vertexBuffer = entityRenderLayer.builtBuffer;
                vertexBuffer.close();
            }
        }
    }

    private static void closeStorageVertexConsumerProviders(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders) {
        if (storageVertexConsumerProviders == null) {
            return;
        }

        for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
            storageVertexConsumerProvider.close();
        }
    }

    private static native void queueBuild(float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityHashCodes,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRayTracingFlags,
        long entityPostRenderFlags,
        long entityPrebuiltBLASs,
        long entityPosts,
        long entityLayerCounts,
        long entityLineFrames,
        long geometryTypes,
        long geometryGroupNames,
        long geometryContentNames,
        long geometryTextures,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices);

    private static native void queueBuildSourcesV1(int formatAbiVersion,
        boolean directEntityInput,
        float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityHashCodes,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRayTracingFlags,
        long entityPostRenderFlags,
        long entityPrebuiltBLASs,
        long entityPosts,
        long entityLayerCounts,
        long entityLineFrames,
        long geometryTypes,
        long geometryGroupNames,
        long geometryContentNames,
        long geometryTextures,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices,
        long vertexByteCounts);

    public static native int beginWorldMeshFrame(long worldToken, long frameToken,
        long resourceGeneration);

    public static native int beginWorldMeshStage(long worldToken, long frameToken,
        long resourceGeneration, long stageToken, int stage);

    public static native int queueWorldMesh(long worldToken, long frameToken,
        long resourceGeneration, long stageToken, int stage, int coordinate,
        double originX, double originY, double originZ, int sourceId, int rayTracingFlag,
        int geometryType, int textureId, int vertexFormat, int drawMode, int indexType,
        int vertexCount, int indexCount, long vertices, int vertexBytes, long indices,
        int indexBytes, int alphaMode, float emission, long shaderKey, long materialKey,
        long auditId);

    public static native int pollWorldMeshAudit(long auditId, boolean consumeTerminal);

    public static native void endWorldMeshStage(long worldToken, long frameToken,
        long resourceGeneration, long stageToken, boolean commit);

    public static native void endWorldMeshFrame(long worldToken, long frameToken,
        long resourceGeneration, boolean commit);

    public static native void invalidateWorldMeshGeneration(long resourceGeneration);

    public static native void build();

    /**
     * Minecraft's Font renderer selects the see-through glyph sheet by RenderType, not by
     * the caller. Keep this predicate in the capture boundary so TextDisplay can preserve
     * that semantic and name tags can deliberately discard the redundant faint glyph pass.
     */
    private static boolean isTextSeeThroughLayer(RenderType renderLayer) {
        String name = renderLayer.name;
        return name.contains("text_see_through") || name.contains("text_background_see_through")
            || name.contains("text_intensity_see_through");
    }

    private static boolean isTextBackgroundLayer(RenderType renderLayer) {
        return PBRVertexConsumer.normalizeTextLayerName(renderLayer.name)
            .startsWith("text_background");
    }

    private static String geometryGroupName(String contentName, String normalizedLayerName) {
        if (contentName.startsWith(PRIORITY_OUTLINE_CONTENT)) {
            String suffix = contentName.substring(PRIORITY_OUTLINE_CONTENT.length());
            return suffix.startsWith("/") && suffix.length() > 1
                ? "priority_outline_" + suffix.substring(1)
                : "priority_outline";
        }
        return normalizedLayerName;
    }

    static String priorityOutlineContent(int color) {
        int rgb = color & 0xFFFFFF;
        OutlinePaletteColor nearest = OUTLINE_PALETTE[OUTLINE_PALETTE.length - 1];
        int nearestDistance = Integer.MAX_VALUE;
        for (OutlinePaletteColor candidate : OUTLINE_PALETTE) {
            int candidateRgb = candidate.rgb();
            int dr = ARGB.red(rgb) - ARGB.red(candidateRgb);
            int dg = ARGB.green(rgb) - ARGB.green(candidateRgb);
            int db = ARGB.blue(rgb) - ARGB.blue(candidateRgb);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest.suffix().isEmpty()
            ? PRIORITY_OUTLINE_CONTENT
            : PRIORITY_OUTLINE_CONTENT + "/" + nearest.suffix();
    }

    private static int semanticIdentity(Object owner, int semanticSalt) {
        return 31 * System.identityHashCode(owner) + semanticSalt;
    }

    private static String normalizeParticleContentName(String particleContentName) {
        if (particleContentName == null || particleContentName.isBlank()) {
            return PARTICLE_DEFAULT_CONTENT;
        }
        return particleContentName;
    }

    private record ParticleMaterialKey(String contentName,
                                       ParticleRenderType renderType,
                                       int emissionStep) {
    }

    private record OutlinePaletteColor(int rgb, String suffix) {
    }

    private static RenderType radiance$particleRenderType(String contentName,
        ParticleRenderType particleRenderType) {
        if (particleRenderType == ParticleRenderType.TERRAIN_SHEET) {
            return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        }
        if (particleRenderType == ParticleRenderType.PARTICLE_SHEET_OPAQUE
            || particleRenderType == ParticleRenderType.PARTICLE_SHEET_LIT) {
            return RenderType.entityCutout(TextureAtlas.LOCATION_PARTICLES);
        }
        return RenderType.entityTranslucent(TextureAtlas.LOCATION_PARTICLES);
    }

    private static String resolveWeatherContentName(RenderType renderLayer) {
        ResourceLocation identifier = getTextureId(renderLayer);
        if (WEATHER_RAIN_TEXTURE.equals(identifier)) {
            return WEATHER_RAIN_CONTENT;
        }
        if (WEATHER_SNOW_TEXTURE.equals(identifier)) {
            return WEATHER_SNOW_CONTENT;
        }
        return WEATHER_DEFAULT_CONTENT;
    }

    private static ResourceLocation getTextureId(RenderType renderLayer) {
        if (renderLayer instanceof RenderType.CompositeRenderType multiPhase) {
            return multiPhase.state.textureState.cutoutTexture()
                .orElse(MissingTextureAtlasSprite.getLocation());
        }
        return MissingTextureAtlasSprite.getLocation();
    }

    public record EntityRenderLayer(RenderType renderLayer, MeshData builtBuffer,
                                    boolean reflect, String contentName) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        /**
         * Optional orthonormal line-extrusion frame (axisX, axisY, axisZ as 9 floats). {@code null}
         * means the world axes, which keeps ordinary geometry unchanged.
         */
        private float[] lineFrame;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(int hashCode, double x, double y, double z, int rayTracingFlag,
            int postRenderFlag,
            int prebuiltBLAS,
            boolean post) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rayTracingFlag = rayTracingFlag;
            this.postRenderFlag = postRenderFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public int getRayTracingFlag() {
            return rayTracingFlag;
        }

        public int getPostRenderFlag() {
            return postRenderFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public int getHashCode() {
            return hashCode;
        }

        public boolean isPost() {
            return post;
        }

        public float[] getLineFrame() {
            return lineFrame;
        }

        public void setLineFrame(float[] lineFrame) {
            this.lineFrame = lineFrame;
        }
    }

    public static class EntityRenderDataList extends ArrayList<EntityRenderData> {
        final java.util.List<com.radiance.client.vertex.RigidModelCapture.Draw> rigidDraws = new ArrayList<>();

        private int totalLayersCount;

        @Override
        public boolean add(EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            return super.add(entityRenderData);
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }

    public static native void queueRigidModel(long model, long instance, int geometryType, int texture,
        long vertices, int vertexCount, double x, double y, double z, int mask, long matrix, long group);
}
