package com.radiance.audit;

import com.radiance.client.proxy.world.ChunkProxy;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded start/end evidence; never scans section contents or mutates renderer settings. */
public final class BenchmarkChunkState {
    private BenchmarkChunkState() {}

    public static void capture(Properties out) throws ReflectiveOperationException {
        if (!ExperimentAccess.permitted())
            throw new IllegalStateException("Isolated benchmark required");
        captureJava(ChunkProxy.class, out);
        out.setProperty("chunk.native", ChunkProxy.performanceSnapshotNative());
    }

    static void captureJava(Class<?> source, Properties out) throws ReflectiveOperationException {
        for (String name : new String[] {"columnQueue", "rebuildQueue"}) {
            Collection<?> queue = (Collection<?>) value(source, name);
            out.setProperty("chunk.java." + name, Integer.toString(queue.size()));
        }
        for (String name : new String[] {"importantBuildsInFlight", "normalBuildsInFlight"})
            out.setProperty(
                    "chunk.java." + name,
                    Integer.toString(((AtomicInteger) value(source, name)).get()));
        long slots = 1;
        for (String name : new String[] {"storageSizeX", "storageSizeY", "storageSizeZ"})
            slots = Math.multiplyExact(slots, ((Number) value(source, name)).longValue());
        out.setProperty("chunk.java.slots", Long.toString(slots));
        out.setProperty(
                "chunk.java.initialSeedPending",
                String.valueOf(value(source, "initialSeedPending")));
    }

    private static Object value(Class<?> source, String name) throws ReflectiveOperationException {
        Field f = source.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
