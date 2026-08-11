package com.radiance.compatibility.flywheel;

import com.radiance.client.proxy.world.NativeInstancingProxy;
import com.radiance.mixins.compatibility.flywheel.FlywheelRenderSystemLightsAccessor;
import com.radiance.mixins.compatibility.flywheel.FlywheelLevelRendererTicksAccessor;
import com.radiance.mixins.compatibility.flywheel.FlywheelLightTextureAccessor;
import dev.engine_room.flywheel.api.backend.Engine;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.task.UnitPlan;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class RadianceFlywheelEngine implements Engine {
    private static final int MAX_ORIGIN_DISTANCE_SQUARED = 256 * 256;
    /** Default on; false keeps every model until the level unloads (A/B and fallback only). */
    private static final boolean RELEASE_UNUSED_MODELS =
        Boolean.parseBoolean(System.getProperty("radiance.flywheelModelRelease", "true"));
    private static final Object INSTANCE_TRANSFER_LOCK = new Object();
    private final ClientLevel level;
    private final long nativeEngine;
    private final AtomicLong instanceIds = new AtomicLong(1);
    private final Map<Model, ModelState> models = new IdentityHashMap<>();
    private final Map<InstancerKey, RadianceInstancer<?>> instancers = new LinkedHashMap<>();
    private final GlobalContext globalContext = new GlobalContext();
    private Vec3i renderOrigin = Vec3i.ZERO;
    private boolean deleted;

    RadianceFlywheelEngine(LevelAccessor level) {
        if (!(level instanceof ClientLevel clientLevel)) {
            throw new IllegalArgumentException("Radiance Flywheel backend requires ClientLevel");
        }
        this.level = clientLevel;
        this.nativeEngine = NativeInstancingProxy.createEngine();
        if (nativeEngine == 0) throw new IllegalStateException("Native instancing engine was not created");
    }

    @Override
    public VisualizationContext createVisualizationContext() {
        ensureLive();
        return globalContext;
    }

    @Override
    public Plan<RenderContext> createFramePlan() {
        return UnitPlan.of();
    }

    @Override
    public Vec3i renderOrigin() {
        return renderOrigin;
    }

    @Override
    public boolean updateRenderOrigin(Camera camera) {
        Vec3 cameraPos = camera.getPosition();
        double dx = renderOrigin.getX() - cameraPos.x;
        double dy = renderOrigin.getY() - cameraPos.y;
        double dz = renderOrigin.getZ() - cameraPos.z;
        if (dx * dx + dy * dy + dz * dz <= MAX_ORIGIN_DISTANCE_SQUARED) return false;
        renderOrigin = BlockPos.containing(cameraPos);
        synchronized (this) {
            instancers.values().forEach(RadianceInstancer::markAllDirty);
        }
        return true;
    }

    @Override
    public synchronized void lightSections(LongSet sections) {
        ensureLive();
    }

    @Override
    public void onLightUpdate(SectionPos sectionPos, LightLayer layer) {
        ensureLive();
    }

    @Override
    public synchronized void render(RenderContext context) {
        ensureLive();
        NativeInstancingProxy.beginFrame(nativeEngine, renderOrigin.getX(), renderOrigin.getY(),
            renderOrigin.getZ(), renderTicks(context));
        uploadShaderLights();
        uploadPendingModels();
        for (RadianceInstancer<?> instancer : instancers.values()) instancer.flush();
        NativeInstancingProxy.render(nativeEngine);
        if (removeEmptyInstancers()) releaseUnusedModels();
    }

    @Override
    public synchronized void renderCrumbling(RenderContext context,
        List<CrumblingBlock> crumblingBlocks) {
        ensureLive();
        int count = crumblingBlocks.stream().mapToInt(block -> block.instances().size()).sum();
        if (count == 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer instances = stack.mallocLong(count);
            LongBuffer positions = stack.mallocLong(count);
            IntBuffer progress = stack.mallocInt(count);
            IntBuffer textures = stack.mallocInt(count);
            for (CrumblingBlock block : crumblingBlocks) {
                for (Instance instance : block.instances()) {
                    if (instance.handle() instanceof Handle<?> handle && !handle.deleted) {
                        instances.put(handle.id);
                        positions.put(block.pos().asLong());
                        progress.put(block.progress());
                        var texture = net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                            "textures/block/destroy_stage_" + block.progress() + ".png");
                        textures.put(net.minecraft.client.Minecraft.getInstance().getTextureManager()
                            .getTexture(texture).getId());
                    }
                }
            }
            int actual = instances.position();
            instances.flip();
            positions.flip();
            progress.flip();
            textures.flip();
            if (actual > 0) {
                NativeInstancingProxy.renderCrumbling(nativeEngine, MemoryUtil.memAddress(instances),
                    MemoryUtil.memAddress(positions), MemoryUtil.memAddress(progress),
                    MemoryUtil.memAddress(textures), actual);
            }
        }
    }

    @Override
    public synchronized void delete() {
        if (deleted) return;
        deleted = true;
        for (RadianceInstancer<?> instancer : instancers.values()) instancer.deleteAll();
        instancers.clear();
        for (ModelState model : models.values()) model.close();
        models.clear();
        NativeInstancingProxy.deleteEngine(nativeEngine);
    }

    private synchronized <I extends Instance> Instancer<I> instancer(Context context,
        InstanceType<I> type, Model model, int bias) {
        ensureLive();
        FlywheelInstanceAdapter adapter = FlywheelInstanceAdapter.require(type);
        ModelState modelState = models.computeIfAbsent(model, ModelState::capture);
        InstancerKey key = new InstancerKey(context, type, model, bias);
        @SuppressWarnings("unchecked")
        RadianceInstancer<I> result = (RadianceInstancer<I>) instancers.computeIfAbsent(key,
            ignored -> new RadianceInstancer<>(type, adapter, model, modelState, bias, context));
        return result;
    }

    /**
     * Same clock as Flywheel's frame uniforms (LevelRenderer ticks plus partial tick). The world
     * game time is unsuitable: it grows without bound and loses sub-tick precision in float.
     */
    static double renderTicks(RenderContext context) {
        return ((FlywheelLevelRendererTicksAccessor) context.renderer()).radiance$getTicks()
            + (double) context.partialTick();
    }

    private void uploadPendingModels() {
        for (ModelState model : models.values()) model.upload(nativeEngine);
    }

    private void uploadShaderLights() {
        Vector3f[] directions = FlywheelRenderSystemLightsAccessor.radiance$getShaderLightDirections();
        if (directions == null || directions.length != 2 || directions[0] == null
            || directions[1] == null) {
            throw new IllegalStateException("Minecraft shader light directions are not initialized");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var values = stack.mallocFloat(6);
            values.put(0, directions[0].x());
            values.put(1, directions[0].y());
            values.put(2, directions[0].z());
            values.put(3, directions[1].x());
            values.put(4, directions[1].y());
            values.put(5, directions[1].z());
            int lightTexture = ((FlywheelLightTextureAccessor) (Object)
                net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture())
                .radiance$getLightTexture().getId();
            NativeInstancingProxy.setShaderLights(nativeEngine, MemoryUtil.memAddress(values),
                lightTexture, level.effects().constantAmbientLight());
        }
    }

    /**
     * Like Flywheel's own backends, an instancer is dropped once it has no instances. Keeping it
     * would pin its model until the level unloads, which for per-structure models (contraptions,
     * unloaded sub-levels) grows with every structure ever seen. A visual that still holds a
     * dropped instancer is forwarded to the live instancer for the same key.
     */
    private boolean removeEmptyInstancers() {
        if (!RELEASE_UNUSED_MODELS) {
            instancers.values().removeIf(instancer -> instancer.instances.isEmpty() && instancer.context.deleted());
            return false;
        }
        return instancers.values().removeIf(RadianceInstancer::detachIfEmpty);
    }

    /** Frees the native mesh, BLAS and buffers of every model no remaining instancer uses. */
    private void releaseUnusedModels() {
        Set<ModelState> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RadianceInstancer<?> instancer : instancers.values()) used.add(instancer.model);
        Iterator<ModelState> iterator = models.values().iterator();
        while (iterator.hasNext()) {
            ModelState model = iterator.next();
            if (used.contains(model)) continue;
            iterator.remove();
            model.release(nativeEngine);
        }
    }

    private void ensureLive() {
        if (deleted) throw new IllegalStateException("Flywheel engine is deleted");
    }

    private interface Context {
        InstancerProvider provider();
        Vec3i origin();
        void writeComposed(Matrix4f pose, Matrix3f normal);
        FlywheelEmbeddingLighting.Snapshot lighting();
        boolean deleted();
        boolean dependsOn(Context ancestor);
    }

    private final class GlobalContext implements VisualizationContext, Context {
        private final InstancerProvider provider = this::instancer;

        private <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model,
            int bias) {
            return RadianceFlywheelEngine.this.instancer(this, type, model, bias);
        }

        @Override public InstancerProvider instancerProvider() { return provider; }
        @Override public InstancerProvider provider() { return provider; }
        @Override public Vec3i renderOrigin() { return RadianceFlywheelEngine.this.renderOrigin; }
        @Override public Vec3i origin() { return renderOrigin(); }
        @Override public boolean deleted() { return false; }
        @Override public boolean dependsOn(Context ancestor) { return this == ancestor; }
        @Override public FlywheelEmbeddingLighting.Snapshot lighting() { return null; }
        @Override public void writeComposed(Matrix4f pose, Matrix3f normal) {
            pose.identity();
            normal.identity();
        }
        @Override public VisualEmbedding createEmbedding(Vec3i origin) {
            return new Embedding(this, origin);
        }
    }

    private final class Embedding implements VisualEmbedding, Context, FlywheelEmbeddingLightingAccess {
        private final Context parent;
        private final Vec3i origin;
        private final InstancerProvider provider = this::instancer;
        private final Matrix4f pose = new Matrix4f();
        private final Matrix3f normal = new Matrix3f();
        private final FlywheelEmbeddingLighting lighting = new FlywheelEmbeddingLighting();
        private volatile boolean deleted;

        private Embedding(Context parent, Vec3i origin) {
            this.parent = parent;
            this.origin = origin;
        }

        private <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model,
            int bias) {
            if (deleted) throw new IllegalStateException("Cannot create an instancer in deleted embedding");
            return RadianceFlywheelEngine.this.instancer(this, type, model, bias);
        }

        @Override public void transforms(Matrix4fc pose, Matrix3fc normal) {
            synchronized (RadianceFlywheelEngine.this) {
                this.pose.set(pose);
                this.normal.set(normal);
                markDependentInstancesDirty();
            }
        }
        @Override public void radiance$setEmbeddingLighting(Matrix4fc sceneMatrix, int scene,
            float skyLightScale) {
            synchronized (RadianceFlywheelEngine.this) {
                lighting.set(sceneMatrix, scene, skyLightScale);
                markDependentInstancesDirty();
            }
        }
        private void markDependentInstancesDirty() {
            instancers.values().stream().filter(i -> i.context.dependsOn(this))
                .forEach(RadianceInstancer::markAllDirty);
        }
        @Override public InstancerProvider instancerProvider() { return provider; }
        @Override public InstancerProvider provider() { return provider; }
        @Override public Vec3i renderOrigin() { return origin; }
        @Override public Vec3i origin() { return origin; }
        @Override public VisualEmbedding createEmbedding(Vec3i childOrigin) {
            if (deleted) throw new IllegalStateException("Cannot nest a deleted embedding");
            return new Embedding(this, childOrigin);
        }
        @Override public void delete() {
            synchronized (RadianceFlywheelEngine.this) {
                deleted = true;
                markDependentInstancesDirty();
            }
        }
        @Override public boolean deleted() { return deleted || parent.deleted(); }
        @Override public boolean dependsOn(Context ancestor) {
            return this == ancestor || parent.dependsOn(ancestor);
        }
        @Override public void writeComposed(Matrix4f outPose, Matrix3f outNormal) {
            parent.writeComposed(outPose, outNormal);
            outPose.mul(pose);
            outNormal.mul(normal);
        }
        @Override public FlywheelEmbeddingLighting.Snapshot lighting() {
            Matrix4f composed = new Matrix4f();
            writeComposed(composed, new Matrix3f());
            return lighting.resolve(parent.lighting(), pose, composed);
        }
    }

    private final class RadianceInstancer<I extends Instance> implements Instancer<I> {
        private final InstanceType<I> type;
        private final FlywheelInstanceAdapter adapter;
        private final Model source;
        private final ModelState model;
        private final int bias;
        private final Context context;
        private final List<Handle<I>> instances = new ArrayList<>();
        /** Set under this lock and the engine lock when the engine drops this empty instancer. */
        private boolean detached;

        private RadianceInstancer(InstanceType<I> type, FlywheelInstanceAdapter adapter,
            Model source, ModelState model, int bias, Context context) {
            this.type = type;
            this.adapter = adapter;
            this.source = source;
            this.model = model;
            this.bias = bias;
            this.context = context;
        }

        @Override public I createInstance() {
            synchronized (this) {
                if (!detached) {
                    if (context.deleted()) throw new IllegalStateException("Embedding is deleted");
                    Handle<I> handle = new Handle<>(instanceIds.getAndIncrement(), this);
                    I instance = type.create(handle);
                    handle.instance = Objects.requireNonNull(instance, "InstanceType.create returned null");
                    instances.add(handle);
                    return instance;
                }
            }
            // Not under this lock: the engine lock is always taken before an instancer lock.
            return live().createInstance();
        }

        /** The engine's current instancer for this key; recreates it (and its model) if dropped. */
        private RadianceInstancer<I> live() {
            return (RadianceInstancer<I>) RadianceFlywheelEngine.this.instancer(context, type, source, bias);
        }

        private synchronized boolean detachIfEmpty() {
            if (!instances.isEmpty()) return false;
            detached = true;
            return true;
        }

        @Override public void stealInstance(I instance) {
            if (instance == null) return;
            if (!(instance.handle() instanceof Handle<?> raw)) {
                throw new IllegalArgumentException("Instance belongs to another backend");
            }
            @SuppressWarnings("unchecked") Handle<I> handle = (Handle<I>) raw;
            synchronized (INSTANCE_TRANSFER_LOCK) {
                RadianceFlywheelEngine oldEngine = handle.engine;
                RadianceFlywheelEngine newEngine = RadianceFlywheelEngine.this;
                synchronized (oldEngine) {
                    synchronized (newEngine) {
                        if (handle.owner == this) return;
                        // Detaching holds this engine's lock, so the flag is stable here.
                        if (detached) {
                            live().stealInstance(instance);
                            return;
                        }
                        if (handle.owner.type != this.type) {
                            throw new IllegalArgumentException(
                                "Cannot steal a Flywheel instance into a different InstanceType");
                        }
                        oldEngine.ensureLive();
                        newEngine.ensureLive();
                        handle.owner.instances.remove(handle);
                        if (oldEngine == newEngine) {
                            handle.owner = this;
                            handle.dirty = true;
                            instances.add(handle);
                            return;
                        }
                        if (handle.nativeCreated) {
                            NativeInstancingProxy.deleteInstance(oldEngine.nativeEngine, handle.id);
                            handle.nativeCreated = false;
                        }
                        handle.engine = newEngine;
                        handle.id = newEngine.instanceIds.getAndIncrement();
                        handle.owner = this;
                        handle.dirty = true;
                        instances.add(handle);
                    }
                }
            }
        }

        private synchronized void flush() {
            model.upload(nativeEngine);
            Iterator<Handle<I>> iterator = instances.iterator();
            while (iterator.hasNext()) {
                Handle<I> handle = iterator.next();
                if (handle.deleted || context.deleted()) {
                    handle.deleted = true;
                    if (handle.nativeCreated) {
                        NativeInstancingProxy.deleteInstance(handle.engine.nativeEngine, handle.id);
                        handle.nativeCreated = false;
                    }
                    iterator.remove();
                } else if (handle.dirty) {
                    handle.write();
                }
            }
        }

        private synchronized void markAllDirty() {
            instances.forEach(handle -> handle.dirty = true);
        }

        private synchronized void deleteAll() {
            for (Handle<I> handle : instances) {
                if (handle.nativeCreated) {
                    NativeInstancingProxy.deleteInstance(handle.engine.nativeEngine, handle.id);
                    handle.nativeCreated = false;
                }
                handle.deleted = true;
            }
            instances.clear();
        }
    }

    private final class Handle<I extends Instance> implements InstanceHandle {
        private long id;
        private RadianceFlywheelEngine engine = RadianceFlywheelEngine.this;
        private RadianceInstancer<I> owner;
        private I instance;
        private volatile boolean dirty = true;
        private volatile boolean deleted;
        private volatile boolean visible = true;
        private boolean nativeCreated;

        private Handle(long id, RadianceInstancer<I> owner) {
            this.id = id;
            this.owner = owner;
        }

        @Override public void setChanged() {
            if (!deleted) dirty = true;
        }
        @Override public void setDeleted() { deleted = true; dirty = true; }
        @Override public void setVisible(boolean visible) {
            if (this.visible != visible) { this.visible = visible; dirty = true; }
        }
        @Override public boolean isVisible() { return visible && !deleted; }

        private void write() {
            int size = owner.type.layout().byteSize();
            ByteBuffer data = MemoryUtil.memCalloc(size);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                owner.type.writer().write(MemoryUtil.memAddress(data), instance);
                ByteBuffer embedding = stack.malloc(112);
                Matrix4f pose = new Matrix4f();
                Matrix3f normal = new Matrix3f();
                owner.context.writeComposed(pose, normal);
                pose.get(0, embedding);
                normal.get(64, embedding);
                NativeInstancingProxy.updateInstance(engine.nativeEngine, id, owner.model.nativeModel,
                    owner.adapter.nativeId(), owner.bias, visible, MemoryUtil.memAddress(data), size,
                    MemoryUtil.memAddress(embedding), MemoryUtil.memAddress(embedding) + 64L,
                    owner.context instanceof Embedding);
                FlywheelEmbeddingLighting.Snapshot lighting = owner.context.lighting();
                if (lighting != null) {
                    ByteBuffer sceneMatrix = stack.malloc(64);
                    lighting.writeMatrix(new Matrix4f()).get(0, sceneMatrix);
                    NativeInstancingProxy.updateInstanceLighting(engine.nativeEngine, id,
                        lighting.scene(), lighting.skyLightScale(), MemoryUtil.memAddress(sceneMatrix));
                }
                nativeCreated = true;
                dirty = false;
            } finally {
                MemoryUtil.memFree(data);
            }
        }
    }

    private static final class ModelState implements AutoCloseable {
        private final List<MeshState> meshes;
        private int nativeModel;
        private boolean uploaded;
        private boolean released;

        private ModelState(List<MeshState> meshes) {
            this.meshes = meshes;
        }

        private static ModelState capture(Model model) {
            List<MeshState> meshes = new ArrayList<>(model.meshes().size());
            try {
                for (Model.ConfiguredMesh configured : model.meshes()) {
                    meshes.add(new MeshState(FlywheelMeshData.capture(configured.mesh()),
                        FlywheelMaterialData.capture(configured.material())));
                }
                return new ModelState(meshes);
            } catch (Throwable failure) {
                meshes.forEach(MeshState::close);
                throw failure;
            }
        }

        private synchronized void upload(long engine) {
            if (uploaded) return;
            if (released) throw new IllegalStateException("Flywheel model was already released");
            nativeModel = NativeInstancingProxy.createModel(engine, meshes.size());
            if (nativeModel <= 0) throw new IllegalStateException("Native Flywheel model was not allocated");
            try {
                for (int i = 0; i < meshes.size(); i++) {
                    MeshState mesh = meshes.get(i);
                    FlywheelMaterialData material = mesh.material;
                    NativeInstancingProxy.uploadModelMesh(engine, nativeModel, i,
                        mesh.mesh.vertexAddress(), mesh.mesh.vertexCount(), mesh.mesh.indexAddress(),
                        mesh.mesh.indexCount(), material.textureId(), material.alphaMode(),
                        material.flags(), material.key());
                }
                NativeInstancingProxy.finishModel(engine, nativeModel);
                uploaded = true;
            } finally {
                if (uploaded) meshes.forEach(MeshState::close);
            }
        }

        /** Only once no instance uses the model; the native side retires it with the frame. */
        private synchronized void release(long engine) {
            if (released) return;
            released = true;
            meshes.forEach(MeshState::close);
            if (uploaded) NativeInstancingProxy.deleteModel(engine, nativeModel);
        }

        @Override public synchronized void close() {
            meshes.forEach(MeshState::close);
        }
    }

    private record MeshState(FlywheelMeshData mesh, FlywheelMaterialData material)
        implements AutoCloseable {
        @Override public void close() { mesh.close(); }
    }

    private static final class InstancerKey {
        private final Context context;
        private final InstanceType<?> type;
        private final Model model;
        private final int bias;
        private InstancerKey(Context context, InstanceType<?> type, Model model, int bias) {
            this.context = context; this.type = type; this.model = model; this.bias = bias;
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof InstancerKey other && context == other.context && type == other.type
                && model == other.model && bias == other.bias;
        }
        @Override public int hashCode() {
            int result = System.identityHashCode(context);
            result = 31 * result + System.identityHashCode(type);
            result = 31 * result + System.identityHashCode(model);
            return 31 * result + bias;
        }
    }
}
