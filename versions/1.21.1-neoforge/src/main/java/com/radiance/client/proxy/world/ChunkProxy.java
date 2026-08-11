package com.radiance.client.proxy.world;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.joml.Matrix4dc;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final SectionRenderDispatcher.CompiledSection PROCESSED =
            new SectionRenderDispatcher.CompiledSection() {
                @Override
                public boolean facesCanSeeEachother(Direction from, Direction to) {
                    return false;
                }
            };
    public static final SectionRenderDispatcher.CompiledSection TERRAIN_EMPTY =
            new SectionRenderDispatcher.CompiledSection() {
                @Override
                public boolean facesCanSeeEachother(Direction from, Direction to) {
                    return false;
                }
            };
    private static final Map<Integer, ChunkBuildState> chunkBuildStates = new ConcurrentHashMap<>();
    private static final SectionBuildQueue<BuildRequest> rebuildQueue =
            new SectionBuildQueue<>(
                    BuildRequest::index,
                    Comparator.naturalOrder(),
                    request -> request.state().primary ? 0 : request.state().renderSpace.handle(),
                    request -> request.distanceSquared().ring(),
                    request -> !request.interactive());
    private static final PriorityBlockingQueue<ColumnRequest> columnQueue =
            new PriorityBlockingQueue<>();
    private static final Set<Long> queuedColumns = ConcurrentHashMap.newKeySet();
    private static final Map<SectionRenderDispatcher.RenderSection, ExternalChunk> externalChunks =
            new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final AtomicLong storageEpoch = new AtomicLong();
    private static final AtomicLong regionSnapshotRevision = new AtomicLong();
    private static final AtomicLong queueSequence = new AtomicLong();
    private static long buildAdmissionSequence;
    private static final AtomicInteger importantBuildsInFlight = new AtomicInteger();
    private static final AtomicInteger normalBuildsInFlight = new AtomicInteger();
    private static volatile ViewArea currentStorage = null;
    private static final SectionPresenceIndex<
                    SectionRenderDispatcher.RenderSection, SectionRenderDispatcher.CompiledSection>
            blockEntitySections = new SectionPresenceIndex<>();
    private static volatile boolean acceptingSectionEvents = false;
    private static volatile boolean initialSeedPending = false;
    private static int storageSizeX;
    private static int storageSizeY;
    private static int storageSizeZ;
    private static int storageBottomSection;
    private static volatile int cameraSectionX;
    private static volatile int cameraSectionZ;
    private static volatile int cameraSectionY;
    private static final long REBUILD_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
    private static final int MIN_REBUILD_ATTEMPTS_PER_FRAME = 8;
    private static final int MAX_REBUILD_ATTEMPTS_PER_FRAME = 256;
    private static final int MIN_COLUMN_SCANS_PER_FRAME = 16;
    private static final int MAX_COLUMN_SCANS_PER_FRAME = 128;
    private static int numChunkRebuildThreads = getChunkRebuildThreadCount();
    private static final int numImportantChunkRebuildThreads = 1;
    private static int numNormalChunkRebuildThreads =
            Math.max(1, numChunkRebuildThreads - numImportantChunkRebuildThreads);
    private static final ExecutorService importantChunkRebuildExecutor =
            Executors.newFixedThreadPool(
                    numImportantChunkRebuildThreads,
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setPriority(Thread.NORM_PRIORITY);
                        return thread;
                    });
    private static final ThreadLocal<SectionBufferBuilderPack>
            blockBufferAllocatorStorageThreadLocal =
                    ThreadLocal.withInitial(SectionBufferBuilderPack::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor =
            Executors.newFixedThreadPool(
                    numNormalChunkRebuildThreads,
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setPriority(Thread.NORM_PRIORITY);
                        return thread;
                    });

    public static native void initNative(
            int numChunks, int sizeX, int sizeY, int sizeZ, int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    /**
     * Default on; false keeps the native scene until the next world or shutdown (A/B and fallback).
     */
    private static final boolean RELEASE_SCENE_ON_LEAVE =
            Boolean.parseBoolean(System.getProperty("radiance.releaseSceneOnLeave", "true"));

    private static native void releaseSceneNative();

    /**
     * Called after the level renderer has left its world; waits for the GPU and frees the scene.
     */
    public static void releaseScene() {
        if (!RELEASE_SCENE_ON_LEAVE) return;
        releaseSceneNative();
        RenderAuditBridge.counter("CHUNK_BUILD", "SCENE_RELEASED", 1, "");
    }

    public static void init(
            int numChunks, int sizeX, int sizeY, int sizeZ, int bottomSectionCoord) {
        clear();
        storageSizeX = sizeX;
        storageSizeY = sizeY;
        storageSizeZ = sizeZ;
        storageBottomSection = bottomSectionCoord;
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
        RenderAuditBridge.counter(
                "CHUNK_BUILD",
                "RESET",
                1,
                "slots=" + numChunks + "; grid=" + sizeX + 'x' + sizeY + 'x' + sizeZ);
    }

    public static void updateSectionPos(SectionPos sectionPos) {
        updateSectionPosNative(sectionPos.x(), sectionPos.y(), sectionPos.z());
    }

    public static void setStorage(ViewArea storage) {
        currentStorage = storage;
    }

    public static void storageCreated(ViewArea storage) {
        currentStorage = storage;
        blockEntitySections.reset(java.util.Arrays.asList(storage.sections));
        acceptingSectionEvents = true;
        initialSeedPending = true;
    }

    public static void removeBlockEntitySection(SectionRenderDispatcher.RenderSection section) {
        blockEntitySections.remove(section);
    }

    static boolean currentBlockEntitySection(
            SectionPresenceIndex.Entry<
                            SectionRenderDispatcher.RenderSection,
                            SectionRenderDispatcher.CompiledSection>
                    entry) {
        return blockEntitySections.isCurrent(entry);
    }

    static List<
                    SectionPresenceIndex.Entry<
                            SectionRenderDispatcher.RenderSection,
                            SectionRenderDispatcher.CompiledSection>>
            blockEntitySections(ViewArea storage) {
        if (storage != currentStorage)
            throw new IllegalStateException("Block-entity index belongs to another ViewArea");
        return blockEntitySections.snapshot();
    }

    private static void publishCompiled(
            SectionRenderDispatcher.RenderSection section,
            SectionRenderDispatcher.CompiledSection compiled) {
        synchronized (blockEntitySections) {
            section.compiled.set(compiled);
            blockEntitySections.publish(
                    section, compiled, !compiled.getRenderableBlockEntities().isEmpty());
        }
    }

    public static void storageRepositioned(ViewArea storage, double cameraX, double cameraZ) {
        currentStorage = storage;
        if (!initialSeedPending || storage == null || storage.sections == null) {
            return;
        }
        initialSeedPending = false;

        Set<Long> loadedColumns = ConcurrentHashMap.newKeySet();
        for (SectionRenderDispatcher.RenderSection section : storage.sections) {
            if (section == null) {
                continue;
            }
            BlockPos origin = section.getOrigin();
            long column =
                    ChunkPos.asLong(
                            SectionPos.blockToSectionCoord(origin.getX()),
                            SectionPos.blockToSectionCoord(origin.getZ()));
            if (!loadedColumns.add(column)) {
                continue;
            }
        }
        cameraSectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraX));
        cameraSectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraZ));
        loadedColumns.stream()
                .sorted(
                        Comparator.comparingLong(
                                column -> {
                                    long dx = ChunkPos.getX(column) - cameraSectionX;
                                    long dz = ChunkPos.getZ(column) - cameraSectionZ;
                                    return dx * dx + dz * dz;
                                }))
                .forEach(column -> requestColumn(ChunkPos.getX(column), ChunkPos.getZ(column)));
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal =
                RenderType.chunkBufferLayers().stream().mapToInt(RenderType::bufferSize).sum();
        int memoryLimited =
                Math.max(
                        1,
                        (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4)
                                - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(
                2,
                Math.min(
                        userThreads,
                        Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final SectionBufferBuilderPack s = blockBufferAllocatorStorageThreadLocal.get();
        s.discardAll();
        return s::clearAll;
    }

    /**
     * Vanilla's visible-section list is intentionally bypassed by Radiance, so its normal F3
     * counter is unavailable. Count the actually compiled, non-empty section meshes instead of the
     * historical rebuild-attempt counter (which could report zero or grow on repeat builds).
     */
    public static int countCompiledSections(ViewArea storage) {
        if (storage == null || storage.sections == null) {
            return 0;
        }

        int count = 0;
        for (SectionRenderDispatcher.RenderSection section : storage.sections) {
            if (section == null) {
                continue;
            }
            SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
            if (compiled != SectionRenderDispatcher.CompiledSection.UNCOMPILED
                    && !compiled.hasNoRenderableLayers()) {
                count++;
            }
        }
        return count;
    }

    public static void clear() {
        PlayerSectionUpdates.clear();
        acceptingSectionEvents = false;
        initialSeedPending = false;
        buildAdmissionSequence = 0;
        storageEpoch.incrementAndGet();
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        numChunkRebuildThreads = getChunkRebuildThreadCount();
        numNormalChunkRebuildThreads =
                Math.max(1, numChunkRebuildThreads - numImportantChunkRebuildThreads);
        backgroundChunkRebuildExecutor =
                Executors.newFixedThreadPool(
                        numNormalChunkRebuildThreads,
                        r -> {
                            Thread thread = new Thread(r);
                            thread.setPriority(Thread.NORM_PRIORITY);
                            return thread;
                        });

        rebuildQueue.clear();
        columnQueue.clear();
        queuedColumns.clear();
        chunkBuildStates.clear();
        for (ExternalChunk external : externalChunks.values()) {
            external.buildState.generation.incrementAndGet();
        }
        externalChunks.clear();
        blockEntitySections.reset(List.of());
        com.radiance.client.render.SectionRasterStorage.clear();
        rebuildTasks.clear();
        queueSequence.set(0L);
        currentStorage = null;
        importantBuildsInFlight.set(0);
        normalBuildsInFlight.set(0);
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk) {
        enqueueRebuild(chunk, chunk.isDirtyFromPlayer());
    }

    public static void sectionChanged(SectionRenderDispatcher.RenderSection section) {
        if (!acceptingSectionEvents) return;
        // Even an already-dirty section may change a neighbor snapshot reused later
        // in this preparation round. Coalescing requests must not hide invalidation.
        regionSnapshotRevision.incrementAndGet();
        var state = chunkBuildStates.get(section.index);
        if (state != null && state.section == section) {
            state.changedAt = System.nanoTime();
            if (state.waitingForSource) {
                state.waitingForSource = false;
                var origin = section.getOrigin();
                requestColumn(origin.getX() >> 4, origin.getZ() >> 4);
            }
        }
    }

    public static void promoteRebuild(SectionRenderDispatcher.RenderSection chunk) {
        if (!acceptingSectionEvents) return;
        ChunkBuildState state =
                chunkBuildStates.computeIfAbsent(
                        chunk.index, ignored -> new ChunkBuildState(chunk));
        synchronized (state) {
            state.interactive = true;
            state.section = chunk;
            rebuildQueue.removeSection(chunk.index);
            state.queued.set(false);
            queueState(state);
        }
    }

    public static void enqueueRebuild(
            SectionRenderDispatcher.RenderSection chunk, boolean interactive) {
        enqueueRebuild(chunk, interactive, interactive);
    }

    private static void enqueueRebuild(
            SectionRenderDispatcher.RenderSection chunk, boolean interactive, boolean direct) {
        if (!acceptingSectionEvents) {
            return;
        }
        ChunkBuildState state =
                chunkBuildStates.computeIfAbsent(
                        chunk.index, ignored -> new ChunkBuildState(chunk));
        synchronized (state) {
            state.section = chunk;
            state.interactive |= interactive;
            state.occupancyEmpty = false;
            state.skipNeighborRefresh = false;
            state.changedAt = System.nanoTime();
            long generation = state.generation.incrementAndGet();
            markChunkDirtyNative(chunk.index, generation);
            if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                RenderAuditBridge.counter(
                        "CHUNK_UPDATE",
                        "DIRTY",
                        generation,
                        "action="
                                + PlayerSectionUpdates.request(chunk.getOrigin())
                                + "; id="
                                + chunk.index
                                + "; java_ns="
                                + System.nanoTime()
                                + "; origin="
                                + chunk.getOrigin()
                                + "; epoch="
                                + storageEpoch.get());
        }
        if (direct) {
            // Preserve all dirty dependency notifications, but do not delay this section behind a
            // column scan.
            synchronized (state) {
                rebuildQueue.removeSection(chunk.index);
                state.queued.set(false);
                queueState(state);
            }
            return;
        }
        // A dirty notification says that the section must be reconsidered, not that it is ready
        // to compile. Route it through the column occupancy pass first so unloaded/empty slots do
        // not flood RenderRegionCache and the native build queues.
        BlockPos origin = chunk.getOrigin();
        requestColumn(
                SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()));
    }

    public static void relocateSection(
            SectionRenderDispatcher.RenderSection section, int originX, int originY, int originZ) {
        if (!acceptingSectionEvents) {
            relocateSingle(section.index, originX, originY, originZ, 0L);
            return;
        }
        ChunkBuildState state =
                chunkBuildStates.computeIfAbsent(
                        section.index, ignored -> new ChunkBuildState(section));
        synchronized (state) {
            state.section = section;
            // ViewArea reuses native slots as the camera moves. Occupancy belongs to the old
            // world coordinate and must never decide whether the relocated section is empty.
            state.occupancyEmpty = false;
            state.skipNeighborRefresh = false;
            state.contentChangeGeneration = -1;
            state.interactive = false;
            rebuildQueue.removeSection(state.nativeIndex);
            state.queued.set(false);
            long generation = state.generation.incrementAndGet();
            relocateSingle(section.index, originX, originY, originZ, generation);
        }
        if (section.isDirty()
                || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
            requestColumn(
                    SectionPos.blockToSectionCoord(originX),
                    SectionPos.blockToSectionCoord(originZ));
        }
    }

    private static void queueState(ChunkBuildState state) {
        state.waitingForSource = false;
        if (state.running.get()) {
            RenderAuditBridge.counter("CHUNK_BUILD", "COALESCED_RUNNING", 1, "");
            return;
        }
        if (state.queued.compareAndSet(false, true)) {
            if (state.changedAt == 0) state.changedAt = System.nanoTime();
            BlockPos origin = state.section.getOrigin();
            int sectionX = SectionPos.blockToSectionCoord(origin.getX());
            int sectionZ = SectionPos.blockToSectionCoord(origin.getZ());
            rebuildQueue.offer(
                    new BuildRequest(
                            state,
                            sectionPriority(state),
                            queueSequence.getAndIncrement(),
                            state.interactive || state.section.isDirtyFromPlayer(),
                            System.nanoTime()));
            RenderAuditBridge.counter("CHUNK_BUILD", "ENQUEUED", 1, "");
        } else {
            RenderAuditBridge.counter("CHUNK_BUILD", "COALESCED_QUEUED", 1, "");
        }
    }

    public static void enqueueExternalRebuild(SectionRenderDispatcher.RenderSection section) {
        ExternalChunk external = externalChunks.get(section);
        if (external != null && external.nativeId >= 0) {
            ChunkBuildState state = external.buildState;
            synchronized (state) {
                updateSchedulingBounds(state);
                state.changedAt = System.nanoTime();
                state.interactive |= section.isDirtyFromPlayer();
                regionSnapshotRevision.incrementAndGet();
                long version = state.generation.incrementAndGet();
                markChunkDirtyNative(external.nativeId, version);
                rebuildQueue.removeSection(state.nativeIndex);
                state.queued.set(false);
                queueState(state);
            }
        }
    }

    public static void refreshExternalPriority(SectionRenderDispatcher.RenderSection section) {
        ExternalChunk external = externalChunks.get(section);
        if (external == null) return;
        rebuildQueue.update(
                external.nativeId,
                request -> {
                    updateSchedulingBounds(external.buildState);
                    return new BuildRequest(
                            request.state(),
                            sectionPriority(request.state()),
                            request.sequence(),
                            request.interactive(),
                            request.enqueuedAt());
                });
    }

    public static void bindExternalSection(
            SectionRenderDispatcher.RenderSection section,
            com.radiance.api.render.RenderSpace space,
            Matrix4dc local) {
        long spaceId = space.handle();
        ExternalChunk external =
                externalChunks.computeIfAbsent(
                        section,
                        value -> {
                            long id = allocateExternalChunkNative();
                            return new ExternalChunk(id, new ChunkBuildState(value, id, false));
                        });
        var state = external.buildState;
        state.renderSpace = space;
        state.localTransform = new org.joml.Matrix4d(local);
        updateSchedulingBounds(state);
        bindExternalSectionNative(external.nativeId, spaceId, local.get(new double[16]));
        synchronized (state) {
            state.changedAt = System.nanoTime();
            long revision = state.generation.incrementAndGet();
            markChunkDirtyNative(external.nativeId, revision);
            queueState(state);
        }
    }

    public static long sectionEpoch() {
        return storageEpoch.get();
    }

    public static native long beginHandoffNative();

    private static native void holdHandoffNative(long handoff, long section);

    private static native boolean holdChangedHandoffNative(
            long handoff, long section, long changeVersion);

    public static native void sealHandoffNative(long handoff);

    public static native void joinHandoffNative(long handoff, long other);

    public static native boolean commitHandoffNative(long handoff);

    public static native String handoffStatusNative(long handoff);

    public static native void closeHandoffNative(long handoff);

    public static void holdSection(long handoff, SectionRenderDispatcher.RenderSection section) {
        var external = externalChunks.get(section);
        if (external != null) holdHandoffNative(handoff, external.nativeId);
        else if (currentStorage != null
                && section.index >= 0
                && section.index < currentStorage.sections.length
                && currentStorage.sections[section.index] == section)
            holdHandoffNative(handoff, section.index);
    }

    public static void holdWorldSections(long handoff, BlockPos minimum, BlockPos maximum) {
        ViewArea storage = currentStorage;
        if (storage == null) return;
        for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++)
            for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++)
                for (int y = minimum.getY() >> 4; y <= maximum.getY() >> 4; y++) {
                    var section = storage.getRenderSectionAt(new BlockPos(x << 4, y << 4, z << 4));
                    if (section != null
                            && section.getOrigin().equals(new BlockPos(x << 4, y << 4, z << 4))) {
                        holdSection(handoff, section);
                        // A handoff must acknowledge the current source contents, rather than an
                        // unchanged version published before the source's packet bundle arrived.
                        // Forced versions must enter preparation even if Minecraft still marks
                        // the old compiled section clean. They remain ordinary background work.
                        enqueueRebuild(section, false, true);
                    }
                }
    }

    private static native void bindExternalSectionNative(long section, long space, double[] local);

    /** Initial discovery retains only an existing publication with a real pending replacement. */
    public static boolean holdChangedWorldSections(
            long handoff, BlockPos minimum, BlockPos maximum) {
        ViewArea storage = currentStorage;
        if (storage == null) return false;
        boolean held = false;
        for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++)
            for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++)
                for (int y = minimum.getY() >> 4; y <= maximum.getY() >> 4; y++) {
                    var position = new BlockPos(x << 4, y << 4, z << 4);
                    var section = storage.getRenderSectionAt(position);
                    if (section != null && section.getOrigin().equals(position)) {
                        var state = chunkBuildStates.get(section.index);
                        if (state != null && state.contentChangeGeneration >= 0)
                            held |=
                                    holdChangedHandoffNative(
                                            handoff, section.index, state.contentChangeGeneration);
                    }
                }
        return held;
    }

    /** A neighbor arriving is not an outgoing transfer. Record actual old-content changes only. */
    public static void blockContentChanged(BlockPos position) {
        ViewArea storage = currentStorage;
        if (!acceptingSectionEvents || storage == null) return;
        var section = storage.getRenderSectionAt(position);
        if (section == null || !SectionPos.of(position).origin().equals(section.getOrigin()))
            return;
        var state = chunkBuildStates.get(section.index);
        if (state != null && state.section == section)
            state.contentChangeGeneration = state.generation.get();
    }

    public static void releaseExternalSection(SectionRenderDispatcher.RenderSection section) {
        ExternalChunk external = externalChunks.remove(section);
        if (external == null) return;
        synchronized (external.buildState) {
            external.buildState.generation.incrementAndGet();
            rebuildQueue.removeSection(external.nativeId);
            external.buildState.queued.set(false);
            com.radiance.client.render.SectionRasterStorage.discard(section);
            releaseExternalChunkNative(external.nativeId);
        }
    }

    public static void rebuildAll() {
        ViewArea storage = currentStorage;
        if (storage == null || storage.sections == null) {
            return;
        }
        for (SectionRenderDispatcher.RenderSection builtChunk : storage.sections) {
            if (builtChunk == null
                    || builtChunk.getCompiled()
                            == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            builtChunk.setDirty(false);
        }
    }

    public static void onChunkLoaded(ChunkPos loadedChunk) {
        if (!acceptingSectionEvents || currentStorage == null) {
            return;
        }
        regionSnapshotRevision.incrementAndGet();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int candidateX = loadedChunk.x + dx;
                int candidateZ = loadedChunk.z + dz;
                invalidateCompiledColumn(candidateX, candidateZ);
                requestColumn(candidateX, candidateZ);
            }
        }
    }

    /**
     * Sections are allowed to compile while one or more neighboring columns are absent. The vanilla
     * region cache represents those neighbors as empty chunks, which keeps terrain at the edge of
     * the loaded range available to ray tracing. Once a real neighbor arrives, rebuild the
     * surrounding compiled sections so boundary faces, AO and mod-provided section geometry are
     * refreshed from real data.
     */
    private static void invalidateCompiledColumn(int sectionX, int sectionZ) {
        ViewArea storage = currentStorage;
        if (storage == null
                || storage.sections == null
                || storageSizeX <= 0
                || storageSizeY <= 0
                || storageSizeZ <= 0) {
            return;
        }
        int xIndex = Math.floorMod(sectionX, storageSizeX);
        int zIndex = Math.floorMod(sectionZ, storageSizeZ);
        int invalidated = 0;
        int skippedKnownEmpty = 0;
        for (int yIndex = 0; yIndex < storageSizeY; yIndex++) {
            int index = (zIndex * storageSizeY + yIndex) * storageSizeX + xIndex;
            if (index < 0 || index >= storage.sections.length) {
                continue;
            }
            SectionRenderDispatcher.RenderSection section = storage.sections[index];
            if (section == null) {
                continue;
            }
            BlockPos origin = section.getOrigin();
            if (SectionPos.blockToSectionCoord(origin.getX()) != sectionX
                    || SectionPos.blockToSectionCoord(origin.getZ()) != sectionZ) {
                continue;
            }
            ChunkBuildState state = chunkBuildStates.get(section.index);
            if (state != null && state.section == section) state.changedAt = System.nanoTime();
            if (section.isDirty()
                    || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED)
                continue;
            if (state != null && state.section == section && state.skipNeighborRefresh) {
                skippedKnownEmpty++;
                continue;
            }
            section.setDirty(false);
            invalidated++;
        }
        if (invalidated != 0) {
            RenderAuditBridge.counter("CHUNK_BUILD", "NEIGHBOR_REFRESH", invalidated, "");
        }
        if (skippedKnownEmpty != 0) {
            RenderAuditBridge.counter(
                    "CHUNK_BUILD", "NEIGHBOR_EMPTY_SKIPPED", skippedKnownEmpty, "");
        }
    }

    private static void requestColumn(int sectionX, int sectionZ) {
        long column = ChunkPos.asLong(sectionX, sectionZ);
        if (queuedColumns.add(column)) {
            columnQueue.offer(
                    new ColumnRequest(
                            column,
                            horizontalDistanceSquared(sectionX, sectionZ),
                            queueSequence.getAndIncrement()));
            RenderAuditBridge.counter("CHUNK_BUILD", "COLUMN_ENQUEUED", 1, "");
        }
    }

    private static void scanColumn(int sectionX, int sectionZ) {
        ViewArea storage = currentStorage;
        if (storage == null
                || storage.sections == null
                || storageSizeX <= 0
                || storageSizeY <= 0
                || storageSizeZ <= 0) {
            return;
        }
        IChunkBuilderExt dispatcherExt = null;
        ChunkAccess levelChunk = null;
        int xIndex = Math.floorMod(sectionX, storageSizeX);
        int zIndex = Math.floorMod(sectionZ, storageSizeZ);
        int occupied = 0;
        int empty = 0;
        long scanStart = RenderAuditBridge.accepts("CHUNK_PERF") ? System.nanoTime() : 0L;
        for (int yIndex = 0; yIndex < storageSizeY; yIndex++) {
            int index = (zIndex * storageSizeY + yIndex) * storageSizeX + xIndex;
            if (index < 0 || index >= storage.sections.length) {
                continue;
            }
            SectionRenderDispatcher.RenderSection section = storage.sections[index];
            if (section == null) {
                continue;
            }
            BlockPos origin = section.getOrigin();
            if (SectionPos.blockToSectionCoord(origin.getX()) != sectionX
                    || SectionPos.blockToSectionCoord(origin.getZ()) != sectionZ) {
                continue;
            }
            if (!section.isDirty()
                    && section.getCompiled()
                            != SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            ChunkBuildState state =
                    chunkBuildStates.computeIfAbsent(
                            section.index, ignored -> new ChunkBuildState(section));
            synchronized (state) {
                state.section = section;
            }
            if (dispatcherExt == null) {
                dispatcherExt =
                        (IChunkBuilderExt)
                                ((IChunkBuilderBuiltChunkExt) section).radiance$getChunkBuilder();
                levelChunk =
                        dispatcherExt
                                .radiance$getWorld()
                                .getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
                if (levelChunk == null) {
                    return;
                }
            }
            int sectionY = SectionPos.blockToSectionCoord(origin.getY());
            int levelIndex =
                    dispatcherExt.radiance$getWorld().getSectionIndexFromSectionY(sectionY);
            if (levelChunk != null
                    && levelIndex >= 0
                    && levelIndex < levelChunk.getSections().length
                    && levelChunk.getSection(levelIndex).hasOnlyAir()) {
                state.occupancyEmpty = true;
                empty++;
                List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers =
                        ClientHooks.gatherAdditionalRenderers(
                                origin, dispatcherExt.radiance$getWorld());
                if (additionalRenderers.isEmpty()) {
                    state.skipNeighborRefresh = true;
                    publishEmpty(state, section);
                    RenderAuditBridge.counter("CHUNK_BUILD", "EMPTY_FAST_PATH", 1, "");
                } else {
                    state.skipNeighborRefresh = false;
                    queueState(state);
                }
                continue;
            }
            state.occupancyEmpty = false;
            state.skipNeighborRefresh = false;
            occupied++;
            queueState(state);
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "OCCUPANCY_NON_EMPTY", occupied, "");
        RenderAuditBridge.counter("CHUNK_BUILD", "OCCUPANCY_EMPTY", empty, "");
        if (scanStart != 0L) {
            RenderAuditBridge.counter(
                    "CHUNK_PERF", "COLUMN_SCAN_NS", System.nanoTime() - scanStart, "");
            RenderAuditBridge.counter("CHUNK_PERF", "COLUMN_SCAN_COUNT", 1, "");
        }
    }

    private static void publishEmpty(
            ChunkBuildState state, SectionRenderDispatcher.RenderSection section) {
        BlockPos origin = section.getOrigin().immutable();
        synchronized (state) {
            long generation = state.generation.get();
            ChunkBuildTicket ticket =
                    new ChunkBuildTicket(
                            state,
                            section,
                            origin,
                            generation,
                            storageEpoch.get(),
                            state.nativeIndex,
                            state.primary,
                            Map.of(),
                            Map.of());
            if (!ticket.isCurrent()) {
                RenderAuditBridge.counter("CHUNK_BUILD", "STALE_DISCARDED", 1, "empty");
                queueState(state);
                return;
            }
            section.setNotDirty();
            state.preparedGeneration = generation;
            publishCompiled(section, SectionRenderDispatcher.CompiledSection.EMPTY);
            com.radiance.client.render.SectionRasterStorage.publish(
                    section, Map.of(), ticket::ownsSection);
            if (state.primary)
                com.radiance.api.render.SourceHooks.nearSectionEmpty(
                        section.index, origin.getX(), origin.getY(), origin.getZ());
            boolean removed = state.hasNativeGeometry.getAndSet(false);
            // Empty is a completed version even when this slot never held geometry. A joined
            // publication may be waiting for precisely this acknowledgement before it can open.
            invalidateSingle(state.nativeIndex, generation);
            state.retry.succeeded();
            if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                RenderAuditBridge.counter(
                        "CHUNK_UPDATE",
                        "EMPTY_CONFIRMED",
                        generation,
                        "action="
                                + PlayerSectionUpdates.request(origin)
                                + "; id="
                                + section.index
                                + "; java_ns="
                                + System.nanoTime()
                                + "; origin="
                                + origin
                                + "; removed="
                                + removed);
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "EMPTY", 1, "");
        RenderAuditBridge.counter("CHUNK_BUILD", "PUBLISHED", 1, "");
    }

    /** Refresh after source scopes have registered holds, before checking their completion. */
    public static void completePendingBuilds() {
        completeBuildsNative();
    }

    public static void rebuild(Camera camera) {
        rebuildTasks.removeIf(Future::isDone);
        BlockPos blockPos = camera.getBlockPosition();
        int nextCameraSectionY = SectionPos.blockToSectionCoord(blockPos.getY());
        int nextCameraSectionX = SectionPos.blockToSectionCoord(blockPos.getX());
        int nextCameraSectionZ = SectionPos.blockToSectionCoord(blockPos.getZ());
        if (nextCameraSectionX != cameraSectionX
                || nextCameraSectionZ != cameraSectionZ
                || nextCameraSectionY != cameraSectionY) {
            cameraSectionX = nextCameraSectionX;
            cameraSectionZ = nextCameraSectionZ;
            cameraSectionY = nextCameraSectionY;
            reprioritizeQueues();
        }
        long frameStart = System.nanoTime();
        int columns = 0;
        while (columns < MAX_COLUMN_SCANS_PER_FRAME
                && (columns < MIN_COLUMN_SCANS_PER_FRAME
                        || System.nanoTime() - frameStart < REBUILD_BUDGET_NANOS)) {
            ColumnRequest request = columnQueue.poll();
            if (request == null) {
                break;
            }
            long column = request.column();
            queuedColumns.remove(column);
            scanColumn(ChunkPos.getX(column), ChunkPos.getZ(column));
            columns++;
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "COLUMN_SCANNED", columns, "");
        int attempts = 0;
        var preparationCaches =
                new java.util.IdentityHashMap<
                        net.minecraft.client.multiplayer.ClientLevel,
                        SectionPreparationCache<RenderRegionCache>>();
        // Producers enter native before releasing their Java permits. Sampling in
        // this order can double-count a transfer, but cannot lose it between stages.
        int outstandingAtRoundStart = importantBuildsInFlight.get() + normalBuildsInFlight.get();
        long nativeBacklog = advanceBuildsNative();
        int preparedThisRound = 0;
        boolean fairnessAttempted = false;
        try (var round =
                new ChunkBuildDispatch.Round<>(rebuildQueue, MAX_REBUILD_ATTEMPTS_PER_FRAME)) {
            while (attempts < MAX_REBUILD_ATTEMPTS_PER_FRAME
                    && (attempts < MIN_REBUILD_ATTEMPTS_PER_FRAME
                            || System.nanoTime() - frameStart < REBUILD_BUDGET_NANOS)) {
                attempts++;
                boolean fairness = (buildAdmissionSequence & 3) == 3 && !fairnessAttempted;
                fairnessAttempted |= fairness;
                BuildRequest request = fairness ? round.pollBackground() : round.poll();
                if (request == null) {
                    break;
                }
                ChunkBuildState state = request.state();
                if (!currentOwner(state)) {
                    continue;
                }
                state.queued.set(false);
                if (state.running.get()) {
                    continue;
                }
                SectionRenderDispatcher.RenderSection builtChunk = state.section;
                RenderAuditBridge.counter("CHUNK_BUILD", "DEQUEUED", 1, "");
                if (!builtChunk.isDirty() && state.preparedGeneration == state.generation.get()) {
                    continue;
                }
                BlockPos origin = builtChunk.getOrigin().immutable();
                IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
                SectionRenderDispatcher chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
                IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
                boolean isImportant = state.interactive || builtChunk.isDirtyFromPlayer();
                if (!SectionUpdatePolicy.settled(
                                request.enqueuedAt(),
                                state.changedAt,
                                isImportant,
                                System.nanoTime())
                        || !SectionUpdatePolicy.canPrepare(
                                outstandingAtRoundStart + preparedThisRound,
                                nativeBacklog,
                                isImportant)) {
                    if (state.queued.compareAndSet(false, true)) round.defer(request);
                    RenderAuditBridge.counter("CHUNK_BUILD", "PIPELINE_DEFERRED", 1, "");
                    continue;
                }
                AtomicInteger inFlight =
                        isImportant ? importantBuildsInFlight : normalBuildsInFlight;
                int limit =
                        isImportant
                                ? numImportantChunkRebuildThreads
                                : Math.max(1, numNormalChunkRebuildThreads * 2);
                long attemptGeneration = state.generation.get();
                if (!state.retry.allows(attemptGeneration, System.nanoTime())) {
                    if (state.queued.compareAndSet(false, true)) round.defer(request);
                    continue;
                }
                try {
                    var result =
                            ChunkBuildDispatch.submit(
                                    state.running,
                                    inFlight,
                                    limit,
                                    () -> {
                                        long generation = state.generation.get();
                                        long epoch = storageEpoch.get();
                                        var world = chunkBuilderExt.radiance$getWorld();
                                        var source =
                                                world.getChunk(
                                                        SectionPos.blockToSectionCoord(
                                                                origin.getX()),
                                                        SectionPos.blockToSectionCoord(
                                                                origin.getZ()),
                                                        ChunkStatus.FULL,
                                                        false);
                                        if (source == null) {
                                            state.waitingForSource = true;
                                            return ChunkBuildDispatch.awaitSource();
                                        }
                                        List<AddSectionGeometryEvent.AdditionalSectionRenderer>
                                                additionalRenderers =
                                                        ClientHooks.gatherAdditionalRenderers(
                                                                origin, world);
                                        int levelIndex = world.getSectionIndex(origin.getY());
                                        if (levelIndex >= 0
                                                && levelIndex < source.getSections().length
                                                && source.getSection(levelIndex).hasOnlyAir()
                                                && additionalRenderers.isEmpty()) {
                                            publishEmpty(state, builtChunk);
                                            return () -> {};
                                        }
                                        long regionStart =
                                                RenderAuditBridge.accepts("CHUNK_PERF")
                                                        ? System.nanoTime()
                                                        : 0L;
                                        RenderChunkRegion region =
                                                preparationCaches
                                                        .computeIfAbsent(
                                                                world,
                                                                ignored ->
                                                                        new SectionPreparationCache<>(
                                                                                RenderRegionCache
                                                                                        ::new))
                                                        .get(regionSnapshotRevision.get())
                                                        .createRegion(
                                                                chunkBuilderExt.radiance$getWorld(),
                                                                SectionPos.of(origin),
                                                                false);
                                        if (regionStart != 0L) {
                                            RenderAuditBridge.counter(
                                                    "CHUNK_PERF",
                                                    "REGION_CREATE_NS",
                                                    System.nanoTime() - regionStart,
                                                    "");
                                            RenderAuditBridge.counter(
                                                    "CHUNK_PERF", "REGION_CREATE_COUNT", 1, "");
                                        }
                                        if (region == null) {
                                            RenderAuditBridge.counter(
                                                    "CHUNK_BUILD", "REGION_UNAVAILABLE", 1, "");
                                            state.waitingForSource = true;
                                            return ChunkBuildDispatch.awaitSource();
                                        }
                                        ChunkBuildTicket ticket =
                                                new ChunkBuildTicket(
                                                        state,
                                                        builtChunk,
                                                        origin,
                                                        generation,
                                                        epoch,
                                                        state.nativeIndex,
                                                        state.primary,
                                                        snapshotChunkTextureIds(),
                                                        snapshotChunkFaces());
                                        synchronized (state) {
                                            if (!ticket.isCurrent()) return null;
                                            builtChunk.setNotDirty();
                                            state.interactive = false;
                                        }
                                        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                                            RenderAuditBridge.counter(
                                                    "CHUNK_UPDATE",
                                                    "ADMITTED",
                                                    generation,
                                                    "id="
                                                            + builtChunk.index
                                                            + "; epoch="
                                                            + ticket.epoch()
                                                            + "; java_ns="
                                                            + System.nanoTime()
                                                            + "; priority="
                                                            + (isImportant ? 1 : 0)
                                                            + "; queue_ns="
                                                            + (System.nanoTime()
                                                                    - request.enqueuedAt()));
                                        RenderAuditBridge.counter(
                                                "CHUNK_BUILD",
                                                isImportant
                                                        ? "SCHEDULED_PRIORITY"
                                                        : "SCHEDULED_NORMAL",
                                                1,
                                                "id="
                                                        + builtChunk.index
                                                        + "; generation="
                                                        + generation
                                                        + "; queue_ns="
                                                        + (System.nanoTime()
                                                                - request.enqueuedAt()));
                                        return ChunkBuildDispatch.currentWork(
                                                ticket::isCurrent,
                                                () ->
                                                        runBuild(
                                                                ticket,
                                                                region,
                                                                chunkBuilder,
                                                                chunkBuilderExt,
                                                                additionalRenderers,
                                                                isImportant));
                                    },
                                    isImportant
                                            ? task ->
                                                    rebuildTasks.add(
                                                            importantChunkRebuildExecutor.submit(
                                                                    task))
                                            : backgroundChunkRebuildExecutor,
                                    () -> {
                                        if (builtChunk.isDirty()
                                                || state.preparedGeneration
                                                        != state.generation.get())
                                            queueState(state);
                                    });
                    if (result == ChunkBuildDispatch.Result.SUBMITTED) {
                        preparedThisRound++;
                        buildAdmissionSequence++;
                        fairnessAttempted = false;
                    } else if (result == ChunkBuildDispatch.Result.WAITING_FOR_SOURCE) {
                        // Keep dirty/old geometry. onChunkLoaded scans this column
                        // and its dependencies; another dirty event also wakes it.
                        RenderAuditBridge.counter("CHUNK_BUILD", "SOURCE_WAIT", 1, "");
                    } else {
                        if (state.queued.compareAndSet(false, true)) round.defer(request);
                        RenderAuditBridge.counter(
                                "CHUNK_BUILD", "CAPACITY_DEFERRED", 1, result.name());
                    }
                } catch (RuntimeException failure) {
                    buildFailed(state, attemptGeneration, isImportant, "prepare/submit", failure);
                    if (state.queued.compareAndSet(false, true)) round.defer(request);
                    RenderAuditBridge.counter(
                            "CHUNK_BUILD",
                            "PREPARE_OR_SUBMIT_FAILED",
                            1,
                            failure.getClass().getSimpleName());
                }
            }
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "FRAME_ATTEMPTS", attempts, "");
        if (RenderAuditBridge.accepts("CHUNK_PERF")) {
            RenderAuditBridge.counter(
                    "CHUNK_PERF",
                    "SCHEDULER_NS",
                    System.nanoTime() - frameStart,
                    "queue=" + rebuildQueue.size());
            RenderAuditBridge.counter("CHUNK_PERF", "SCHEDULER_FRAME_COUNT", 1, "");
        }
    }

    private static boolean currentOwner(ChunkBuildState state) {
        if (state.primary) return chunkBuildStates.get(state.section.index) == state;
        ExternalChunk external = externalChunks.get(state.section);
        return external != null
                && external.buildState == state
                && external.nativeId == state.nativeIndex;
    }

    private static void buildFailed(
            ChunkBuildState state,
            long generation,
            boolean important,
            String stage,
            Exception error) {
        synchronized (state) {
            if (!currentOwner(state) || state.generation.get() != generation) return;
            // A retry is not a new source mutation. setDirty would re-enter source hooks,
            // advance the revision and bypass the backoff on every failed attempt.
            state.preparedGeneration = -1;
            state.interactive |= important;
            long now = System.nanoTime();
            if (state.retry.failed(generation, now))
                LOG.error(
                        "Section {} at {} version {} failed during {}; retaining published geometry, retry in {} ms",
                        state.nativeIndex,
                        state.section.getOrigin(),
                        generation,
                        stage,
                        state.retry.delayMillis(now),
                        error);
        }
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void runBuild(
            ChunkBuildTicket ticket,
            RenderChunkRegion chunkRendererRegion,
            SectionRenderDispatcher chunkBuilder,
            IChunkBuilderExt chunkBuilderExt,
            List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            boolean important) {
        if (!ticket.isCurrent()) return;
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            SectionBufferBuilderPack storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(
                    chunkRendererRegion,
                    chunkBuilder,
                    chunkBuilderExt,
                    ticket.section,
                    storage,
                    additionalRenderers,
                    ticket.nativeIndex,
                    important,
                    ticket.primary,
                    ticket,
                    ticket.origin,
                    ticket.textureIds,
                    ticket.faceStates);
            synchronized (ticket.state) {
                if (ticket.isCurrent()) {
                    ticket.state.preparedGeneration = ticket.generation;
                    ticket.state.retry.succeeded();
                }
            }
        } catch (Exception e) {
            if (ticket.isCurrent())
                buildFailed(ticket.state, ticket.generation, important, "compile", e);
            RenderAuditBridge.counter("CHUNK_BUILD", "FAILED", 1, e.getClass().getSimpleName());
        }
    }

    private static void rebuildSingle(
            RenderChunkRegion chunkRendererRegion,
            SectionRenderDispatcher chunkBuilder,
            IChunkBuilderExt chunkBuilderExt,
            SectionRenderDispatcher.RenderSection builtChunk,
            SectionBufferBuilderPack storage,
            List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            long nativeIndex,
            boolean important,
            boolean countBuiltChunk,
            ChunkBuildTicket ticket,
            BlockPos buildOrigin,
            Map<ResourceLocation, Integer> textureIds,
            Map<RenderType, Integer> faceStates) {

        long requestedRevision = ticket.generation;
        SectionPos chunkSectionPos = SectionPos.of(buildOrigin);

        Vec3 vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorting vertexSorter =
                VertexSorting.byDistance(
                        (float) (vec3d.x - buildOrigin.getX()),
                        (float) (vec3d.y - buildOrigin.getY()),
                        (float) (vec3d.z - buildOrigin.getZ()));

        boolean external = !countBuiltChunk;
        long compileStart = RenderAuditBridge.accepts("CHUNK_PERF") ? System.nanoTime() : 0L;
        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
            RenderAuditBridge.counter(
                    "CHUNK_UPDATE",
                    "COMPILE_BEGIN",
                    requestedRevision,
                    "id=" + nativeIndex + "; java_ns=" + System.nanoTime());
        SectionCompiler.Results renderData =
                ((IChunkBuilderExt) chunkBuilder)
                        .radiance$getSectionBuilder()
                        .compile(
                                chunkSectionPos,
                                chunkRendererRegion,
                                vertexSorter,
                                storage,
                                additionalRenderers);
        if (compileStart != 0L) {
            RenderAuditBridge.counter(
                    "CHUNK_PERF", "COMPILE_NS", System.nanoTime() - compileStart, "");
            RenderAuditBridge.counter("CHUNK_PERF", "COMPILE_COUNT", 1, "");
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "COMPILED", 1, "");
        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
            RenderAuditBridge.counter(
                    "CHUNK_UPDATE",
                    "COMPILE_END",
                    requestedRevision,
                    "id=" + nativeIndex + "; java_ns=" + System.nanoTime());

        Map<RenderType, MeshData> allBuffers = renderData.renderedLayers;
        Map<RenderType, MeshData> buffers = new java.util.LinkedHashMap<>();
        allBuffers.forEach(
                (layer, mesh) -> {
                    if (!com.radiance.api.render.SourceHooks.retainOffscreenLayer(layer))
                        buffers.put(layer, mesh);
                });
        Object publicationLock = ticket.state;
        synchronized (publicationLock) {
            if (!ticket.isCurrent()) {
                RenderAuditBridge.counter("CHUNK_BUILD", "STALE_DISCARDED", 1, "");
                closeMeshes(allBuffers);
                return;
            }
            builtChunk.updateGlobalBlockEntities(renderData.globalBlockEntities);

            if (buffers.isEmpty()) {
                RenderAuditBridge.counter("CHUNK_BUILD", "EMPTY", 1, "");
                SectionRenderDispatcher.CompiledSection chunkData =
                        new com.radiance.client.render.RasterCompiledSection(vertexSorter) {
                            @Override
                            public List<BlockEntity> getRenderableBlockEntities() {
                                return renderData.blockEntities;
                            }

                            @Override
                            public boolean facesCanSeeEachother(Direction from, Direction to) {
                                return renderData.visibilitySet.visibilityBetween(from, to);
                            }

                            @Override
                            public boolean isEmpty(RenderType layer) {
                                return layer == null
                                        ? allBuffers.isEmpty()
                                        : !allBuffers.containsKey(layer);
                            }
                        };
                publishCompiled(builtChunk, chunkData);
                if (countBuiltChunk) {
                    builtChunkNum++;
                }

                invalidateSingle(nativeIndex, requestedRevision);
                if (ticket != null) {
                    ticket.state.hasNativeGeometry.set(false);
                }
                RenderAuditBridge.counter("CHUNK_BUILD", "PUBLISHED", 1, "");
            } else {
                SectionRenderDispatcher.CompiledSection chunkData =
                        new com.radiance.client.render.RasterCompiledSection(vertexSorter) {
                            @Override
                            public List<BlockEntity> getRenderableBlockEntities() {
                                return renderData.blockEntities;
                            }

                            @Override
                            public boolean facesCanSeeEachother(Direction from, Direction to) {
                                return renderData.visibilitySet.visibilityBetween(from, to);
                            }

                            @Override
                            public boolean isEmpty(RenderType layer) {
                                return layer == null
                                        ? allBuffers.isEmpty()
                                        : !allBuffers.containsKey(layer);
                            }
                        };
                publishCompiled(builtChunk, chunkData);
                if (countBuiltChunk) {
                    builtChunkNum++;
                }

                ByteBuffer geometryTypeBB = null;
                ByteBuffer geometryGroupNameBB = null;
                ByteBuffer geometryMaterialFlagsBB = null;
                ByteBuffer geometryTextureBB = null;
                ByteBuffer vertexFormatBB = null;
                ByteBuffer vertexCountBB = null;
                ByteBuffer verticesBB = null;
                List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());

                try {
                    int geometryTypeSize = buffers.size() * Integer.BYTES;
                    geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
                    long geometryTypeAddr = memAddress(geometryTypeBB);
                    int geometryTypeBaseAddr = 0;

                    int geometryGroupNameSize = buffers.size() * Long.BYTES;
                    geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
                    long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
                    int geometryGroupNameBaseAddr = 0;

                    int geometryMaterialFlagsSize = buffers.size() * Integer.BYTES;
                    geometryMaterialFlagsBB = MemoryUtil.memAlloc(geometryMaterialFlagsSize);
                    long geometryMaterialFlagsAddr = memAddress(geometryMaterialFlagsBB);
                    int geometryMaterialFlagsBaseAddr = 0;

                    int geometryTextureSize = buffers.size() * Integer.BYTES;
                    geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
                    long geometryTextureAddr = memAddress(geometryTextureBB);
                    int geometryTextureBaseAddr = 0;

                    int vertexFormatSize = buffers.size() * Integer.BYTES;
                    vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                    long vertexFormatAddr = memAddress(vertexFormatBB);
                    int vertexFormatBaseAddr = 0;

                    int vertexCountSize = buffers.size() * Integer.BYTES;
                    vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                    long vertexCountAddr = memAddress(vertexCountBB);
                    int vertexCountBaseAddr = 0;

                    int verticesSize = buffers.size() * Long.BYTES;
                    verticesBB = MemoryUtil.memAlloc(verticesSize);
                    long verticesAddr = memAddress(verticesBB);
                    int verticesBaseAddr = 0;

                    for (Map.Entry<RenderType, MeshData> entry : buffers.entrySet()) {
                        RenderType renderLayer = entry.getKey();
                        assert renderLayer.mode() == QUADS;

                        MeshData vertexBuffer = entry.getValue();
                        BufferProxy.BufferInfo vertexBufferInfo =
                                BufferProxy.getBufferInfo(vertexBuffer.vertexBuffer());
                        assert vertexBuffer.drawState().indexCount()
                                == vertexBuffer.drawState().vertexCount() / 4 * 6;

                        int geometryTypeID =
                                Constants.GeometryTypes.getGeometryType(renderLayer, true)
                                        .getValue();
                        ResourceLocation textureLocation =
                                ((RenderType.CompositeRenderType) renderLayer)
                                        .state
                                        .textureState
                                        .cutoutTexture()
                                        .orElse(MissingTextureAtlasSprite.getLocation());
                        Integer geometryTextureID = textureIds.get(textureLocation);
                        if (geometryTextureID == null) {
                            geometryTextureID =
                                    textureIds.get(MissingTextureAtlasSprite.getLocation());
                            RenderAuditBridge.counter(
                                    "CHUNK_BUILD",
                                    "UNKNOWN_TEXTURE",
                                    1,
                                    textureLocation.toString());
                        }
                        int vertexFormatID =
                                Constants.VertexFormats.getValue(vertexBuffer.drawState().format());

                        geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                        geometryTypeBaseAddr += Integer.BYTES;

                        ByteBuffer geometryGroupNameBuffer =
                                MemoryUtil.memUTF8(renderLayer.name, true);
                        geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                        geometryGroupNameBB.putLong(
                                geometryGroupNameBaseAddr, memAddress(geometryGroupNameBuffer));
                        geometryGroupNameBaseAddr += Long.BYTES;

                        RenderType.CompositeState renderState =
                                ((RenderType.CompositeRenderType) renderLayer).state;
                        RenderTypeCompositeStateAccessor renderStateAccessor =
                                (RenderTypeCompositeStateAccessor) (Object) renderState;
                        Integer materialFlags = faceStates.get(renderLayer);
                        if (materialFlags == null)
                            throw new IllegalStateException(
                                    "Missing draw-state snapshot: " + renderLayer);
                        geometryMaterialFlagsBB.putInt(
                                geometryMaterialFlagsBaseAddr, materialFlags);
                        geometryMaterialFlagsBaseAddr += Integer.BYTES;

                        geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                        geometryTextureBaseAddr += Integer.BYTES;

                        vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                        vertexFormatBaseAddr += Integer.BYTES;

                        vertexCountBB.putInt(
                                vertexCountBaseAddr, vertexBuffer.drawState().vertexCount());
                        vertexCountBaseAddr += Integer.BYTES;

                        verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                        verticesBaseAddr += Long.BYTES;
                    }

                    long nativeStart =
                            RenderAuditBridge.accepts("CHUNK_PERF") ? System.nanoTime() : 0L;
                    boolean replacingNativeGeometry =
                            ticket != null && ticket.state.hasNativeGeometry.get();
                    long nativeSubmitIntent =
                            RenderAuditBridge.beginIntent(
                                    "CHUNK_NATIVE_SUBMIT",
                                    "ChunkProxy.rebuildSingle",
                                    "nativeId="
                                            + nativeIndex
                                            + "; generation="
                                            + (requestedRevision)
                                            + "; origin="
                                            + buildOrigin.getX()
                                            + ','
                                            + buildOrigin.getY()
                                            + ','
                                            + buildOrigin.getZ()
                                            + "; geometryCount="
                                            + buffers.size()
                                            + "; javaPriority="
                                            + important
                                            + "; replacing="
                                            + replacingNativeGeometry);
                    if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                        RenderAuditBridge.counter(
                                "CHUNK_UPDATE",
                                "NATIVE_CALL",
                                requestedRevision,
                                "id=" + nativeIndex + "; java_ns=" + System.nanoTime());
                    try (var request =
                            com.radiance.api.audit.DrawingAudit.begin(
                                    "SECTION",
                                    "SectionCompiler",
                                    ticket == null ? "CAPTURE" : "DIRECT",
                                    "WORLD")) {
                        rebuildSingle(
                                buildOrigin.getX(),
                                buildOrigin.getY(),
                                buildOrigin.getZ(),
                                nativeIndex,
                                requestedRevision,
                                buffers.size(),
                                geometryTypeAddr,
                                geometryGroupNameAddr,
                                geometryMaterialFlagsAddr,
                                geometryTextureAddr,
                                vertexFormatAddr,
                                vertexCountAddr,
                                verticesAddr,
                                important ? 1 : 0,
                                countBuiltChunk);
                        com.radiance.api.audit.DrawingAudit.consumed("native.section.enqueue");
                    }
                    RenderAuditBridge.transition(
                            nativeSubmitIntent,
                            "ACCEPTED",
                            "MCVR_CHUNK_SCENE",
                            "native rebuild call returned",
                            true);
                    if (ticket != null) {
                        ticket.state.hasNativeGeometry.set(true);
                    }
                    if (nativeStart != 0L) {
                        RenderAuditBridge.counter(
                                "CHUNK_PERF",
                                "NATIVE_SUBMIT_NS",
                                System.nanoTime() - nativeStart,
                                "");
                        RenderAuditBridge.counter("CHUNK_PERF", "NATIVE_SUBMIT_COUNT", 1, "");
                    }
                    RenderAuditBridge.counter("CHUNK_BUILD", "NATIVE_SUBMITTED", 1, "");
                } finally {
                    if (geometryTypeBB != null) {
                        MemoryUtil.memFree(geometryTypeBB);
                    }
                    if (geometryGroupNameBB != null) {
                        MemoryUtil.memFree(geometryGroupNameBB);
                    }
                    if (geometryMaterialFlagsBB != null) {
                        MemoryUtil.memFree(geometryMaterialFlagsBB);
                    }
                    if (geometryTextureBB != null) {
                        MemoryUtil.memFree(geometryTextureBB);
                    }
                    if (vertexFormatBB != null) {
                        MemoryUtil.memFree(vertexFormatBB);
                    }
                    if (vertexCountBB != null) {
                        MemoryUtil.memFree(vertexCountBB);
                    }
                    if (verticesBB != null) {
                        MemoryUtil.memFree(verticesBB);
                    }
                    for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                        MemoryUtil.memFree(geometryGroupNameBuffer);
                    }
                }
                RenderAuditBridge.counter("CHUNK_BUILD", "PUBLISHED", 1, "");
            }

            if (external) {
                ExternalChunk owner = externalChunks.get(builtChunk);
                if (owner != null
                        && externalChunks.get(builtChunk) == owner
                        && owner.nativeId == nativeIndex) {
                    com.radiance.client.render.SectionRasterStorage.publish(
                            builtChunk, allBuffers, ticket::ownsSection);
                }
            }
            if (!external) {
                Map<RenderType, MeshData> raster = new java.util.LinkedHashMap<>();
                allBuffers.forEach(
                        (layer, mesh) -> {
                            if (com.radiance.api.render.SourceHooks.retainOffscreenLayer(layer))
                                raster.put(layer, mesh);
                        });
                com.radiance.client.render.SectionRasterStorage.publish(
                        builtChunk, raster, ticket::ownsSection);
            }
            closeMeshes(allBuffers);
        }
    }

    private static void closeMeshes(Map<RenderType, MeshData> meshes) {
        for (MeshData mesh : meshes.values()) {
            mesh.close();
        }
    }

    /**
     * TextureManager is owned by the render thread. Snapshot the GL names before a build is handed
     * to a worker so high-distance compilation never races texture reload or disposal.
     */
    private static Map<RenderType, Integer> snapshotChunkFaces() {
        Map<RenderType, Integer> states = new HashMap<>();
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            states.put(layer, com.radiance.client.render.MaterialFaces.capture(layer));
        }
        return Map.copyOf(states);
    }

    private static Map<ResourceLocation, Integer> snapshotChunkTextureIds() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        Map<ResourceLocation, Integer> result = new HashMap<>();
        ResourceLocation missing = MissingTextureAtlasSprite.getLocation();
        result.put(missing, textureManager.getTexture(missing).getId());
        for (RenderType renderType : RenderType.chunkBufferLayers()) {
            if (!(renderType instanceof RenderType.CompositeRenderType composite)) {
                continue;
            }
            ResourceLocation texture = composite.state.textureState.cutoutTexture().orElse(missing);
            result.computeIfAbsent(
                    texture, location -> textureManager.getTexture(location).getId());
        }
        return Map.copyOf(result);
    }

    private static native void rebuildSingle(
            int originX,
            int originY,
            int originZ,
            long index,
            long generation,
            int size,
            long geometryTypes,
            long geometryGroupNames,
            long geometryMaterialFlags,
            long geometryTextures,
            long vertexFormats,
            long vertexCounts,
            long vertices,
            int priority,
            boolean collectEmission);

    private static native long allocateExternalChunkNative();

    private static native void releaseExternalChunkNative(long index);

    public static native boolean isChunkReady(long index);

    public static native int countReadyChunksNative();

    // Low word: queued + preparing + submitted + awaiting-publication owners.
    // High word: saturating logical native input bytes; this is not total GPU allocation.
    private static native void completeBuildsNative();

    private static native long advanceBuildsNative();

    public static boolean isChunkReady(SectionRenderDispatcher.RenderSection builtChunk) {
        var external = externalChunks.get(builtChunk);
        if (external != null) return isChunkReady(external.nativeId);
        return !com.radiance.api.render.SourceHooks.isExternal(builtChunk)
                && isChunkReady(builtChunk.index);
    }

    private static native void markChunkDirtyNative(long index, long generation);

    public static native void relocateSingle(
            long index, int originX, int originY, int originZ, long generation);

    public static native void invalidateSingle(long index, long generation);

    public static native String performanceSnapshotNative();

    /** Optional bounded trace; clock sample must be bracketed by Java nanoTime for calibration. */
    public static native String drainUpdateTraceNative();

    private static long horizontalDistanceSquared(int sectionX, int sectionZ) {
        long dx = sectionX - cameraSectionX;
        long dz = sectionZ - cameraSectionZ;
        return dx * dx + dz * dz;
    }

    private static void reprioritizeQueues() {
        List<ColumnRequest> columns = new ArrayList<>();
        columnQueue.drainTo(columns);
        for (ColumnRequest request : columns) {
            int sectionX = ChunkPos.getX(request.column());
            int sectionZ = ChunkPos.getZ(request.column());
            columnQueue.offer(
                    new ColumnRequest(
                            request.column(),
                            horizontalDistanceSquared(sectionX, sectionZ),
                            request.sequence()));
        }

        rebuildQueue.reprioritize(
                request -> {
                    ChunkBuildState state = request.state();
                    if (state == null || !state.queued.get()) {
                        return null;
                    }
                    BlockPos origin = state.section.getOrigin();
                    return new BuildRequest(
                            state,
                            sectionPriority(state),
                            request.sequence(),
                            state.interactive
                                    || state.section.isDirtyFromPlayer()
                                    || request.interactive(),
                            request.enqueuedAt());
                });
    }

    private record ColumnRequest(long column, long distanceSquared, long sequence)
            implements Comparable<ColumnRequest> {
        @Override
        public int compareTo(ColumnRequest other) {
            int distance = Long.compare(distanceSquared, other.distanceSquared);
            return distance != 0 ? distance : Long.compare(sequence, other.sequence);
        }
    }

    private static SectionBuildPriority sectionPriority(ChunkBuildState state) {
        double cameraX = (cameraSectionX + .5) * 16;
        double cameraY = (cameraSectionY + .5) * 16;
        double cameraZ = (cameraSectionZ + .5) * 16;
        var bounds = state.schedulingBounds;
        if (bounds != null)
            return SectionBuildPriority.at(
                    bounds.x, bounds.y, bounds.z, bounds.hx, bounds.hy, bounds.hz, cameraX, cameraY,
                    cameraZ);
        var origin = state.section.getOrigin();
        return SectionBuildPriority.at(
                origin.getX() + 8.0,
                origin.getY() + 8.0,
                origin.getZ() + 8.0,
                cameraX,
                cameraY,
                cameraZ);
    }

    private static void updateSchedulingBounds(ChunkBuildState state) {
        var matrix = state.renderSpace.transform(new org.joml.Matrix4d()).mul(state.localTransform);
        var center = matrix.transformPosition(new org.joml.Vector3d(8, 8, 8));
        state.schedulingBounds =
                new SchedulingBounds(
                        center.x,
                        center.y,
                        center.z,
                        8
                                * (Math.abs(matrix.m00())
                                        + Math.abs(matrix.m10())
                                        + Math.abs(matrix.m20())),
                        8
                                * (Math.abs(matrix.m01())
                                        + Math.abs(matrix.m11())
                                        + Math.abs(matrix.m21())),
                        8
                                * (Math.abs(matrix.m02())
                                        + Math.abs(matrix.m12())
                                        + Math.abs(matrix.m22())));
    }

    private record SchedulingBounds(
            double x, double y, double z, double hx, double hy, double hz) {}

    private record BuildRequest(
            ChunkBuildState state,
            SectionBuildPriority distanceSquared,
            long sequence,
            boolean interactive,
            long enqueuedAt)
            implements Comparable<BuildRequest> {
        long index() {
            return state.nativeIndex;
        }

        @Override
        public int compareTo(BuildRequest other) {
            int priority = Boolean.compare(other.interactive, interactive);
            if (priority != 0) return priority;
            int distance = distanceSquared.compareTo(other.distanceSquared);
            return distance != 0 ? distance : Long.compare(sequence, other.sequence);
        }
    }

    private static final class ChunkBuildState {
        private volatile SectionRenderDispatcher.RenderSection section;
        private final long nativeIndex;
        private final boolean primary;
        private com.radiance.api.render.RenderSpace renderSpace;
        private org.joml.Matrix4d localTransform;
        private volatile SchedulingBounds schedulingBounds;
        private final AtomicLong generation = new AtomicLong();
        private final AtomicBoolean queued = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean hasNativeGeometry = new AtomicBoolean();
        private final SectionBuildRetry retry = new SectionBuildRetry();
        private volatile boolean interactive;
        private volatile boolean occupancyEmpty;
        private volatile boolean skipNeighborRefresh;
        private volatile boolean waitingForSource;
        private volatile long changedAt;
        private volatile long preparedGeneration = -1;
        private volatile long contentChangeGeneration = -1;

        private ChunkBuildState(SectionRenderDispatcher.RenderSection section) {
            this(section, section.index, true);
        }

        private ChunkBuildState(
                SectionRenderDispatcher.RenderSection section, long nativeIndex, boolean primary) {
            this.section = section;
            this.nativeIndex = nativeIndex;
            this.primary = primary;
        }
    }

    private record ChunkBuildTicket(
            ChunkBuildState state,
            SectionRenderDispatcher.RenderSection section,
            BlockPos origin,
            long generation,
            long epoch,
            long nativeIndex,
            boolean primary,
            Map<ResourceLocation, Integer> textureIds,
            Map<RenderType, Integer> faceStates) {
        private boolean isCurrent() {
            return ownsSection() && state.generation.get() == generation;
        }

        private boolean ownsSection() {
            BlockPos currentOrigin = section.getOrigin();
            return acceptingSectionEvents
                    && storageEpoch.get() == epoch
                    && state.section == section
                    && currentOwner(state)
                    && currentOrigin.getX() == origin.getX()
                    && currentOrigin.getY() == origin.getY()
                    && currentOrigin.getZ() == origin.getZ();
        }
    }

    private static final class ExternalChunk {
        private final long nativeId;
        private final ChunkBuildState buildState;

        private ExternalChunk(long nativeId, ChunkBuildState buildState) {
            this.nativeId = nativeId;
            this.buildState = buildState;
        }
    }
}
