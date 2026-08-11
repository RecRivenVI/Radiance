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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4dc;

public class ChunkProxy {

    public static final SectionRenderDispatcher.CompiledSection PROCESSED = new SectionRenderDispatcher.CompiledSection() {
        @Override
        public boolean facesCanSeeEachother(Direction from, Direction to) {
            return false;
        }
    };
    public static final SectionRenderDispatcher.CompiledSection TERRAIN_EMPTY = new SectionRenderDispatcher.CompiledSection() {
        @Override
        public boolean facesCanSeeEachother(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, ChunkBuildState> chunkBuildStates = new ConcurrentHashMap<>();
    private static final PriorityBlockingQueue<BuildRequest> rebuildQueue =
        new PriorityBlockingQueue<>();
    private static final PriorityBlockingQueue<ColumnRequest> columnQueue =
        new PriorityBlockingQueue<>();
    private static final Set<Long> queuedColumns = ConcurrentHashMap.newKeySet();
    private static final Map<SectionRenderDispatcher.RenderSection, ExternalChunk> externalChunks =
        new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final AtomicLong storageEpoch = new AtomicLong();
    private static final AtomicLong queueSequence = new AtomicLong();
    private static final AtomicInteger importantBuildsInFlight = new AtomicInteger();
    private static final AtomicInteger normalBuildsInFlight = new AtomicInteger();
    private static final int MAX_EXTERNAL_REBUILDS_PER_FRAME = 8;
    private static final ExternalSectionBuildScheduler<ExternalChunk> externalBuildScheduler =
        new ExternalSectionBuildScheduler<>(new AtomicInteger(), 1,
            MAX_EXTERNAL_REBUILDS_PER_FRAME);
    private static volatile ViewArea currentStorage = null;
    private static final SectionPresenceIndex<SectionRenderDispatcher.RenderSection,
        SectionRenderDispatcher.CompiledSection> blockEntitySections = new SectionPresenceIndex<>();
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
    private static int numNormalChunkRebuildThreads = Math.max(1,
        numChunkRebuildThreads - numImportantChunkRebuildThreads);
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<SectionBufferBuilderPack>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(SectionBufferBuilderPack::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord) {
        clear();
        storageSizeX = sizeX;
        storageSizeY = sizeY;
        storageSizeZ = sizeZ;
        storageBottomSection = bottomSectionCoord;
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
        RenderAuditBridge.counter("CHUNK_BUILD", "RESET", 1,
            "slots=" + numChunks + "; grid=" + sizeX + 'x' + sizeY + 'x' + sizeZ);
    }

    public static void updateSectionPos(SectionPos sectionPos) {
        updateSectionPosNative(sectionPos.x(), sectionPos.y(),
            sectionPos.z());
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
        synchronized (blockEntitySections) {
            var compiled = section.getCompiled();
            blockEntitySections.publish(section, compiled, !compiled.getRenderableBlockEntities().isEmpty());
        }
    }

    static List<SectionPresenceIndex.Entry<SectionRenderDispatcher.RenderSection,
        SectionRenderDispatcher.CompiledSection>> blockEntitySections(ViewArea storage) {
        if (storage != currentStorage) throw new IllegalStateException("Block-entity index belongs to another ViewArea");
        return blockEntitySections.snapshot();
    }

    private static void publishCompiled(SectionRenderDispatcher.RenderSection section,
        SectionRenderDispatcher.CompiledSection compiled) {
        synchronized (blockEntitySections) {
            section.compiled.set(compiled);
            blockEntitySections.publish(section, compiled, !compiled.getRenderableBlockEntities().isEmpty());
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
            long column = ChunkPos.asLong(SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()));
            if (!loadedColumns.add(column)) {
                continue;
            }
        }
        cameraSectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraX));
        cameraSectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraZ));
        loadedColumns.stream()
            .sorted(Comparator.comparingLong(column -> {
                long dx = ChunkPos.getX(column) - cameraSectionX;
                long dz = ChunkPos.getZ(column) - cameraSectionZ;
                return dx * dx + dz * dz;
            }))
            .forEach(column -> requestColumn(ChunkPos.getX(column), ChunkPos.getZ(column)));
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal = RenderType.chunkBufferLayers()
            .stream()
            .mapToInt(RenderType::bufferSize)
            .sum();
        int memoryLimited = Math.max(1,
            (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4) - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(2,
            Math.min(userThreads, Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final SectionBufferBuilderPack s = blockBufferAllocatorStorageThreadLocal.get();
        s.discardAll();
        return s::clearAll;
    }

    /**
     * Vanilla's visible-section list is intentionally bypassed by Radiance, so its normal F3
     * counter is unavailable. Count the actually compiled, non-empty section meshes instead of
     * the historical rebuild-attempt counter (which could report zero or grow on repeat builds).
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
        storageEpoch.incrementAndGet();
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        numChunkRebuildThreads = getChunkRebuildThreadCount();
        numNormalChunkRebuildThreads = Math.max(1,
            numChunkRebuildThreads - numImportantChunkRebuildThreads);
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
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
            externalBuildScheduler.cancel(external.nativeId, external.buildState);
        }
        externalChunks.clear();
        blockEntitySections.reset(List.of());
        com.radiance.client.render.SectionRasterStorage.clear();
        externalBuildScheduler.clearPending();
        rebuildTasks.clear();
        queueSequence.set(0L);
        currentStorage = null;
        importantBuildsInFlight.set(0);
        normalBuildsInFlight.set(0);
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk) {
        enqueueRebuild(chunk, chunk.isDirtyFromPlayer());
    }

    public static void promoteRebuild(SectionRenderDispatcher.RenderSection chunk) {
        if (!acceptingSectionEvents) return;
        ChunkBuildState state=chunkBuildStates.computeIfAbsent(chunk.index, ignored -> new ChunkBuildState(chunk));
        synchronized(state) {
            state.interactive=true;
            state.section=chunk;
            rebuildQueue.removeIf(request -> request.index()==chunk.index);
            state.queued.set(false);
            queueState(state);
        }
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk, boolean interactive) {
        if (!acceptingSectionEvents) {
            return;
        }
        ChunkBuildState state = chunkBuildStates.computeIfAbsent(chunk.index,
            ignored -> new ChunkBuildState(chunk));
        synchronized (state) {
            state.section = chunk;
            state.interactive |= interactive;
            state.occupancyEmpty = false;
            state.skipNeighborRefresh = false;
            long generation = state.generation.incrementAndGet();
            markChunkDirtyNative(chunk.index, generation);
            if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
            RenderAuditBridge.counter("CHUNK_UPDATE","DIRTY",generation,
                "action="+PlayerSectionUpdates.request(chunk.getOrigin())+"; id="+chunk.index+"; java_ns="+System.nanoTime()
                    +"; origin="+chunk.getOrigin()+"; epoch="+storageEpoch.get());
        }
        if (interactive) {
            // Preserve all dirty dependency notifications, but do not delay this section behind a column scan.
            synchronized (state) {
                rebuildQueue.removeIf(request -> request.index() == chunk.index);
                state.queued.set(false);
                queueState(state);
            }
            return;
        }
        // A dirty notification says that the section must be reconsidered, not that it is ready
        // to compile. Route it through the column occupancy pass first so unloaded/empty slots do
        // not flood RenderRegionCache and the native build queues.
        BlockPos origin = chunk.getOrigin();
        requestColumn(SectionPos.blockToSectionCoord(origin.getX()),
            SectionPos.blockToSectionCoord(origin.getZ()));
    }

    public static void relocateSection(SectionRenderDispatcher.RenderSection section,
        int originX, int originY, int originZ) {
        if (!acceptingSectionEvents) {
            relocateSingle(section.index, originX, originY, originZ, 0L);
            return;
        }
        ChunkBuildState state = chunkBuildStates.computeIfAbsent(section.index,
            ignored -> new ChunkBuildState(section));
        synchronized (state) {
            state.section = section;
            // ViewArea reuses native slots as the camera moves. Occupancy belongs to the old
            // world coordinate and must never decide whether the relocated section is empty.
            state.occupancyEmpty = false;
            state.skipNeighborRefresh = false;
            long generation = state.generation.incrementAndGet();
            relocateSingle(section.index, originX, originY, originZ, generation);
        }
        if (section.isDirty()
            || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
            requestColumn(SectionPos.blockToSectionCoord(originX),
                SectionPos.blockToSectionCoord(originZ));
        }
    }

    private static void queueState(ChunkBuildState state) {
        if (state.running.get()) {
            RenderAuditBridge.counter("CHUNK_BUILD", "COALESCED_RUNNING", 1, "");
            return;
        }
        if (state.queued.compareAndSet(false, true)) {
            BlockPos origin = state.section.getOrigin();
            int sectionX = SectionPos.blockToSectionCoord(origin.getX());
            int sectionZ = SectionPos.blockToSectionCoord(origin.getZ());
            rebuildQueue.offer(new BuildRequest(state.section.index,
                sectionDistanceSquared(origin), queueSequence.getAndIncrement(),
                state.interactive || state.section.isDirtyFromPlayer(), System.nanoTime()));
            RenderAuditBridge.counter("CHUNK_BUILD", "ENQUEUED", 1, "");
        } else {
            RenderAuditBridge.counter("CHUNK_BUILD", "COALESCED_QUEUED", 1, "");
        }
    }

    public static void syncExternalSection(SectionRenderDispatcher.RenderSection section,
        Matrix4dc transform) {
        ExternalChunk external = externalChunks.computeIfAbsent(section,
            value -> {
                long nativeId = allocateExternalChunkNative();
                return new ExternalChunk(nativeId, value,
                    externalBuildScheduler.createState());
            });
        if (external.nativeId < 0) {
            return;
        }

        updateExternalChunkTransformNative(external.nativeId,
            transform.m00(), transform.m10(), transform.m20(), transform.m30(),
            transform.m01(), transform.m11(), transform.m21(), transform.m31(),
            transform.m02(), transform.m12(), transform.m22(), transform.m32());
        if (section.isDirty()
            || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
            externalBuildScheduler.request(external.nativeId, external, external.buildState);
        }
    }

    public static void enqueueExternalRebuild(SectionRenderDispatcher.RenderSection section) {
        ExternalChunk external = externalChunks.get(section);
        if (external != null && external.nativeId >= 0) {
            externalBuildScheduler.request(external.nativeId, external, external.buildState);
        }
    }

    public static void releaseMissingExternalSections(
        Set<SectionRenderDispatcher.RenderSection> activeSections) {
        for (Map.Entry<SectionRenderDispatcher.RenderSection, ExternalChunk> entry
            : externalChunks.entrySet()) {
            if (activeSections.contains(entry.getKey())
                || !externalChunks.remove(entry.getKey(), entry.getValue())) {
                continue;
            }

            ExternalChunk external = entry.getValue();
            synchronized (external) {
                externalBuildScheduler.cancel(external.nativeId, external.buildState);
                com.radiance.client.render.SectionRasterStorage.discard(entry.getKey());
                releaseExternalChunkNative(external.nativeId);
            }
        }
    }

    public static void rebuildAll() {
        ViewArea storage = currentStorage;
        if (storage == null || storage.sections == null) {
            return;
        }
        for (SectionRenderDispatcher.RenderSection builtChunk : storage.sections) {
            if (builtChunk == null
                || builtChunk.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            builtChunk.setDirty(false);
        }
    }

    public static void onChunkLoaded(ChunkPos loadedChunk) {
        if (!acceptingSectionEvents || currentStorage == null) {
            return;
        }
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
     * Sections are allowed to compile while one or more neighboring columns are absent. The
     * vanilla region cache represents those neighbors as empty chunks, which keeps terrain at the
     * edge of the loaded range available to ray tracing. Once a real neighbor arrives, rebuild the
     * surrounding compiled sections so boundary faces, AO and mod-provided section geometry are
     * refreshed from real data.
     */
    private static void invalidateCompiledColumn(int sectionX, int sectionZ) {
        ViewArea storage = currentStorage;
        if (storage == null || storage.sections == null || storageSizeX <= 0
            || storageSizeY <= 0 || storageSizeZ <= 0) {
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
                || SectionPos.blockToSectionCoord(origin.getZ()) != sectionZ
                || section.isDirty()
                || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            ChunkBuildState state = chunkBuildStates.get(section.index);
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
            RenderAuditBridge.counter("CHUNK_BUILD", "NEIGHBOR_EMPTY_SKIPPED",
                skippedKnownEmpty, "");
        }
    }

    private static void requestColumn(int sectionX, int sectionZ) {
        long column = ChunkPos.asLong(sectionX, sectionZ);
        if (queuedColumns.add(column)) {
            columnQueue.offer(new ColumnRequest(column,
                horizontalDistanceSquared(sectionX, sectionZ), queueSequence.getAndIncrement()));
            RenderAuditBridge.counter("CHUNK_BUILD", "COLUMN_ENQUEUED", 1, "");
        }
    }

    private static void scanColumn(int sectionX, int sectionZ) {
        ViewArea storage = currentStorage;
        if (storage == null || storage.sections == null || storageSizeX <= 0
            || storageSizeY <= 0 || storageSizeZ <= 0) {
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
                && section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            ChunkBuildState state = chunkBuildStates.computeIfAbsent(section.index,
                ignored -> new ChunkBuildState(section));
            synchronized (state) {
                state.section = section;
            }
            if (dispatcherExt == null) {
                dispatcherExt = (IChunkBuilderExt) ((IChunkBuilderBuiltChunkExt) section)
                    .radiance$getChunkBuilder();
                levelChunk = dispatcherExt.radiance$getWorld().getChunk(sectionX, sectionZ,
                    ChunkStatus.FULL, false);
                if (levelChunk == null) {
                    return;
                }
            }
            int sectionY = SectionPos.blockToSectionCoord(origin.getY());
            int levelIndex = dispatcherExt.radiance$getWorld().getSectionIndexFromSectionY(sectionY);
            if (levelChunk != null && levelIndex >= 0
                && levelIndex < levelChunk.getSections().length
                && levelChunk.getSection(levelIndex).hasOnlyAir()) {
                state.occupancyEmpty = true;
                empty++;
                List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers =
                    ClientHooks.gatherAdditionalRenderers(origin, dispatcherExt.radiance$getWorld());
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
            RenderAuditBridge.counter("CHUNK_PERF", "COLUMN_SCAN_NS",
                System.nanoTime() - scanStart, "");
            RenderAuditBridge.counter("CHUNK_PERF", "COLUMN_SCAN_COUNT", 1, "");
        }
    }

    private static void publishEmpty(ChunkBuildState state,
        SectionRenderDispatcher.RenderSection section) {
        BlockPos origin = section.getOrigin().immutable();
        synchronized (state) {
            long generation = state.generation.get();
            ChunkBuildTicket ticket = new ChunkBuildTicket(state, section, origin, generation,
                storageEpoch.get(), section.index, true, Map.of(), Map.of());
            if (!ticket.isCurrent()) {
                RenderAuditBridge.counter("CHUNK_BUILD", "STALE_DISCARDED", 1, "empty");
                queueState(state);
                return;
            }
            section.setNotDirty();
            publishCompiled(section, SectionRenderDispatcher.CompiledSection.EMPTY);
            if (state.hasNativeGeometry.getAndSet(false)) {
                invalidateSingle(section.index, generation);
            }
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "EMPTY", 1, "");
        RenderAuditBridge.counter("CHUNK_BUILD", "PUBLISHED", 1, "");
    }

    public static void rebuild(Camera camera) {
        rebuildTasks.removeIf(Future::isDone);
        BlockPos blockPos = camera.getBlockPosition();
        cameraSectionY = SectionPos.blockToSectionCoord(blockPos.getY());
        int nextCameraSectionX = SectionPos.blockToSectionCoord(blockPos.getX());
        int nextCameraSectionZ = SectionPos.blockToSectionCoord(blockPos.getZ());
        if (nextCameraSectionX != cameraSectionX || nextCameraSectionZ != cameraSectionZ) {
            cameraSectionX = nextCameraSectionX;
            cameraSectionZ = nextCameraSectionZ;
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
        try (var round = new ChunkBuildDispatch.Round<>(rebuildQueue, MAX_REBUILD_ATTEMPTS_PER_FRAME)) {
        while (attempts < MAX_REBUILD_ATTEMPTS_PER_FRAME
            && (attempts < MIN_REBUILD_ATTEMPTS_PER_FRAME
                || System.nanoTime() - frameStart < REBUILD_BUDGET_NANOS)) {
            attempts++;
            BuildRequest request = attempts % 4 == 1
                ? round.pollPreferred(r -> !r.interactive() && System.nanoTime()-r.enqueuedAt() >= 250_000_000L,
                    Comparator.comparingLong(BuildRequest::enqueuedAt)) : round.poll();
            if (request == null) {
                break;
            }
            int index = request.index();
            ChunkBuildState state = chunkBuildStates.get(index);
            if (state == null) {
                continue;
            }
            state.queued.set(false);
            if (state.running.get()) {
                continue;
            }
            SectionRenderDispatcher.RenderSection builtChunk = state.section;
            RenderAuditBridge.counter("CHUNK_BUILD", "DEQUEUED", 1, "");
            if (!builtChunk.isDirty()) {
                continue;
            }
            BlockPos origin = builtChunk.getOrigin().immutable();
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            SectionRenderDispatcher chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            boolean isImportant = state.interactive || builtChunk.isDirtyFromPlayer();
            AtomicInteger inFlight = isImportant ? importantBuildsInFlight : normalBuildsInFlight;
            int limit = isImportant ? numImportantChunkRebuildThreads
                : Math.max(1, numNormalChunkRebuildThreads * 2);
            try {
                var result = ChunkBuildDispatch.submit(state.running, inFlight, limit, () -> {
                    long generation = state.generation.get();
                    long epoch = storageEpoch.get();
                    var world = chunkBuilderExt.radiance$getWorld();
                    var source = world.getChunk(SectionPos.blockToSectionCoord(origin.getX()),
                        SectionPos.blockToSectionCoord(origin.getZ()), ChunkStatus.FULL, false);
                    if (source == null) return null;
                    List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers =
                        ClientHooks.gatherAdditionalRenderers(origin, world);
                    int levelIndex = world.getSectionIndex(origin.getY());
                    if (levelIndex >= 0 && levelIndex < source.getSections().length
                        && source.getSection(levelIndex).hasOnlyAir() && additionalRenderers.isEmpty()) {
                        publishEmpty(state, builtChunk);
                        return () -> {};
                    }
                    long regionStart = RenderAuditBridge.accepts("CHUNK_PERF") ? System.nanoTime() : 0L;
                    RenderChunkRegion region = new RenderRegionCache().createRegion(
                        chunkBuilderExt.radiance$getWorld(), SectionPos.of(origin), additionalRenderers.isEmpty());
                    if (regionStart != 0L) {
                        RenderAuditBridge.counter("CHUNK_PERF", "REGION_CREATE_NS", System.nanoTime()-regionStart, "");
                        RenderAuditBridge.counter("CHUNK_PERF", "REGION_CREATE_COUNT", 1, "");
                    }
                    if (region == null) {
                        RenderAuditBridge.counter("CHUNK_BUILD", "REGION_UNAVAILABLE", 1, "");
                        return null;
                    }
                    ChunkBuildTicket ticket = new ChunkBuildTicket(state, builtChunk, origin,
                        generation, epoch, builtChunk.index, true,
                        snapshotChunkTextureIds(), snapshotChunkFaces());
                    synchronized (state) {
                        if (!ticket.isCurrent()) return null;
                        builtChunk.setNotDirty();
                        state.interactive=false;
                    }
                    if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                    RenderAuditBridge.counter("CHUNK_UPDATE", "ADMITTED", generation,
                        "id=" + builtChunk.index + "; epoch=" + ticket.epoch() + "; java_ns=" + System.nanoTime()
                            + "; priority=" + (isImportant ? 1 : 0) + "; queue_ns=" + (System.nanoTime()-request.enqueuedAt()));
                    RenderAuditBridge.counter("CHUNK_BUILD", isImportant ? "SCHEDULED_PRIORITY" : "SCHEDULED_NORMAL", 1,
                        "id="+builtChunk.index+"; generation="+generation+"; queue_ns="+(System.nanoTime()-request.enqueuedAt()));
                    return ChunkBuildDispatch.currentWork(ticket::isCurrent,
                        () -> runBuild(ticket, region, chunkBuilder, chunkBuilderExt, additionalRenderers, isImportant));
                }, isImportant ? task -> rebuildTasks.add(importantChunkRebuildExecutor.submit(task))
                               : backgroundChunkRebuildExecutor,
                    () -> { if (builtChunk.isDirty()) queueState(state); });
                if (result != ChunkBuildDispatch.Result.SUBMITTED) {
                    if (state.queued.compareAndSet(false, true)) round.defer(request);
                    RenderAuditBridge.counter("CHUNK_BUILD", "CAPACITY_DEFERRED", 1, result.name());
                }
            } catch (RuntimeException failure) {
                builtChunk.setDirty(isImportant);
                if (state.queued.compareAndSet(false, true)) round.defer(request);
                RenderAuditBridge.counter("CHUNK_BUILD", "PREPARE_OR_SUBMIT_FAILED", 1, failure.getClass().getSimpleName());
            }
        }
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "FRAME_ATTEMPTS", attempts, "");
        if (RenderAuditBridge.accepts("CHUNK_PERF")) {
            RenderAuditBridge.counter("CHUNK_PERF", "SCHEDULER_NS",
                System.nanoTime() - frameStart, "queue=" + rebuildQueue.size());
            RenderAuditBridge.counter("CHUNK_PERF", "SCHEDULER_FRAME_COUNT", 1, "");
        }

        int externalScheduled = externalBuildScheduler.schedule(
            ChunkProxy::prepareExternalBuild,
            backgroundChunkRebuildExecutor::execute,
            (ticket, phase, failure) -> RenderAuditBridge.counter("CHUNK_BUILD",
                "EXTERNAL_" + phase + "_FAILED", 1,
                failure.getClass().getSimpleName()));
        RenderAuditBridge.counter("CHUNK_BUILD", "EXTERNAL_SCHEDULED", externalScheduled, "");
    }

    private static ExternalSectionBuildScheduler.PreparedBuild prepareExternalBuild(
        ExternalSectionBuildScheduler.Ticket<ExternalChunk> schedulerTicket) {
        ExternalChunk external = schedulerTicket.owner();
        SectionRenderDispatcher.RenderSection builtChunk = external.section;
        if (!isCurrentExternal(schedulerTicket)) return () -> { };

        IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
        SectionRenderDispatcher chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
        IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers =
            ClientHooks.gatherAdditionalRenderers(builtChunk.getOrigin(),
                chunkBuilderExt.radiance$getWorld());
        RenderChunkRegion chunkRendererRegion = new RenderRegionCache().createRegion(
            chunkBuilderExt.radiance$getWorld(), SectionPos.of(builtChunk.getOrigin()),
            additionalRenderers.isEmpty());
        if (chunkRendererRegion == null) {
            // Missing source data is temporary. Keep the last published geometry and the request.
            RenderAuditBridge.counter("CHUNK_BUILD", "EXTERNAL_REGION_UNAVAILABLE", 1, "");
            return null;
        }

        BlockPos origin = builtChunk.getOrigin().immutable();
        Map<ResourceLocation, Integer> textureIds = snapshotChunkTextureIds();
        Map<RenderType, Integer> faceStates = snapshotChunkFaces();
        ExternalBuildTicket ticket = new ExternalBuildTicket(schedulerTicket, external, origin);
        builtChunk.setNotDirty();
        return () -> runExternalBuild(ticket, chunkRendererRegion, chunkBuilder, chunkBuilderExt,
            additionalRenderers, textureIds, faceStates);
    }

    private static void runExternalBuild(ExternalBuildTicket ticket,
        RenderChunkRegion chunkRendererRegion,
        SectionRenderDispatcher chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
        Map<ResourceLocation, Integer> textureIds, Map<RenderType, Integer> faceStates) throws Exception {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            SectionBufferBuilderPack storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt,
                ticket.external.section, storage, additionalRenderers, ticket.external.nativeId,
                true, false, null, ticket, ticket.origin, textureIds, faceStates);
        }
    }

    private static boolean isCurrentExternal(
        ExternalSectionBuildScheduler.Ticket<ExternalChunk> ticket) {
        ExternalChunk external = ticket.owner();
        return ticket.isCurrent() && externalChunks.get(external.section) == external;
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

    private static void runBuild(ChunkBuildTicket ticket,
        RenderChunkRegion chunkRendererRegion,
        SectionRenderDispatcher chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
        boolean important) {
        if (!ticket.isCurrent()) return;
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            SectionBufferBuilderPack storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, ticket.section,
                storage, additionalRenderers, ticket.nativeIndex, important, ticket.primary,
                ticket, null, ticket.origin, ticket.textureIds, ticket.faceStates);
        } catch (Exception e) {
            if (ticket.isCurrent()) ticket.section.setDirty(important);
            RenderAuditBridge.counter("CHUNK_BUILD", "FAILED", 1, e.getClass().getSimpleName());
        }
    }

    private static void rebuildSingle(RenderChunkRegion chunkRendererRegion,
        SectionRenderDispatcher chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        SectionRenderDispatcher.RenderSection builtChunk,
        SectionBufferBuilderPack storage,
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
        long nativeIndex,
        boolean important,
        boolean countBuiltChunk,
        ChunkBuildTicket ticket,
        ExternalBuildTicket externalTicket,
        BlockPos buildOrigin,
        Map<ResourceLocation, Integer> textureIds, Map<RenderType, Integer> faceStates) {

        SectionPos chunkSectionPos = SectionPos.of(buildOrigin);

        Vec3 vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorting
            vertexSorter =
            VertexSorting.byDistance((float) (vec3d.x - buildOrigin
                    .getX()),
                (float) (vec3d.y - buildOrigin
                    .getY()),
                (float) (vec3d.z - buildOrigin
                    .getZ()));

        boolean external = !countBuiltChunk;
        long compileStart = RenderAuditBridge.accepts("CHUNK_PERF") ? System.nanoTime() : 0L;
        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
        RenderAuditBridge.counter("CHUNK_UPDATE","COMPILE_BEGIN",ticket==null?-1:ticket.generation,
            "id="+nativeIndex+"; java_ns="+System.nanoTime());
        SectionCompiler.Results renderData =
            ((IChunkBuilderExt) chunkBuilder).radiance$getSectionBuilder()
                .compile(chunkSectionPos, chunkRendererRegion, vertexSorter, storage,
                    additionalRenderers);
        if (compileStart != 0L) {
            RenderAuditBridge.counter("CHUNK_PERF", "COMPILE_NS",
                System.nanoTime() - compileStart, "");
            RenderAuditBridge.counter("CHUNK_PERF", "COMPILE_COUNT", 1, "");
        }
        RenderAuditBridge.counter("CHUNK_BUILD", "COMPILED", 1, "");
        if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
        RenderAuditBridge.counter("CHUNK_UPDATE","COMPILE_END",ticket==null?-1:ticket.generation,
            "id="+nativeIndex+"; java_ns="+System.nanoTime());

        Map<RenderType, MeshData> allBuffers = renderData.renderedLayers;
        Map<RenderType, MeshData> buffers = new java.util.LinkedHashMap<>();
        allBuffers.forEach((layer, mesh) -> {
            if (!com.radiance.client.render.SectionRasterStorage.isRasterLayer(layer)) buffers.put(layer, mesh);
        });
        Object publicationLock = ticket != null ? ticket.state
            : externalTicket != null ? externalTicket.external : builtChunk;
        synchronized (publicationLock) {
        if ((ticket != null && !ticket.isCurrent())
            || (externalTicket != null && !externalTicket.isCurrent())) {
            RenderAuditBridge.counter("CHUNK_BUILD", "STALE_DISCARDED", 1, "");
            closeMeshes(allBuffers);
            return;
        }
        builtChunk.updateGlobalBlockEntities(renderData.globalBlockEntities);

        if (buffers.isEmpty()) {
            RenderAuditBridge.counter("CHUNK_BUILD", "EMPTY", 1, "");
            SectionRenderDispatcher.CompiledSection chunkData = new com.radiance.client.render.RasterCompiledSection(vertexSorter) {
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
                    return layer == null ? allBuffers.isEmpty() : !allBuffers.containsKey(layer);
                }
            };
            publishCompiled(builtChunk, chunkData);
            if (countBuiltChunk) {
                builtChunkNum++;
            }

            invalidateSingle(nativeIndex, ticket == null ? -1L : ticket.generation);
            if (ticket != null) {
                ticket.state.hasNativeGeometry.set(false);
            }
            RenderAuditBridge.counter("CHUNK_BUILD", "PUBLISHED", 1, "");
        } else {
            SectionRenderDispatcher.CompiledSection chunkData = new com.radiance.client.render.RasterCompiledSection(vertexSorter) {
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
                    return layer == null ? allBuffers.isEmpty() : !allBuffers.containsKey(layer);
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
                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.vertexBuffer());
                    assert vertexBuffer.drawState()
                        .indexCount() == vertexBuffer.drawState()
                        .vertexCount() / 4 * 6;

                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, true)
                            .getValue();
                    ResourceLocation textureLocation =
                        ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                            .orElse(MissingTextureAtlasSprite.getLocation());
                    Integer geometryTextureID = textureIds.get(textureLocation);
                    if (geometryTextureID == null) {
                        geometryTextureID = textureIds.get(MissingTextureAtlasSprite.getLocation());
                        RenderAuditBridge.counter("CHUNK_BUILD", "UNKNOWN_TEXTURE", 1,
                            textureLocation.toString());
                    }
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.drawState()
                            .format());

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    RenderType.CompositeState renderState =
                        ((RenderType.CompositeRenderType) renderLayer).state;
                    RenderTypeCompositeStateAccessor renderStateAccessor =
                        (RenderTypeCompositeStateAccessor) (Object) renderState;
                    Integer materialFlags = faceStates.get(renderLayer);
                    if (materialFlags == null) throw new IllegalStateException("Missing draw-state snapshot: " + renderLayer);
                    geometryMaterialFlagsBB.putInt(geometryMaterialFlagsBaseAddr, materialFlags);
                    geometryMaterialFlagsBaseAddr += Integer.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.drawState()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }

                long nativeStart = RenderAuditBridge.accepts("CHUNK_PERF")
                    ? System.nanoTime() : 0L;
                boolean replacingNativeGeometry = ticket != null
                    && ticket.state.hasNativeGeometry.get();
                // Primary sections must use one publication model. Mixing an immediate in-frame
                // first build with a fenced replacement lets consecutive generations of the same
                // slot execute concurrently before the first GPU build is complete. External
                // sections retain their existing immediate path; primary sections publish only
                // after the native batch fence signals.
                boolean nativeImmediate = important && ticket == null;
                long nativeSubmitIntent = RenderAuditBridge.beginIntent("CHUNK_NATIVE_SUBMIT",
                    "ChunkProxy.rebuildSingle",
                    "nativeId=" + nativeIndex
                        + "; generation=" + (ticket == null ? -1L : ticket.generation)
                        + "; origin=" + buildOrigin.getX() + ',' + buildOrigin.getY() + ','
                        + buildOrigin.getZ() + "; geometryCount=" + buffers.size()
                        + "; javaPriority=" + important
                        + "; nativeImmediate=" + nativeImmediate
                        + "; replacing=" + replacingNativeGeometry);
                if (RenderAuditBridge.accepts("CHUNK_UPDATE"))
                RenderAuditBridge.counter("CHUNK_UPDATE","NATIVE_CALL",ticket==null?-1:ticket.generation,
                    "id="+nativeIndex+"; java_ns="+System.nanoTime());
                rebuildSingle(buildOrigin.getX(),
                    buildOrigin.getY(),
                    buildOrigin.getZ(),
                    nativeIndex,
                    ticket == null ? -1L : ticket.generation,
                    buffers.size(),
                    geometryTypeAddr,
                    geometryGroupNameAddr,
                    geometryMaterialFlagsAddr,
                    geometryTextureAddr,
                    vertexFormatAddr,
                    vertexCountAddr,
                    verticesAddr,
                    nativeImmediate,
                    important ? 1 : 0,
                    countBuiltChunk);
                RenderAuditBridge.transition(nativeSubmitIntent, "ACCEPTED", "MCVR_CHUNK_SCENE",
                    "native rebuild call returned", true);
                if (ticket != null) {
                    ticket.state.hasNativeGeometry.set(true);
                }
                if (nativeStart != 0L) {
                    RenderAuditBridge.counter("CHUNK_PERF", "NATIVE_SUBMIT_NS",
                        System.nanoTime() - nativeStart, "");
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
            ExternalChunk owner = externalTicket == null ? externalChunks.get(builtChunk)
                : externalTicket.external;
            if (owner != null && externalChunks.get(builtChunk) == owner
                && owner.nativeId == nativeIndex) {
                com.radiance.client.render.SectionRasterStorage.publish(builtChunk, allBuffers,
                    () -> externalChunks.get(builtChunk) == owner);
            }
        }
        if (!external) {
            Map<RenderType, MeshData> raster = new java.util.LinkedHashMap<>();
            allBuffers.forEach((layer, mesh) -> {
                if (com.radiance.client.render.SectionRasterStorage.isRasterLayer(layer)) raster.put(layer, mesh);
            });
            if (!raster.isEmpty()) {
                ViewArea storageOwner = currentStorage;
                com.radiance.client.render.SectionRasterStorage.publish(builtChunk, raster,
                    () -> currentStorage == storageOwner
                        && (ticket == null || ticket.isCurrent()));
            }
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
     * TextureManager is owned by the render thread. Snapshot the GL names before a build is
     * handed to a worker so high-distance compilation never races texture reload or disposal.
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
            result.computeIfAbsent(texture, location -> textureManager.getTexture(location).getId());
        }
        return Map.copyOf(result);
    }

    private static native void rebuildSingle(int originX,
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
        boolean important,
        int priority,
        boolean collectEmission);

    private static native long allocateExternalChunkNative();

    private static native void updateExternalChunkTransformNative(long index,
        double m00, double m01, double m02, double m03,
        double m10, double m11, double m12, double m13,
        double m20, double m21, double m22, double m23);

    private static native void releaseExternalChunkNative(long index);

    public static native boolean isChunkReady(long index);

    public static native int countReadyChunksNative();

    public static boolean isChunkReady(SectionRenderDispatcher.RenderSection builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    private static native void markChunkDirtyNative(long index, long generation);

    public static native void relocateSingle(long index, int originX, int originY, int originZ,
        long generation);

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
            columnQueue.offer(new ColumnRequest(request.column(),
                horizontalDistanceSquared(sectionX, sectionZ), request.sequence()));
        }

        List<BuildRequest> builds = new ArrayList<>();
        rebuildQueue.drainTo(builds);
        for (BuildRequest request : builds) {
            ChunkBuildState state = chunkBuildStates.get(request.index());
            if (state == null || !state.queued.get()) {
                continue;
            }
            BlockPos origin = state.section.getOrigin();
            rebuildQueue.offer(new BuildRequest(request.index(), sectionDistanceSquared(origin),
                request.sequence(), state.interactive || state.section.isDirtyFromPlayer() || request.interactive(), request.enqueuedAt()));
        }
    }

    private record ColumnRequest(long column, long distanceSquared, long sequence)
        implements Comparable<ColumnRequest> {
        @Override
        public int compareTo(ColumnRequest other) {
            int distance = Long.compare(distanceSquared, other.distanceSquared);
            return distance != 0 ? distance : Long.compare(sequence, other.sequence);
        }
    }

    private static long sectionDistanceSquared(BlockPos origin) {
        long dy = SectionPos.blockToSectionCoord(origin.getY()) - cameraSectionY;
        return horizontalDistanceSquared(SectionPos.blockToSectionCoord(origin.getX()), SectionPos.blockToSectionCoord(origin.getZ())) + dy*dy;
    }

    private record BuildRequest(int index, long distanceSquared, long sequence, boolean interactive, long enqueuedAt)
        implements Comparable<BuildRequest> {
        @Override
        public int compareTo(BuildRequest other) {
            int priority = Boolean.compare(other.interactive, interactive);
            if (priority != 0) return priority;
            int distance = Long.compare(distanceSquared, other.distanceSquared);
            return distance != 0 ? distance : Long.compare(sequence, other.sequence);
        }
    }

    private static final class ChunkBuildState {
        private volatile SectionRenderDispatcher.RenderSection section;
        private final AtomicLong generation = new AtomicLong();
        private final AtomicBoolean queued = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean hasNativeGeometry = new AtomicBoolean();
        private volatile boolean interactive;
        private volatile boolean occupancyEmpty;
        private volatile boolean skipNeighborRefresh;

        private ChunkBuildState(SectionRenderDispatcher.RenderSection section) {
            this.section = section;
        }
    }

    private record ChunkBuildTicket(ChunkBuildState state,
                                    SectionRenderDispatcher.RenderSection section,
                                    BlockPos origin,
                                    long generation,
                                    long epoch,
                                    long nativeIndex,
                                    boolean primary,
                                    Map<ResourceLocation, Integer> textureIds, Map<RenderType, Integer> faceStates) {
        private boolean isCurrent() {
            BlockPos currentOrigin = section.getOrigin();
            return acceptingSectionEvents
                && storageEpoch.get() == epoch
                && state.section == section
                && state.generation.get() == generation
                && currentOrigin.getX() == origin.getX()
                && currentOrigin.getY() == origin.getY()
                && currentOrigin.getZ() == origin.getZ();
        }
    }

    private record ExternalBuildTicket(
        ExternalSectionBuildScheduler.Ticket<ExternalChunk> schedulerTicket,
        ExternalChunk external,
        BlockPos origin) {
        private boolean isCurrent() {
            return isCurrentExternal(schedulerTicket)
                && external.section.getOrigin().equals(origin);
        }
    }

    private static final class ExternalChunk {
        private final long nativeId;
        private final SectionRenderDispatcher.RenderSection section;
        private final ExternalSectionBuildScheduler.State buildState;

        private ExternalChunk(long nativeId, SectionRenderDispatcher.RenderSection section,
            ExternalSectionBuildScheduler.State buildState) {
            this.nativeId = nativeId;
            this.section = section;
            this.buildState = buildState;
        }
    }

}
