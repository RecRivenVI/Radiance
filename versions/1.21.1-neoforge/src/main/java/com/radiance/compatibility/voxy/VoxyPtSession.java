package com.radiance.compatibility.voxy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.model.IdNotYetComputedException;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One bounded CPU mesher and an immutable, complete-child LOD cut for a leased Voxy world. */
final class VoxyPtSession implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Radiance/Voxy");
    private static final int MAX_NODES = 8192;
    private final long epoch;
    private final Level level;
    private final VoxyClientInstance owner;
    private final IVoxyRenderSystemHolder holder;
    private final VoxyInstance.WorldLease lease;
    private final WorldEngine world;
    private final VoxyModels models;
    private final Object modelLock = new Object();
    private final RenderDataFactory mesher;
    private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Node> work = new ConcurrentLinkedQueue<>();
    private final ArrayBlockingQueue<Result> completed = new ArrayBlockingQueue<>(4);
    private final Set<Integer> missingModels = ConcurrentHashMap.newKeySet();
    private final Set<Node> blockedModels = ConcurrentHashMap.newKeySet();
    private final Thread worker;
    private volatile boolean running = true;
    private volatile Throwable workerFailure;
    private int biomeCount;
    private long frames;
    private final AtomicLong generations = new AtomicLong();
    private Set<Long> previousCut = Set.of();
    private Set<Long> previousRoots = Set.of();
    private boolean budgetReported;
    private boolean nativeBudgetReported;

    private static final class Node {
        final long key;
        final AtomicLong revision;
        final AtomicBoolean queued = new AtomicBoolean();
        volatile boolean ready;
        volatile int children;
        long lastUsed;

        Node(long key, long generation) {
            this.key = key;
            revision = new AtomicLong(generation);
        }
    }

    private record Result(Node node, long revision, BuiltSection mesh) {}

    VoxyPtSession(
            long epoch,
            Level level,
            VoxyClientInstance owner,
            IVoxyRenderSystemHolder holder,
            VoxyInstance.WorldLease lease) {
        this.epoch = epoch;
        this.level = level;
        this.owner = owner;
        this.holder = holder;
        this.lease = lease;
        world = lease.engine();
        ModelFactory factory = null;
        RenderDataFactory created = null;
        VoxyModels createdModels = null;
        boolean nativeStarted = false;
        try {
            factory = new ModelFactory(world.getMapper(), null);
            models = new VoxyModels(epoch, factory);
            createdModels = models;
            created = new RenderDataFactory(world, factory, false, false);
            mesher = created;
            VoxyNative.begin(epoch);
            nativeStarted = true;
            VoxyCaptureLock.LOCK.writeLock().lock();
            try {
                world.setDirtyCallback(
                        (section, flags, neighbours) -> {
                            dirty(section.key);
                            for (int side = 0; side < 6; side++) {
                                if ((neighbours & (1 << side)) == 0) continue;
                                int x = section.x, y = section.y, z = section.z;
                                int delta = (side & 1) == 0 ? -1 : 1;
                                if (side < 2) y += delta;
                                else if (side < 4) x += delta;
                                else z += delta;
                                dirty(WorldEngine.getWorldSectionId(section.lvl, x, y, z));
                            }
                        });
            } finally {
                VoxyCaptureLock.LOCK.writeLock().unlock();
            }
            worker = new Thread(this::run, "Radiance Voxy mesh");
            worker.setDaemon(true);
            worker.start();
            LOGGER.info(
                    "Voxy PT session {} acquired source world lease; CPU mesher=1, GL renderer=0",
                    epoch);
        } catch (RuntimeException | Error e) {
            if (nativeStarted) {
                VoxyCaptureLock.LOCK.writeLock().lock();
                try {
                    world.setDirtyCallback(null);
                } finally {
                    VoxyCaptureLock.LOCK.writeLock().unlock();
                }
                try {
                    VoxyNative.end(epoch);
                } catch (RuntimeException | Error cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            try {
                if (created != null) created.free();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            try {
                if (createdModels != null) createdModels.close();
                else if (factory != null) factory.free();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            try {
                lease.close();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private void queue(Node node) {
        if (running && nodes.get(node.key) == node && node.queued.compareAndSet(false, true))
            work.add(node);
    }

    private void dirty(long key) {
        Node node = nodes.get(key);
        if (node != null) {
            node.revision.accumulateAndGet(generations.incrementAndGet(), Math::max);
            queue(node);
        }
    }

    private Node request(long key) {
        Node present = nodes.get(key);
        if (present != null) {
            present.lastUsed = frames;
            return present;
        }
        if (nodes.size() >= MAX_NODES) {
            if (!budgetReported) {
                LOGGER.warn(
                        "Voxy PT residency budget reached ({} nodes); retaining valid parent proxies",
                        MAX_NODES);
                budgetReported = true;
            }
            return null;
        }
        Node next = new Node(key, generations.incrementAndGet());
        next.lastUsed = frames;
        Node old = nodes.putIfAbsent(key, next);
        if (old != null) return old;
        queue(next);
        return next;
    }

    private static long stableRevision() {
        VoxyCaptureLock.LOCK.readLock().lock();
        try {
            return VoxyCaptureLock.revision();
        } finally {
            VoxyCaptureLock.LOCK.readLock().unlock();
        }
    }

    private void run() {
        try {
            while (running) {
                Node node = work.poll();
                if (node == null) {
                    Thread.sleep(10);
                    continue;
                }
                if (nodes.get(node.key) != node) {
                    node.queued.set(false);
                    continue;
                }
                long revision = node.revision.get();
                BuiltSection mesh = null;
                boolean retry = false;
                try {
                    long before = stableRevision();
                    var section = world.acquireIfExists(node.key);
                    if (section == null)
                        continue; // Unknown storage data is never published as empty.
                    try {
                        synchronized (modelLock) {
                            mesh = mesher.generateMesh(section);
                        }
                    } finally {
                        section.release();
                    }
                    if (before != stableRevision() || revision != node.revision.get()) {
                        mesh.free();
                        mesh = null;
                        retry = true;
                        continue;
                    }
                    Result result = new Result(node, revision, mesh);
                    while (running) {
                        if (completed.offer(result, 100, TimeUnit.MILLISECONDS)) {
                            mesh = null;
                            break;
                        }
                    }
                } catch (IdNotYetComputedException missing) {
                    blockedModels.add(node);
                    if (missing.isIdBlockId) missingModels.add(missing.id);
                    else if (missingModels.isEmpty())
                        throw new IllegalStateException(
                                "Voxy fluid model dependency was not scheduled", missing);
                } finally {
                    if (mesh != null) mesh.free();
                    node.queued.set(false);
                    if (running && (retry || revision != node.revision.get())) queue(node);
                }
            }
        } catch (InterruptedException e) {
            if (running) workerFailure = e;
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            workerFailure = e;
        }
    }

    void frame() {
        if (workerFailure != null)
            throw new IllegalStateException("Voxy mesh worker failed", workerFailure);
        synchronized (modelLock) {
            var biomes = world.getMapper().getBiomeEntries();
            boolean newBiomes = biomeCount != biomes.length;
            for (; biomeCount < biomes.length; biomeCount++)
                models.factory.addBiome(biomes[biomeCount]);
            var baked = new ArrayList<Integer>();
            for (int id : missingModels) {
                models.factory.addEntry(id);
                baked.add(id);
                if (baked.size() == 2) break;
            }
            if (!baked.isEmpty() || newBiomes || frames == 0) {
                models.factory.processAllThings();
                models.drain();
                for (int id : baked) {
                    if (models.factory.hasModelForBlockId(id)) {
                        missingModels.remove(id);
                    }
                }
                models.publish();
                // Retry only on model progress, not every render frame.
                for (Node node : blockedModels.toArray(Node[]::new)) {
                    if (blockedModels.remove(node)) queue(node);
                }
            }
        }
        for (int n = 0; n < 4; n++) {
            Result result = completed.poll();
            if (result == null) break;
            try {
                var mesh = result.mesh();
                if (nodes.get(mesh.position) != result.node()) continue;
                if (result.revision() != result.node().revision.get()) {
                    queue(result.node());
                    continue;
                }
                validate(mesh);
                if (!VoxyNative.section(
                        epoch,
                        mesh.position,
                        result.revision(),
                        mesh.isEmpty() ? 0 : mesh.geometryBuffer.address,
                        mesh.isEmpty() ? 0 : Math.toIntExact(mesh.geometryBuffer.size / 8))) {
                    if (!nativeBudgetReported) {
                        LOGGER.warn(
                                "Voxy PT native residency budget reached; deferring new versions and retaining valid proxies");
                        nativeBudgetReported = true;
                    }
                    queue(result.node());
                    continue;
                }
                result.node().ready = true;
                result.node().children = Byte.toUnsignedInt(mesh.childExistence);
            } finally {
                result.mesh().free();
            }
        }
        Set<Long> cut = new HashSet<>();
        Set<Long> roots = new HashSet<>();
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = VoxyConfig.CONFIG.sectionRenderDistance * 512.0;
        if (!Double.isFinite(distance) || distance <= 0 || distance > 32768)
            throw new IllegalArgumentException(
                    "Voxy PT distance outside supported source range (0, 32768]");
        int minX = Math.floorDiv((int) Math.floor(camera.x - distance), 512);
        int maxX = Math.floorDiv((int) Math.floor(camera.x + distance), 512);
        int minZ = Math.floorDiv((int) Math.floor(camera.z - distance), 512);
        int maxZ = Math.floorDiv((int) Math.floor(camera.z + distance), 512);
        int minY = Math.floorDiv(level.getMinBuildHeight(), 512);
        int maxY = Math.floorDiv(level.getMaxBuildHeight() - 1, 512);
        double pixels =
                minecraft.getWindow().getHeight()
                        / (2 * Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5));
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                double dx = Math.max(0, Math.abs(x * 512.0 + 256 - camera.x) - 256);
                double dz = Math.max(0, Math.abs(z * 512.0 + 256 - camera.z) - 256);
                if (dx * dx + dz * dz > distance * distance) continue;
                for (int y = minY; y <= maxY; y++) {
                    roots.add(WorldEngine.getWorldSectionId(4, x, y, z));
                    choose(4, x, y, z, camera.x, camera.y, camera.z, pixels, cut);
                }
            }
        if (!cut.equals(previousCut) || !roots.equals(previousRoots)) {
            VoxyNative.select(
                    epoch,
                    cut.stream().mapToLong(Long::longValue).toArray(),
                    roots.stream().mapToLong(Long::longValue).toArray());
            previousCut = Set.copyOf(cut);
            previousRoots = Set.copyOf(roots);
        }
        if (frames % 60 == 0
                && nodes.values()
                        .removeIf(
                                node -> frames - node.lastUsed > 300 && !cut.contains(node.key))) {
            VoxyNative.retain(epoch, nodes.keySet().stream().mapToLong(Long::longValue).toArray());
            budgetReported = false;
            nativeBudgetReported = false;
        }
        if ((frames++ % 300) == 0) LOGGER.info("Voxy PT session {} counters {}", epoch, counters());
    }

    private void choose(
            int lod,
            int x,
            int y,
            int z,
            double cx,
            double cy,
            double cz,
            double pixels,
            Set<Long> cut) {
        Node node = request(WorldEngine.getWorldSectionId(lod, x, y, z));
        if (node == null || !node.ready) return;
        int width = 32 << lod;
        double dx = Math.max(0, Math.abs(x * (double) width + width * 0.5 - cx) - width * 0.5);
        double dy = Math.max(0, Math.abs(y * (double) width + width * 0.5 - cy) - width * 0.5);
        double dz = Math.max(0, Math.abs(z * (double) width + width * 0.5 - cz) - width * 0.5);
        boolean refine =
                lod != 0
                        && node.children != 0
                        && width * pixels / Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz))
                                > VoxyConfig.CONFIG.subDivisionSize;
        if (refine) {
            boolean ready = true;
            for (int i = 0; i < 8; i++) {
                if ((node.children & (1 << i)) == 0) continue;
                Node child =
                        request(
                                WorldEngine.getWorldSectionId(
                                        lod - 1,
                                        x * 2 + (i & 1),
                                        y * 2 + ((i >> 2) & 1),
                                        z * 2 + ((i >> 1) & 1)));
                ready &= child != null && child.ready;
            }
            if (ready) {
                for (int i = 0; i < 8; i++)
                    if ((node.children & (1 << i)) != 0)
                        choose(
                                lod - 1,
                                x * 2 + (i & 1),
                                y * 2 + ((i >> 2) & 1),
                                z * 2 + ((i >> 1) & 1),
                                cx,
                                cy,
                                cz,
                                pixels,
                                cut);
                return;
            }
        }
        cut.add(node.key);
    }

    private static void validate(BuiltSection mesh) {
        if (mesh.isEmpty()) return;
        if ((mesh.geometryBuffer.size & 7) != 0
                || mesh.geometryBuffer.size > 4L * 1024 * 1024
                || mesh.offsets == null
                || mesh.offsets.length != 8
                || mesh.offsets[0] != 0)
            throw new IllegalArgumentException("Voxy BuiltSection ABI/budget mismatch");
        long count = mesh.geometryBuffer.size / 8;
        int previous = 0;
        for (int offset : mesh.offsets) {
            if (offset < previous || offset > count)
                throw new IllegalArgumentException("Voxy quad group range");
            previous = offset;
        }
    }

    long[] counters() {
        return VoxyNative.counters(epoch);
    }

    long epoch() {
        return epoch;
    }

    @Override
    public void close() {
        running = false;
        VoxyCaptureLock.LOCK.writeLock().lock();
        try {
            world.setDirtyCallback(null);
        } finally {
            VoxyCaptureLock.LOCK.writeLock().unlock();
        }
        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join(100);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        Result result;
        while ((result = completed.poll()) != null) result.mesh().free();
        try {
            VoxyNative.end(epoch);
        } finally {
            try {
                mesher.free();
                models.close();
            } finally {
                lease.close();
                owner.unregisterRenderer(holder);
            }
        }
        LOGGER.info(
                "Voxy PT session {} released; worker stopped and source world lease returned",
                epoch);
    }
}
