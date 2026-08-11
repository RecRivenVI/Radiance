package com.radiance.api.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common registration and source lifetime boundary. It stores participants, not a second copy of
 * the native scene. Optional integrations are installed once, without linking absent mod classes.
 */
public final class RenderPlatform {
    public static final int API_VERSION =
            com.radiance.render.contract.RenderAbi.PLATFORM_API_VERSION;
    private static volatile List<SourceIntegration> sources = List.of();
    private static long resourceEpoch = 1;
    private static final java.util.Map<String, String> AVAILABILITY =
            new java.util.LinkedHashMap<>();

    public static synchronized void sourceAvailability(String id, String reason) {
        AVAILABILITY.put(id, reason);
    }

    public static synchronized java.util.Map<String, String> availability() {
        return java.util.Map.copyOf(AVAILABILITY);
    }

    private RenderPlatform() {}

    public static void initializeNative(int version) {
        if (version != API_VERSION)
            throw new IllegalStateException(
                    "Radiance Java/native render API mismatch: Java="
                            + API_VERSION
                            + ", native="
                            + version);
    }

    public static synchronized void register(int version, SourceIntegration source) {
        if (version != API_VERSION)
            throw new IllegalArgumentException("Radiance render API version mismatch: " + version);
        Objects.requireNonNull(source);
        if (source.id().isBlank() || sources.stream().anyMatch(s -> s.id().equals(source.id())))
            throw new IllegalArgumentException(
                    "Duplicate or empty rendering source: " + source.id());
        var next = new ArrayList<>(sources);
        next.add(source);
        sources = List.copyOf(next);
        AVAILABILITY.put(source.id(), "已登记；实际绘制见下方运行记录");
    }

    public static List<SourceIntegration> sources() {
        return sources;
    }

    public static synchronized long resourceEpoch() {
        return resourceEpoch;
    }

    public static synchronized void resourcesInvalidated() {
        ++resourceEpoch;
        notifyOwners(SourceIntegration::resourcesInvalidated);
        BlockEntityPublications.clear();
    }

    public static void worldClosed() {
        notifyOwners(SourceIntegration::worldClosed);
        BlockEntityPublications.clear();
    }

    public static List<String> integrations() {
        return sources.stream().map(SourceIntegration::id).toList();
    }

    private static void notifyOwners(java.util.function.Consumer<SourceIntegration> notify) {
        Throwable first = null;
        for (var source : sources)
            try {
                notify.accept(source);
            } catch (RuntimeException | Error failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        if (first instanceof RuntimeException failure) throw failure;
        if (first instanceof Error failure) throw failure;
    }
}
