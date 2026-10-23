package com.radiance.audit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Optional source observer, separate from the independent request experiment. */
public final class TakeoverSourceProbe {
    private static final boolean ENABLED =
            "1".equals(ExperimentAccess.getenv("RADIANCE_TAKEOVER_SOURCES"));
    private static final Set<Object> engines = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long calls;
    private static int maximumModels, maximumInstances;

    private TakeoverSourceProbe() {}

    public static void observeFlywheel(Object engine) {
        if (!ENABLED || (++calls != 1 && calls % 64 != 0)) return;
        try {
            synchronized (engine) {
                engines.add(engine);
                var models = (Map<?, ?>) RigidModelProbe.field(engine, "models");
                var instancers = (Map<?, ?>) RigidModelProbe.field(engine, "instancers");
                int instances = 0;
                for (var instancer : instancers.values())
                    instances +=
                            ((java.util.Collection<?>)
                                            RigidModelProbe.field(instancer, "instances"))
                                    .size();
                maximumModels = Math.max(maximumModels, models.size());
                maximumInstances = Math.max(maximumInstances, instances);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Flywheel source observation contract", failure);
        }
    }

    public static void finish() {
        if (!ENABLED) return;
        if (calls == 0 || maximumModels == 0 || maximumInstances == 0)
            throw new IllegalStateException(
                    "Actual Flywheel model/instance source was not exercised");
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "TAKEOVER_SOURCES flywheel-render-calls={} engines={} models={} instances={} result=PASS scope=existing-source-adapter-not-universal-parity",
                        calls,
                        engines.size(),
                        maximumModels,
                        maximumInstances);
        try {
            var output = java.nio.file.Path.of(System.getProperty("validation.result"));
            java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.writeString(
                    output.resolve("takeover-sources.json"),
                    new com.google.gson.Gson()
                                    .toJson(
                                            Map.of(
                                                    "flywheelRenderCalls",
                                                    calls,
                                                    "engines",
                                                    engines.size(),
                                                    "models",
                                                    maximumModels,
                                                    "instances",
                                                    maximumInstances,
                                                    "performanceMeasured",
                                                    false,
                                                    "scope",
                                                    "existing actual source adapter; not complete semantic or new-protocol proof"))
                            + "\n");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
