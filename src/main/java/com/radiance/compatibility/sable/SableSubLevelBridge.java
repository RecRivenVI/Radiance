package com.radiance.compatibility.sable;

import com.radiance.compatibility.veil.VeilAdapter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * 将 Sable 的隔离区块接入 Radiance/Radiance native，而不是让它们回落到不可用的 OpenGL
 * {@code VertexBuffer} 路径。
 */
public final class SableSubLevelBridge {

    private static final String VANILLA_RENDER_DATA =
        "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData";
    private static final String VANILLA_SINGLE_RENDER_DATA =
        "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData";

    private static volatile Method getContainer;
    private static volatile Method getAllSubLevels;
    private static volatile Object sableHelper;
    private static volatile Method getContainingEntity;
    private static volatile Method getContainingBlock;
    private static volatile Method getTrackingSubLevel;
    private static volatile Method setBlockEntityCameraPosition;
    private static volatile Method renderSingleBlock;
    private static volatile Camera blockOutlineCamera;
    private static volatile Method setBlockOutlineCamera;
    private static volatile Method setBlockOutlinePose;
    private static volatile Method clearBlockOutlineCamera;

    private SableSubLevelBridge() {
    }

    /** Restores the Sable setup hook skipped by Radiance's shortened terrain setup. */
    public static void prepareRenderer(ClientLevel level, Camera camera,
        net.minecraft.client.renderer.culling.Frustum frustum, boolean spectator) {
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            RendererLifecycle.prepare(level, camera, frustum, spectator);
        }
    }

    // Keep optional Sable/Veil types out of the always-loaded bridge's signatures.
    private static final class RendererLifecycle {
        private static void prepare(ClientLevel level, Camera camera,
            net.minecraft.client.renderer.culling.Frustum frustum, boolean spectator) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return;
            var dispatcher = dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher.get();
            dispatcher.preRenderChunks(camera);
            Vec3 position = camera.getPosition();
            dispatcher.updateCulling(container.getAllSubLevels(), position.x, position.y,
                position.z, VeilAdapter.createRenderBridge(frustum),
                spectator);
        }
    }

    public static void update(ClientLevel level, Camera camera, float partialTick) {
        Collection<?> subLevels = getSubLevels(level);
        if (subLevels == null) {
            ChunkProxy.releaseMissingExternalSections(Collections.emptySet());
            return;
        }

        Set<SectionRenderDispatcher.RenderSection> activeSections =
            Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object subLevel : subLevels) {
            Object renderData = invoke(subLevel, "getRenderData");
            if (renderData == null
                || !VANILLA_RENDER_DATA.equals(renderData.getClass().getName())) {
                continue;
            }

            Object sectionsValue = invoke(renderData, "allRenderSections");
            if (!(sectionsValue instanceof Collection<?> sections)) {
                continue;
            }

            Object pose = invoke(subLevel, "renderPose", new Class<?>[] {float.class}, partialTick);
            PoseComponents components = readPose(pose);
            if (components == null) {
                continue;
            }

            for (Object value : sections) {
                if (!(value instanceof SectionRenderDispatcher.RenderSection section)) {
                    continue;
                }

                activeSections.add(section);
                ChunkProxy.syncExternalSection(section, sectionTransform(section.getOrigin(), components));
            }
        }

        ChunkProxy.releaseMissingExternalSections(activeSections);
    }

    /**
     * Sable deliberately renders a one-block sublevel without allocating a chunk section. Its
     * vanilla dispatcher cannot run after Radiance replaces {@code LevelRenderer.renderLevel}, so
     * capture that specialized model path into Radiance's world geometry queue instead.
     */
    public static void queueSingleBlocks(ClientLevel level, float partialTick) {
        Collection<?> subLevels = getSubLevels(level);
        if (subLevels == null || subLevels.isEmpty()) {
            return;
        }

        List<StorageVertexConsumerProvider> storages = new ArrayList<>();
        EntityProxy.EntityRenderDataList renderDataList = new EntityProxy.EntityRenderDataList();
        for (Object subLevel : subLevels) {
            Object renderData = invoke(subLevel, "getRenderData");
            if (!isSingleRenderData(renderData)) {
                continue;
            }

            PoseComponents components = readPose(
                invoke(subLevel, "renderPose", new Class<?>[] {float.class}, partialTick));
            if (components == null) {
                continue;
            }

            StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(786432);
            for (RenderType renderType : RenderType.chunkBufferLayers()) {
                VertexConsumer consumer = storage.getBuffer(renderType);
                invokeRenderSingleBlock(renderData, renderType, consumer,
                    components.position.x, components.position.y, components.position.z);
            }

            int previousSize = renderDataList.size();
            EntityProxy.processWorldEntityRenderData(storage,
                31 * System.identityHashCode(subLevel) + 1,
                components.position.x, components.position.y, components.position.z,
                com.radiance.client.constant.Constants.RayTracingFlags.WORLD, true,
                renderDataList);
            if (renderDataList.size() > previousSize) {
                storages.add(storage);
            } else {
                storage.close();
            }
        }

        if (!renderDataList.isEmpty()) {
            EntityProxy.queueBuild(storages, renderDataList);
        }
    }

    public static void markDirty(SectionRenderDispatcher.RenderSection section) {
        ChunkProxy.enqueueExternalRebuild(section);
    }

    /**
     * Resolves the selected plot block back to its sublevel and supplies both the camera-relative
     * event transform used by NeoForge highlights and the local transform used by Radiance's own
     * outline mesh.
     */
    public static BlockOutlineContext blockOutlineContext(ClientLevel level, Camera camera,
        BlockPos blockPos, float partialTick) {
        Object subLevel = blockSubLevel(level, blockPos);
        if (subLevel == null) {
            return null;
        }

        Object pose = invoke(subLevel, "renderPose", new Class<?>[] {float.class}, partialTick);
        PoseComponents components = readPose(pose);
        if (components == null) {
            return null;
        }

        Vec3 localCamera = localCameraPosition(camera.getPosition(), components);
        Camera eventCamera = createBlockOutlineCamera(camera, pose);
        return new BlockOutlineContext(eventCamera, new Quaternionf(components.orientation),
            new Vector3d(components.scale), new Vector3d(components.rotationPoint),
            new Vector3d(components.position), localCamera, eventCamera != camera);
    }

    public static void finishBlockOutlineContext(BlockOutlineContext context) {
        if (context == null || !context.adaptedCamera) {
            return;
        }
        try {
            Method clearMethod = clearBlockOutlineCamera;
            Camera adaptedCamera = blockOutlineCamera;
            if (clearMethod != null && adaptedCamera != null) {
                clearMethod.invoke(adaptedCamera);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            failReflection("failed to clear Sable block outline camera", e);
        }
    }

    /**
     * Draws Sable's F3+B sub-level debug boxes. Sable renders these inside
     * {@code LevelRenderer.renderLevel} TAIL, which Radiance replaces wholesale, so the bridge
     * re-emits the same camera-relative line boxes through the world debug-line path.
     */
    public static void queueDebugBoxes(ClientLevel level, Camera camera, float partialTick) {
        Collection<?> subLevels = getSubLevels(level);
        if (subLevels == null || subLevels.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        Vec3 cameraPosition = camera.getPosition();
        // The grey bound box is axis-aligned in camera-relative world: it only follows the
        // sub-level position, so it keeps the world-origin line frame.
        StorageVertexConsumerProvider axisStorage = new StorageVertexConsumerProvider(4096, 1.0F);
        try {
            for (Object subLevel : subLevels) {
                Bounds bounds = readBounds(tryInvoke(subLevel, "boundingBox"));
                if (bounds == null) {
                    continue;
                }
                LevelRenderer.renderLineBox(pose, axisStorage.getBuffer(RenderType.lines()),
                    bounds.minX() - cameraPosition.x, bounds.minY() - cameraPosition.y,
                    bounds.minZ() - cameraPosition.z,
                    bounds.maxX() - cameraPosition.x, bounds.maxY() - cameraPosition.y,
                    bounds.maxZ() - cameraPosition.z,
                    0.5F, 0.5F, 0.5F, 0.7F);

                Object plot = tryInvoke(subLevel, "getPlot");
                Bounds plotBounds = plot == null ? null
                    : readBounds(tryInvoke(plot, "getBoundingBox"));
                PoseComponents renderPose = readPose(tryInvoke(subLevel, "renderPose",
                    new Class<?>[] {float.class}, partialTick));
                if (plotBounds == null || renderPose == null) {
                    continue;
                }

                // The plot box and rotation marker are drawn in the sub-level's own frame, so they
                // carry its full orientation.
                StorageVertexConsumerProvider posedStorage =
                    new StorageVertexConsumerProvider(4096, 1.0F);
                VertexConsumer consumer = posedStorage.getBuffer(RenderType.lines());
                Quaternionf orientation = new Quaternionf(renderPose.orientation);
                pose.pushPose();
                try {
                    pose.translate(renderPose.position.x - cameraPosition.x,
                        renderPose.position.y - cameraPosition.y,
                        renderPose.position.z - cameraPosition.z);
                    pose.mulPose(orientation);
                    LevelRenderer.renderLineBox(pose, consumer,
                        plotBounds.minX() - renderPose.rotationPoint.x,
                        plotBounds.minY() - renderPose.rotationPoint.y,
                        plotBounds.minZ() - renderPose.rotationPoint.z,
                        plotBounds.maxX() + 1.0 - renderPose.rotationPoint.x,
                        plotBounds.maxY() + 1.0 - renderPose.rotationPoint.y,
                        plotBounds.maxZ() + 1.0 - renderPose.rotationPoint.z,
                        0.9F, 0.5F, 0.5F, 1.0F);
                    LevelRenderer.renderLineBox(pose, consumer,
                        -2.0F / 16.0F, -2.0F / 16.0F, -2.0F / 16.0F,
                        2.0F / 16.0F, 2.0F / 16.0F, 2.0F / 16.0F,
                        0.7F, 0.7F, 0.5F, 1.0F);
                } finally {
                    pose.popPose();
                }

                EntityProxy.queueDefaultWorldDebugLines(posedStorage, 0.0, 0.0, 0.0,
                    "sable_sublevel", com.radiance.client.constant.Constants.Coordinates.CAMERA_SHIFT,
                    EntityProxy.lineFrameFromOrientation(orientation));
            }
        } catch (RuntimeException | Error failure) {
            axisStorage.close();
            throw failure;
        }
        EntityProxy.queueDefaultWorldDebugLines(axisStorage, 0.0, 0.0, 0.0, "sable_sublevel",
            com.radiance.client.constant.Constants.Coordinates.CAMERA_SHIFT, null);
    }

    private static Bounds readBounds(Object bounds) {
        if (bounds == null) {
            return null;
        }
        return new Bounds(component(bounds, "minX"), component(bounds, "minY"),
            component(bounds, "minZ"), component(bounds, "maxX"), component(bounds, "maxY"),
            component(bounds, "maxZ"));
    }

    private static Object tryInvoke(Object target, String methodName) {
        return tryInvoke(target, methodName, new Class<?>[0]);
    }

    private static Object tryInvoke(Object target, String methodName, Class<?>[] parameterTypes,
        Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY,
                          double maxZ) {
    }

    /**
     * Radiance 直接调用 {@link net.minecraft.client.renderer.entity.EntityRenderDispatcher}，
     * 会绕过 Sable 注入在 {@code LevelRenderer.renderEntity} 上的位姿变换。这里复现该变换，
     * 让实体网格仍以自身原点捕获，而由 Radiance 把原点放到 sublevel 的渲染位置。
     */
    public static EntityRenderTransform entityRenderTransform(Entity entity, float partialTick,
        double interpolatedX, double interpolatedY, double interpolatedZ) {
        Object contained = entitySubLevel(entity, false);
        if (contained != null) {
            PoseComponents renderPose = readPose(
                invoke(contained, "renderPose", new Class<?>[] {float.class}, partialTick));
            if (renderPose == null) {
                return null;
            }

            Vector3d worldPosition = transformPosition(
                new Vector3d(interpolatedX, interpolatedY, interpolatedZ), renderPose);
            return new EntityRenderTransform(worldPosition,
                new Quaternionf(renderPose.orientation), true);
        }

        Object tracking = entitySubLevel(entity, true);
        if (tracking == null || entity.isPassenger()) {
            return null;
        }

        PoseComponents lastPose = readPose(invoke(tracking, "lastPose"));
        PoseComponents logicalPose = readPose(invoke(tracking, "logicalPose"));
        PoseComponents renderPose = readPose(
            invoke(tracking, "renderPose", new Class<?>[] {float.class}, partialTick));
        if (lastPose == null || logicalPose == null || renderPose == null) {
            return null;
        }

        Vector3d previousLocal = inverseTransformPosition(
            new Vector3d(entity.xOld, entity.yOld, entity.zOld), lastPose);
        Vector3d currentLocal = inverseTransformPosition(
            new Vector3d(entity.getX(), entity.getY(), entity.getZ()), logicalPose);
        Vector3d interpolatedLocal = new Vector3d(
            Mth.lerp(partialTick, previousLocal.x, currentLocal.x),
            Mth.lerp(partialTick, previousLocal.y, currentLocal.y),
            Mth.lerp(partialTick, previousLocal.z, currentLocal.z));
        return new EntityRenderTransform(transformPosition(interpolatedLocal, renderPose),
            new Quaternionf(), false);
    }

    public static void queueBlockEntities(ClientLevel level, Camera camera,
        BlockEntityRenderDispatcher dispatcher, float partialTick) {
        Collection<?> subLevels = getSubLevels(level);
        if (subLevels == null || subLevels.isEmpty()) {
            return;
        }

        try {
            for (Object subLevel : subLevels) {
                Object renderData = invoke(subLevel, "getRenderData");
                if (!isSupportedRenderData(renderData)) {
                    continue;
                }

                Object pose = invoke(subLevel, "renderPose", new Class<?>[] {float.class}, partialTick);
                PoseComponents components = readPose(pose);
                if (components == null) {
                    continue;
                }

                Set<BlockEntity> blockEntities = Collections.newSetFromMap(new IdentityHashMap<>());
                collectBlockEntities(subLevel, blockEntities);
                if (blockEntities.isEmpty()) {
                    continue;
                }

                setBlockEntityCamera(dispatcher, localCameraPosition(camera.getPosition(), components));
                Matrix4f localTransform = new Matrix4f()
                    .rotate(new Quaternionf(components.orientation))
                    .scale((float) components.scale.x, (float) components.scale.y,
                        (float) components.scale.z);
                EntityProxy.queueTransformedBlockEntities(blockEntities, dispatcher, partialTick,
                    localTransform, components.rotationPoint, components.position,
                    System.identityHashCode(subLevel));
            }
        } finally {
            setBlockEntityCamera(dispatcher, null);
        }
    }

    private static void collectBlockEntities(Object subLevel, Set<BlockEntity> destination) {
        Object plot = invoke(subLevel, "getPlot");
        Object loadedChunksValue = invoke(plot, "getLoadedChunks");
        if (!(loadedChunksValue instanceof Collection<?> loadedChunks)) {
            return;
        }

        for (Object holder : loadedChunks) {
            Object chunkValue = invoke(holder, "getChunk");
            if (chunkValue instanceof LevelChunk chunk) {
                destination.addAll(chunk.getBlockEntities().values());
            }
        }
    }

    private static Collection<?> getSubLevels(ClientLevel level) {
        try {
            Method containerMethod = getContainer;
            if (containerMethod == null) {
                Class<?> containerClass = Class.forName(
                    "dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                containerMethod = containerClass.getMethod("getContainer", ClientLevel.class);
                getContainer = containerMethod;
            }

            Object container = containerMethod.invoke(null, level);
            if (container == null) {
                return Collections.emptyList();
            }

            Method allSubLevelsMethod = getAllSubLevels;
            if (allSubLevelsMethod == null
                || !allSubLevelsMethod.getDeclaringClass().isAssignableFrom(container.getClass())) {
                allSubLevelsMethod = container.getClass().getMethod("getAllSubLevels");
                getAllSubLevels = allSubLevelsMethod;
            }

            Object value = allSubLevelsMethod.invoke(container);
            return value instanceof Collection<?> collection ? collection : Collections.emptyList();
        } catch (ClassNotFoundException e) {
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) failReflection("installed Sable class is missing", e);
            return null;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            failReflection("failed to enumerate Sable client sublevels", e);
            return Collections.emptyList();
        }
    }

    private static Object entitySubLevel(Entity entity, boolean tracking) {
        try {
            Object helper = sableHelper;
            if (helper == null) {
                Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
                helper = sableClass.getField("HELPER").get(null);
                sableHelper = helper;
            }

            Method method = tracking ? getTrackingSubLevel : getContainingEntity;
            if (method == null
                || !method.getDeclaringClass().isAssignableFrom(helper.getClass())) {
                method = helper.getClass().getMethod(
                    tracking ? "getTrackingSubLevel" : "getContaining", Entity.class);
                if (tracking) {
                    getTrackingSubLevel = method;
                } else {
                    getContainingEntity = method;
                }
            }
            return method.invoke(helper, entity);
        } catch (ClassNotFoundException e) {
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) failReflection("installed Sable class is missing", e);
            return null;
        } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException
                 | InvocationTargetException e) {
            failReflection("failed to resolve Sable entity sublevel", e);
            return null;
        }
    }

    private static Object blockSubLevel(ClientLevel level, BlockPos blockPos) {
        try {
            Object helper = sableHelper;
            if (helper == null) {
                Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
                helper = sableClass.getField("HELPER").get(null);
                sableHelper = helper;
            }

            Method method = getContainingBlock;
            if (method == null
                || !method.getDeclaringClass().isAssignableFrom(helper.getClass())) {
                method = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);
                getContainingBlock = method;
            }
            return method.invoke(helper, level, blockPos);
        } catch (ClassNotFoundException e) {
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) failReflection("installed Sable class is missing", e);
            return null;
        } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException
                 | InvocationTargetException e) {
            failReflection("failed to resolve Sable block sublevel", e);
            return null;
        }
    }

    private static boolean isSupportedRenderData(Object renderData) {
        return isChunkedRenderData(renderData) || isSingleRenderData(renderData);
    }

    private static boolean isChunkedRenderData(Object renderData) {
        return renderData != null
            && VANILLA_RENDER_DATA.equals(renderData.getClass().getName());
    }

    private static boolean isSingleRenderData(Object renderData) {
        return renderData != null
            && VANILLA_SINGLE_RENDER_DATA.equals(renderData.getClass().getName());
    }

    private static void invokeRenderSingleBlock(Object renderData, RenderType renderType,
        VertexConsumer consumer, double renderX, double renderY, double renderZ) {
        try {
            Method method = renderSingleBlock;
            if (method == null
                || !method.getDeclaringClass().isAssignableFrom(renderData.getClass())) {
                method = renderData.getClass().getMethod("renderSingleBlock", RenderType.class,
                    VertexConsumer.class, Matrix4f.class, double.class, double.class, double.class);
                renderSingleBlock = method;
            }
            method.invoke(renderData, renderType, consumer, new Matrix4f(),
                renderX, renderY, renderZ);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            failReflection("failed to render Sable single-block sublevel", e);
        }
    }

    private static Camera createBlockOutlineCamera(Camera camera, Object pose) {
        try {
            Camera adaptedCamera = blockOutlineCamera;
            if (adaptedCamera == null) {
                synchronized (SableSubLevelBridge.class) {
                    adaptedCamera = blockOutlineCamera;
                    if (adaptedCamera == null) {
                        Class<?> cameraClass = Class.forName(
                            "dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera");
                        adaptedCamera = (Camera) cameraClass.getDeclaredConstructor().newInstance();
                        setBlockOutlineCamera = cameraClass.getMethod("setCamera", Camera.class);
                        for (Method candidate : cameraClass.getMethods()) {
                            if (candidate.getName().equals("setPose")
                                && candidate.getParameterCount() == 1) {
                                setBlockOutlinePose = candidate;
                                break;
                            }
                        }
                        clearBlockOutlineCamera = cameraClass.getMethod("clear");
                        blockOutlineCamera = adaptedCamera;
                    }
                }
            }

            Method setCameraMethod = setBlockOutlineCamera;
            Method setPoseMethod = setBlockOutlinePose;
            if (setCameraMethod == null || setPoseMethod == null) {
                return camera;
            }
            setCameraMethod.invoke(adaptedCamera, camera);
            setPoseMethod.invoke(adaptedCamera, pose);
            return adaptedCamera;
        } catch (ClassNotFoundException e) {
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) failReflection("installed Sable class is missing", e);
            return camera;
        } catch (ReflectiveOperationException e) {
            failReflection("failed to prepare Sable block outline camera", e);
            return camera;
        }
    }

    private static PoseComponents readPose(Object pose) {
        if (pose == null) {
            return null;
        }

        Object position = invoke(pose, "position");
        Object orientation = invoke(pose, "orientation");
        Object rotationPoint = invoke(pose, "rotationPoint");
        Object scale = invoke(pose, "scale");
        if (position == null || orientation == null || rotationPoint == null || scale == null) {
            return null;
        }

        return new PoseComponents(
            vector(position),
            new Quaterniond(component(orientation, "x"), component(orientation, "y"),
                component(orientation, "z"), component(orientation, "w")).normalize(),
            vector(rotationPoint),
            vector(scale));
    }

    private static Matrix4d sectionTransform(BlockPos sectionOrigin, PoseComponents pose) {
        return new Matrix4d()
            .translation(pose.position)
            .rotate(pose.orientation)
            .scale(pose.scale)
            .translate(sectionOrigin.getX() - pose.rotationPoint.x,
                sectionOrigin.getY() - pose.rotationPoint.y,
                sectionOrigin.getZ() - pose.rotationPoint.z);
    }

    private static Vector3d transformPosition(Vector3d position, PoseComponents pose) {
        // Analytic form of Pose3dc#transformPosition. Matrix4d#invert() is not used anywhere here:
        // at plot-scale translations (~2e7) JOML's fast inverse path is inaccurate and corrupted
        // the sub-level entity placement by multiple blocks.
        Vector3d local = position.sub(pose.rotationPoint, position).mul(pose.scale);
        pose.orientation.transform(local, local);
        return local.add(pose.position);
    }

    private static Vector3d inverseTransformPosition(Vector3d position, PoseComponents pose) {
        Vector3d local = position.sub(pose.position, position);
        pose.orientation.transformInverse(local, local);
        local.mul(1.0 / pose.scale.x, 1.0 / pose.scale.y, 1.0 / pose.scale.z);
        return local.add(pose.rotationPoint);
    }

    private static Vec3 localCameraPosition(Vec3 cameraPosition, PoseComponents pose) {
        Vector3d local = new Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z)
            .sub(pose.position);
        pose.orientation.transformInverse(local, local);
        local.mul(1.0 / pose.scale.x, 1.0 / pose.scale.y, 1.0 / pose.scale.z).add(pose.rotationPoint);
        return new Vec3(local.x, local.y, local.z);
    }

    private static void setBlockEntityCamera(BlockEntityRenderDispatcher dispatcher, Vec3 position) {
        try {
            Method method = setBlockEntityCameraPosition;
            if (method == null) {
                method = dispatcher.getClass().getMethod("sable$setCameraPosition", Vec3.class);
                setBlockEntityCameraPosition = method;
            }
            method.invoke(dispatcher, position);
        } catch (NoSuchMethodException e) {
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
                failReflection("installed Sable block entity camera extension is missing", e);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            failReflection("failed to update Sable block entity camera", e);
        }
    }

    private static Vector3d vector(Object value) {
        return new Vector3d(component(value, "x"), component(value, "y"), component(value, "z"));
    }

    private static double component(Object value, String methodName) {
        Object component = invoke(value, methodName);
        return component instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes,
        Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, arguments);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            failReflection("failed to invoke " + methodName + " on "
                + target.getClass().getName(), e);
            return null;
        }
    }

    private static void failReflection(String message, ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException invocation ? invocation.getCause() : e;
        if (cause instanceof Error error) throw error;
        throw new IllegalStateException("Sable render bridge " + message, cause);
    }

    private record PoseComponents(Vector3d position, Quaterniond orientation,
                                  Vector3d rotationPoint, Vector3d scale) {
    }

    /**
     * @param position     world-space position of the rendered geometry origin
     * @param orientation  model rotation applied by the host renderer (body pose)
     * @param rotatesModel whether the caller must apply {@code orientation} to the body and to the
     *                     captured line frame. Tracking entities are only repositioned by the host,
     *                     so their lines keep the world-origin frame.
     */
    public record EntityRenderTransform(Vector3d position, Quaternionf orientation,
                                        boolean rotatesModel) {
    }

    public record BlockOutlineContext(Camera camera, Quaternionf orientation, Vector3d scale,
                                      Vector3d rotationPoint, Vector3d worldPosition,
                                      Vec3 localCameraPosition, boolean adaptedCamera) {

        public void applyEventTransform(PoseStack poseStack, Vec3 realCameraPosition) {
            poseStack.translate(worldPosition.x - realCameraPosition.x,
                worldPosition.y - realCameraPosition.y,
                worldPosition.z - realCameraPosition.z);
            poseStack.mulPose(orientation);
            poseStack.translate(localCameraPosition.x - rotationPoint.x,
                localCameraPosition.y - rotationPoint.y,
                localCameraPosition.z - rotationPoint.z);
            poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        }

        public void applyLocalTransform(PoseStack poseStack, BlockPos blockPos) {
            poseStack.mulPose(orientation);
            poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
            poseStack.translate(blockPos.getX() - rotationPoint.x,
                blockPos.getY() - rotationPoint.y,
                blockPos.getZ() - rotationPoint.z);
        }
    }
}
