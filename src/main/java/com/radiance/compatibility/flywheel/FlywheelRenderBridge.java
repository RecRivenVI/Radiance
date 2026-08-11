package com.radiance.compatibility.flywheel;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Optional-dependency bridge restoring Flywheel's dispatcher calls in the replaced LevelRenderer. */
public final class FlywheelRenderBridge {
    private static volatile Api api;

    private FlywheelRenderBridge() {
    }

    public static Frame begin(LevelRenderer renderer, ClientLevel level, RenderBuffers buffers,
        Matrix4fc modelView, Matrix4f projection, Camera camera, DeltaTracker deltaTracker) {
        Api api = api();
        if (api == null) return Frame.EMPTY;
        Object manager = api.invoke(api.managerGet, null, level);
        if (manager == null) return Frame.EMPTY;
        Object context = api.invoke(api.contextCreate, null, renderer, level, buffers, modelView,
            projection, camera, deltaTracker.getGameTimeDeltaPartialTick(false));
        Object dispatcher = api.invoke(api.managerDispatcher, manager);
        api.invoke(api.onStart, dispatcher, context);
        return new Frame(api, dispatcher, context);
    }

    public static boolean shouldSkipVanillaEntity(ClientLevel level, Entity entity) {
        Api api = api();
        if (api == null) return false;
        Object manager = api.invoke(api.managerGet, null, level);
        return manager != null && (Boolean) api.invoke(api.skipVanillaEntity, null, entity);
    }

    /** Uses Flywheel's own decision because a visualizer may retain vanilla-rendered parts. */
    public static boolean shouldSkipVanillaBlockEntity(ClientLevel level,
        BlockEntity blockEntity) {
        Api api = api();
        if (api == null) return false;
        Object manager = api.invoke(api.managerGet, null, level);
        return manager != null && (Boolean) api.invoke(api.skipVanillaBlockEntity, null,
            blockEntity);
    }

    private static Api api() {
        if (!ModList.get().isLoaded("flywheel")) return null;
        Api resolved = api;
        if (resolved != null) return resolved;
        synchronized (FlywheelRenderBridge.class) {
            if (api != null) return api;
            try {
                api = new Api();
                return api;
            } catch (ReflectiveOperationException | LinkageError missing) {
                throw new IllegalStateException("Installed Flywheel dispatcher API could not be linked", missing);
            }
        }
    }

    public static final class Frame {
        private static final Frame EMPTY = new Frame(null, null, null);
        private final Api api;
        private final Object dispatcher;
        private final Object context;

        private Frame(Api api, Object dispatcher, Object context) {
            this.api = api;
            this.dispatcher = dispatcher;
            this.context = context;
        }

        public void afterEntities() {
            if (api != null) api.invoke(api.afterEntities, dispatcher, context);
        }

        public void beforeCrumbling(
            Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress) {
            if (api != null) api.invoke(api.beforeCrumbling, dispatcher, context,
                destructionProgress);
        }

        public boolean active() {
            return api != null;
        }
    }

    private static final class Api {
        private final Method managerGet;
        private final Method managerDispatcher;
        private final Method contextCreate;
        private final Method onStart;
        private final Method afterEntities;
        private final Method beforeCrumbling;
        private final Method skipVanillaEntity;
        private final Method skipVanillaBlockEntity;

        private Api() throws ReflectiveOperationException {
            Class<?> manager = Class.forName(
                "dev.engine_room.flywheel.api.visualization.VisualizationManager");
            Class<?> dispatcher = Class.forName(
                "dev.engine_room.flywheel.api.visualization.VisualizationManager$RenderDispatcher");
            Class<?> renderContext = Class.forName("dev.engine_room.flywheel.api.backend.RenderContext");
            Class<?> contextImpl = Class.forName(
                "dev.engine_room.flywheel.impl.event.RenderContextImpl");
            Class<?> visualizationHelper = Class.forName(
                "dev.engine_room.flywheel.lib.visualization.VisualizationHelper");
            managerGet = manager.getMethod("get", net.minecraft.world.level.LevelAccessor.class);
            managerDispatcher = manager.getMethod("renderDispatcher");
            contextCreate = contextImpl.getMethod("create", LevelRenderer.class, ClientLevel.class,
                RenderBuffers.class, Matrix4fc.class, Matrix4f.class, Camera.class, float.class);
            onStart = dispatcher.getMethod("onStartLevelRender", renderContext);
            afterEntities = dispatcher.getMethod("afterEntities", renderContext);
            beforeCrumbling = dispatcher.getMethod("beforeCrumbling", renderContext,
                Long2ObjectMap.class);
            skipVanillaEntity = visualizationHelper.getMethod("skipVanillaRender", Entity.class);
            skipVanillaBlockEntity = visualizationHelper.getMethod("skipVanillaRender",
                BlockEntity.class);
        }

        private Object invoke(Method method, Object owner, Object... args) {
            try {
                return method.invoke(owner, args);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access Flywheel dispatcher API", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Flywheel dispatcher failed", cause);
            }
        }
    }
}
