package com.radiance.audit;

import com.radiance.adapter.vanilla.VanillaModelPose;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.PartModelCapture;
import com.radiance.client.vertex.RigidModelCapture;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/** Bounded source accounting at Radiance-owned boundaries, never at vanilla producer methods. */
public final class InstanceInputCensus {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.instanceCensus");
    private static Path output;
    private static long frame = -1;
    private static Object current;
    private static final IdentityHashMap<Object, Source> owners = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Source> produced = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Description> descriptions =
            new IdentityHashMap<>();
    private static final Map<Long, List<Object>> frames = new LinkedHashMap<>();
    private static final Map<Long, Integer> frameCounts = new LinkedHashMap<>();
    private static final Map<String, Long> reasons = new TreeMap<>();
    private static java.lang.reflect.Method compatiblePose;
    private static long[] initialPartCounters;
    private static boolean cloud;
    private static final Map<Long, Integer> cloudSubmissions = new LinkedHashMap<>();

    private static final class Description {
        final String renderer;
        final IdentityHashMap<ModelPart, String> parts = new IdentityHashMap<>();
        final Set<String> models = new TreeSet<>();

        Description(Object renderer) {
            this.renderer = renderer.getClass().getName();
            describe(renderer, "renderer", new IdentityHashMap<>());
        }

        void describe(Object value, String path, IdentityHashMap<Object, Boolean> seen) {
            if (value == null || seen.put(value, true) != null) return;
            if (value instanceof ModelPart part) {
                parts.put(part, path);
                models.add("ModelPart:" + path);
                if ((Object) part instanceof PartModelCapture.SourceAccess source)
                    source.radiance$children()
                            .forEach((name, child) -> describe(child, path + "." + name, seen));
                return;
            }
            if (value instanceof Model) models.add(value.getClass().getName());
            if (value instanceof Map<?, ?> map) {
                map.forEach(
                        (key, entry) -> {
                            if (entry instanceof Model || entry instanceof ModelPart)
                                describe(entry, path + "[" + key + "]", seen);
                        });
                return;
            }
            for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || !(Model.class.isAssignableFrom(field.getType())
                                    || ModelPart.class.isAssignableFrom(field.getType())
                                    || Map.class.isAssignableFrom(field.getType()))) continue;
                    try {
                        field.setAccessible(true);
                        describe(field.get(value), path + "." + field.getName(), seen);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(
                                "Instance census model descriptor", failure);
                    }
                }
        }
    }

    private static final class Source {
        final Description description;
        final Map<String, Integer> replay = new TreeMap<>();
        int rejectedVersions;

        Source(Description value) {
            description = value;
        }
    }

    private InstanceInputCensus() {}

    static void start(Path directory) {
        if (!ENABLED) return;
        output = directory;
        frame = -1;
        frames.clear();
        frameCounts.clear();
        reasons.clear();
        descriptions.clear();
        initialPartCounters = PartModelCapture.counters();
        cloudSubmissions.clear();
    }

    private static boolean collecting() {
        if (output == null || !FrameProfiler.collecting()) return false;
        long now = FrameProfiler.frameToken();
        if (frame != now) {
            frame = now;
            owners.clear();
            produced.clear();
            current = null;
            if (frames.size() < 3) frames.put(now, new ArrayList<>());
            frameCounts.put(now, 0);
        }
        return true;
    }

    public static void owner(Object provider, Object renderer) {
        if (!collecting() || renderer == null) return;
        current = provider;
        owners.put(provider, new Source(descriptions.computeIfAbsent(renderer, Description::new)));
    }

    public static void selected(Object provider) {
        if (collecting()) current = provider;
    }

    public static void cloud(boolean active) {
        if (ENABLED) cloud = active;
    }

    public static void rejectedVersion() {
        if (!collecting()) return;
        Source source = owners.get(current);
        if (source != null) source.rejectedVersions++;
    }

    public static void replay(Object input) {
        if (!collecting()) return;
        Source source = owners.get(current);
        if (source == null) return;
        try {
            var part = (VanillaModelPose.Part) RigidModelProbe.field(input, "part");
            if (compatiblePose == null) {
                compatiblePose =
                        RigidModelCapture.class.getDeclaredMethod(
                                "compatiblePose", com.mojang.blaze3d.vertex.PoseStack.Pose.class);
                compatiblePose.setAccessible(true);
            }
            String reason =
                    !((boolean) compatiblePose.invoke(null, part.pose()))
                            ? "non-uniform-pose"
                            : source.rejectedVersions > 0
                                    ? "local-version-rejected"
                                    : "binding-or-consumer-eligibility";
            String model = source.description.parts.getOrDefault(part.part(), "unmapped-part");
            source.replay.merge(model + "/" + reason, 1, Integer::sum);
            reasons.merge(reason, 1L, Long::sum);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Instance census replay input", failure);
        }
    }

    public static void produced(Object provider, EntityProxy.EntityRenderDataList data, int first) {
        if (!collecting()) return;
        Source source = owners.get(provider);
        for (int i = first; i < data.size(); ++i) produced.put(data.get(i), source);
    }

    public static void submitted(EntityProxy.EntityRenderDataList data) {
        if (!collecting()) return;
        for (var entity : data) {
            if (entity.isPost() || entity.getPrebuiltBLAS() >= 0) continue;
            if (cloud) {
                cloudSubmissions.merge(frame, 1, Integer::sum);
                continue; // Redirected by endCachedCloud, not a dynamic entity BLAS.
            }
            frameCounts.merge(frame, 1, Integer::sum);
            if (!frames.containsKey(frame)) continue;
            Source source = produced.get(entity);
            var layers = new ArrayList<Object>();
            for (var layer : entity) {
                var draw = layer.builtBuffer().drawState();
                RenderType type = layer.renderLayer();
                String material =
                        type instanceof RenderType.CompositeRenderType composite
                                ? composite
                                        .state
                                        .textureState
                                        .cutoutTexture()
                                        .map(Object::toString)
                                        .orElse("none")
                                : type.name;
                layers.add(
                        Map.of(
                                "layer",
                                type.name,
                                "material",
                                material,
                                "vertices",
                                draw.vertexCount(),
                                "bytes",
                                (long) draw.vertexCount() * draw.format().getVertexSize(),
                                "mode",
                                draw.mode().name(),
                                "format",
                                draw.format().toString()));
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("owner", entity.getHashCode());
            row.put("queryMask", entity.getRayTracingFlag());
            row.put("position", List.of(entity.getX(), entity.getY(), entity.getZ()));
            row.put(
                    "renderer",
                    source == null
                            ? (entity.getRayTracingFlag() == 8
                                    ? "first-person ItemInHandRenderer capture"
                                    : "unattributed")
                            : source.description.renderer);
            row.put("models", source == null ? List.of() : source.description.models);
            row.put("replay", source == null ? Map.of() : source.replay);
            row.put("versionRejections", source == null ? 0 : source.rejectedVersions);
            row.put("layers", layers);
            frames.get(frame).add(row);
        }
    }

    static void finish() {
        if (output == null) return;
        try {
            Files.writeString(
                    output.resolve("instance-inputs.json"),
                    new com.google.gson.Gson()
                                    .toJson(
                                            Map.of(
                                                    "frames",
                                                    frames,
                                                    "frameCounts",
                                                    frameCounts,
                                                    "reasons",
                                                    reasons,
                                                    "partCounters",
                                                    PartModelCapture.counters(),
                                                    "initialPartCounters",
                                                    initialPartCounters,
                                                    "redirectedCloudSubmissions",
                                                    cloudSubmissions,
                                                    "scope",
                                                    "Radiance-owned source accounting; first three frames in full, not performance data"))
                            + "\n");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
        output = null;
        frames.clear();
        owners.clear();
        produced.clear();
        descriptions.clear();
    }
}
