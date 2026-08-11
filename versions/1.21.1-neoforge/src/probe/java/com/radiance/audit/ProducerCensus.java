package com.radiance.audit;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.proxy.world.EntityProxy.EntityRenderDataList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Optional, bounded render-thread census. No per-vertex observer and no changes to renderer inputs.
 */
public final class ProducerCensus {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.producerCensus");
    private static final Map<String, Row> rows = new TreeMap<>();
    private static final IdentityHashMap<Object, Row> providers = new IdentityHashMap<>();
    private static final IdentityHashMap<MeshData, Row> meshes = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Row> rigidOwners = new IdentityHashMap<>();

    private record GeometryCounts(int vertices, boolean direct) {}

    private static final IdentityHashMap<Object, GeometryCounts> geometryCounts =
            new IdentityHashMap<>();
    private static boolean active;
    private static boolean countsOnly;
    private static Thread owner;
    private static Path directory;
    private static Row rendering, face;
    private static Row explicitFaceOwner;
    private static Deque<Row> submission;
    private static long frames;
    private static Timer top;
    private static final int LIMIT = 4096;

    private ProducerCensus() {}

    private static boolean enabled() {
        return active && Thread.currentThread() == owner;
    }

    public static boolean collecting() {
        return enabled();
    }

    public static void start(Path output) {
        start(output, false);
    }

    static void start(Path output, boolean requested) {
        start(output, requested, false);
    }

    static void start(Path output, boolean requested, boolean onlyCounts) {
        if (!ENABLED && !requested) return;
        countsOnly = onlyCounts;
        rows.clear();
        providers.clear();
        meshes.clear();
        rigidOwners.clear();
        geometryCounts.clear();
        frames = 0;
        rendering = null;
        face = null;
        submission = null;
        directory = output;
        owner = Thread.currentThread();
        active = true;
        top = null;
        explicitFaceOwner = null;
    }

    public static void frame() {
        if (enabled()) {
            frames++;
            providers.clear();
            meshes.clear();
            rigidOwners.clear();
        }
    }

    private static Row row(String name) {
        Row found = rows.get(name);
        if (found != null) return found;
        if (rows.size() >= LIMIT) name = "unattributed/capacity";
        return rows.computeIfAbsent(name, Row::new);
    }

    public static Scope renderer(String kind, Object renderer) {
        if (!enabled()) return null;
        Row r = row(kind + "/" + (renderer == null ? "unknown" : renderer.getClass().getName()));
        Row previous = rendering;
        rendering = r;
        r.calls++;
        Timer timer = new Timer();
        return () -> {
            timer.close();
            r.renderNs += timer.elapsed;
            r.renderSelfNs += timer.self;
            rendering = previous;
        };
    }

    public static Scope model(Object part, Object consumer) {
        if (!enabled()) return null;
        Row r = rendering == null ? row("unattributed/model") : rendering;
        r.partCalls++;
        if (r.parts.size() < LIMIT) r.parts.add(part);
        r.consumers.add(consumer.getClass().getName());
        Timer timer = new Timer();
        return () -> {
            timer.close();
            r.partNs += timer.elapsed;
        };
    }

    public static Scope baked(String entry, Object model, Object consumer) {
        if (!enabled()) return null;
        Row r =
                row(
                        (rendering == null ? "unattributed" : rendering.name)
                                + "::"
                                + entry
                                + "/"
                                + model.getClass().getName());
        r.calls++;
        if (r.parts.size() < LIMIT) r.parts.add(model);
        r.consumers.add(consumer.getClass().getName());
        var pbr = consumer instanceof com.radiance.client.vertex.PBRVertexConsumer p ? p : null;
        int before = pbr == null ? 0 : pbr.getVertexCount();
        Timer timer = new Timer();
        return () -> {
            timer.close();
            r.renderNs += timer.elapsed;
            r.renderSelfNs += timer.self;
            if (pbr != null) {
                long count = pbr.getVertexCount() - before;
                r.vertices += count;
                r.bytes += count * pbr.getFormat().getVertexSize();
            }
        };
    }

    public static void provider(Object provider) {
        if (!enabled()) return;
        Row r = rendering == null ? row("unattributed/dynamic") : rendering;
        Row old = providers.putIfAbsent(provider, r);
        if (old != null && old != r) providers.put(provider, row("mixed/provider"));
    }

    public static void captureAllocation(int bytes) {
        if (!enabled()) return;
        Row r = rendering == null ? row("unattributed/capture-allocation") : rendering;
        r.captureAllocations++;
        r.captureRequestedBytes += bytes;
    }

    public static void produced(Object provider, EntityRenderDataList data, int first) {
        if (!enabled()) return;
        Row r = providers.getOrDefault(provider, row("unattributed/dynamic"));
        for (int i = first; i < data.size(); i++)
            for (var layer : data.get(i)) {
                MeshData mesh = layer.builtBuffer();
                if (meshes.putIfAbsent(mesh, r) == null) {
                    r.capturePolicies.add(capturePolicy(provider));
                    r.geometries++;
                    r.vertices += mesh.drawState().vertexCount();
                    r.bytes +=
                            (long) mesh.drawState().vertexCount()
                                    * mesh.drawState().format().getVertexSize();
                }
            }
    }

    private static String capturePolicy(Object provider) {
        var parts = new ArrayList<String>();
        for (String key : List.of("surfaceRole", "rigidEnabled", "partCaptureEnabled")) {
            try {
                parts.add(key + "=" + RigidModelProbe.field(provider, key));
            } catch (ReflectiveOperationException absent) {
                parts.add(key + "=unavailable-in-frozen-product");
            }
        }
        parts.add("partSource=" + System.getProperty("radiance.internal.partSource", "absent"));
        parts.add("bakedSource=" + System.getProperty("radiance.internal.bakedSource", "absent"));
        return String.join("/", parts);
    }

    public static Scope submit(EntityRenderDataList data) {
        if (!enabled()) return null;
        var previous = submission;
        submission = new ArrayDeque<>();
        for (var entity : data) {
            Set<Row> owners = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var layer : entity) {
                Row r = meshes.getOrDefault(layer.builtBuffer(), row("unattributed/submission"));
                submission.add(r);
                r.submitted++;
                owners.add(r);
            }
            for (Row r : owners) {
                r.entityInputs++;
                if (!entity.isPost() && entity.getPrebuiltBLAS() < 0) r.blasCandidates++;
            }
        }
        return () -> submission = previous;
    }

    public static void rigidProduced(Object provider, List<?> draws) {
        if (!enabled()) return;
        Row r = providers.getOrDefault(provider, row("unattributed/rigid-material"));
        for (Object draw : draws) {
            rigidOwners.put(draw, r);
            try {
                Object entry = RigidModelProbe.field(draw, "entry");
                GeometryCounts counts = geometryCounts.get(entry);
                if (counts == null) {
                    MeshData mesh;
                    try {
                        mesh = (MeshData) RigidModelProbe.field(entry, "mesh");
                    } catch (NoSuchFieldException boundResource) {
                        var accessor = entry.getClass().getDeclaredMethod("mesh");
                        accessor.setAccessible(true);
                        mesh = (MeshData) accessor.invoke(entry);
                    }
                    counts =
                            new GeometryCounts(mesh.drawState().vertexCount(), directSource(entry));
                    if (geometryCounts.size() < LIMIT) geometryCounts.put(entry, counts);
                }
                r.persistentDraws++;
                r.persistentVertices += counts.vertices;
                if (counts.direct) {
                    r.directSourceDraws++;
                    r.directSourceVertices += counts.vertices;
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Rigid census contract", failure);
            }
        }
    }

    private static boolean directSource(Object entry) throws ReflectiveOperationException {
        String name = entry.getClass().getName();
        if (name.equals("com.radiance.client.vertex.PartModelCapture$Entry")
                || name.equals("com.radiance.client.vertex.PaintingModelCapture$Entry")
                || name.equals("com.radiance.client.vertex.BoundPartCapture$Resource")) return true;
        try {
            var type = Class.forName("com.radiance.client.vertex.BakedModelTakeover");
            var versions = type.getDeclaredField("VERSIONS");
            versions.setAccessible(true);
            for (Object version : (List<?>) versions.get(null))
                if (RigidModelProbe.field(version, "geometry") == entry) return true;
        } catch (ClassNotFoundException absentInFrozenBaseline) {
            // The baseline can reuse a legacy recipe cache. That is not source-intent takeover.
        }
        return false;
    }

    public static Scope providerFaces(Object provider) {
        if (!enabled()) return null;
        Row previous = explicitFaceOwner;
        explicitFaceOwner = providers.getOrDefault(provider, row("unattributed/provider-material"));
        return () -> explicitFaceOwner = previous;
    }

    public static Scope rigidFaces(Object draw) {
        if (!enabled()) return null;
        Row previous = explicitFaceOwner;
        explicitFaceOwner = rigidOwners.getOrDefault(draw, row("unattributed/rigid-material"));
        return () -> explicitFaceOwner = previous;
    }

    static <T> T consumeFace(T explicit, Deque<T> queue, java.util.function.Supplier<T> missing) {
        return explicit != null
                ? explicit
                : queue == null || queue.isEmpty() ? missing.get() : queue.removeFirst();
    }

    public static Scope face(Object layer) {
        if (!enabled()) return null;
        Row previous = face;
        face = consumeFace(explicitFaceOwner, submission, () -> row("unattributed/material"));
        Row r = face;
        r.faceCalls++;
        if (r.layers.size() < LIMIT) r.layers.add(layer);
        long start = countsOnly ? 0 : System.nanoTime();
        return () -> {
            if (!countsOnly) r.faceNs += System.nanoTime() - start;
            face = previous;
        };
    }

    public static Scope phase(int index) {
        if (!enabled() || face == null || countsOnly) return null;
        Row r = face;
        long start = System.nanoTime();
        return () -> r.phaseNs[index] += System.nanoTime() - start;
    }

    public static void finish() {
        if (!enabled()) return;
        active = false;
        StringBuilder csv =
                new StringBuilder(
                        "producer,frames,renderer_calls,renderer_inclusive_ns,renderer_self_ns,model_calls,model_unique,model_ns,geometries,vertices,generated_bytes,submitted_geometries,entity_inputs,blas_candidates,face_calls,unique_layers,face_ns,setup_ns,clear_ns,backup_ns,restore_ns,consumer_classes,persistent_draws,persistent_vertices,direct_source_draws,direct_source_vertices,capture_policies,capture_allocations,capture_requested_bytes\n");
        for (Row r : rows.values())
            csv.append(r.name)
                    .append(',')
                    .append(frames)
                    .append(',')
                    .append(r.calls)
                    .append(',')
                    .append(r.renderNs)
                    .append(',')
                    .append(r.renderSelfNs)
                    .append(',')
                    .append(r.partCalls)
                    .append(',')
                    .append(r.parts.size())
                    .append(',')
                    .append(r.partNs)
                    .append(',')
                    .append(r.geometries)
                    .append(',')
                    .append(r.vertices)
                    .append(',')
                    .append(r.bytes)
                    .append(',')
                    .append(r.submitted)
                    .append(',')
                    .append(r.entityInputs)
                    .append(',')
                    .append(r.blasCandidates)
                    .append(',')
                    .append(r.faceCalls)
                    .append(',')
                    .append(r.layers.size())
                    .append(',')
                    .append(r.faceNs)
                    .append(',')
                    .append(r.phaseNs[0])
                    .append(',')
                    .append(r.phaseNs[1])
                    .append(',')
                    .append(r.phaseNs[2])
                    .append(',')
                    .append(r.phaseNs[3])
                    .append(',')
                    .append(String.join(";", r.consumers))
                    .append(',')
                    .append(r.persistentDraws)
                    .append(',')
                    .append(r.persistentVertices)
                    .append(',')
                    .append(r.directSourceDraws)
                    .append(',')
                    .append(r.directSourceVertices)
                    .append(',')
                    .append(String.join(";", r.capturePolicies))
                    .append(',')
                    .append(r.captureAllocations)
                    .append(',')
                    .append(r.captureRequestedBytes)
                    .append('\n');
        rows.clear();
        providers.clear();
        meshes.clear();
        rigidOwners.clear();
        geometryCounts.clear();
        rendering = null;
        face = null;
        submission = null;
        explicitFaceOwner = null;
        Path output = directory;
        String text = csv.toString();
        countsOnly = false;
        Thread writer =
                new Thread(
                        () -> {
                            try {
                                Files.createDirectories(output);
                                Files.writeString(output.resolve("producers.csv"), text);
                            } catch (java.io.IOException error) {
                                com.mojang.logging.LogUtils.getLogger()
                                        .error("[Radiance Audit/census] Write failed", error);
                            }
                        },
                        "Radiance Audit census writer");
        writer.setDaemon(true);
        writer.start();
    }

    static java.util.function.LongSupplier clock = System::nanoTime;

    static final class Timer implements AutoCloseable {
        final Timer parent = top;
        final long start = countsOnly ? 0 : clock.getAsLong();
        long children, elapsed, self;

        Timer() {
            top = this;
        }

        public void close() {
            elapsed = countsOnly ? 0 : clock.getAsLong() - start;
            self = elapsed - children;
            top = parent;
            if (parent != null) parent.children += elapsed;
        }
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Row {
        long captureAllocations, captureRequestedBytes;
        final String name;
        long calls,
                renderNs,
                renderSelfNs,
                partCalls,
                partNs,
                geometries,
                vertices,
                bytes,
                submitted,
                entityInputs,
                blasCandidates,
                faceCalls,
                faceNs;
        long persistentDraws, persistentVertices, directSourceDraws, directSourceVertices;
        final long[] phaseNs = new long[4];
        final Set<Object> parts = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> layers = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<String> consumers = new TreeSet<>();
        final Set<String> capturePolicies = new TreeSet<>();

        Row(String name) {
            this.name = name;
        }
    }
}
