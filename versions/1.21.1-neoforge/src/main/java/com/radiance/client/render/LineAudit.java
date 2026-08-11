package com.radiance.client.render;

import com.radiance.render.contract.LineContract;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLongArray;

/** Fixed-size opt-in comparison/invariant counters. No per-vertex logs or allocation. */
public final class LineAudit {
    public static final boolean ENABLED = Boolean.getBoolean("radiance.lines.audit");
    private static final int COLUMNS = 7;
    private static final int[] COMPONENTS = {0, 4, 8, 20, 24, 28, 32, 36, 40, 44, 48, 52};
    private static final AtomicLongArray COUNTERS = new AtomicLongArray(32 * COLUMNS);

    private LineAudit() {}

    public static void style(
            LineContract.SourceKey source,
            LineContract.LineStyle style,
            LineContract.Basis3 basis,
            float previousBatchWidth) {
        if (!ENABLED) return;
        int base = source.id() * COLUMNS;
        if (base >= COUNTERS.length()) return;
        COUNTERS.incrementAndGet(base);
        if (Float.isFinite(previousBatchWidth)
                && Math.abs(style.worldThickness() - previousBatchWidth) > 1e-6f)
            COUNTERS.incrementAndGet(
                    base + 1); // Classified defect: batch width replaced source width.
        if (!style.valid() || !basis.finite() || Math.abs(basis.determinant()) < 1e-12)
            COUNTERS.incrementAndGet(base + 2);
    }

    public static void vertices(LineContract.SourceKey source, ByteBuffer input, int count) {
        if (!ENABLED) return;
        int base = source.id() * COLUMNS;
        if (base >= COUNTERS.length()) return;
        var bytes = input.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int vertex = 0; vertex < count; vertex++) {
            int offset = bytes.position() + vertex * LineContract.VERTEX_BYTES;
            boolean finite = true;
            for (int component : COMPONENTS)
                finite &= Float.isFinite(bytes.getFloat(offset + component));
            boolean scalar = true;
            for (int component = 20; component < 56; component += 4)
                scalar &= bytes.getFloat(offset + component) == 0;
            if (scalar) COUNTERS.incrementAndGet(base + 6);
            if ((bytes.getInt(offset + 68) & LineContract.EXPLICIT_REFERENCE) != 0)
                for (int component = 56; component < 68; component += 4)
                    finite &= Float.isFinite(bytes.getFloat(offset + component));
            if (!finite) COUNTERS.incrementAndGet(base + 2);
            COUNTERS.incrementAndGet(base + 3);
            if ((bytes.getInt(offset + 68) & LineContract.EXPLICIT_REFERENCE) != 0)
                COUNTERS.incrementAndGet(
                        base + 4); // Explicit reference replaces normal-name convention.
        }
    }

    public static void event(LineContract.SourceKey source) {
        if (ENABLED && source.id() < 32) COUNTERS.incrementAndGet(source.id() * COLUMNS + 5);
    }

    /**
     * Columns: styles, classified width changes, invalid input, vertices, explicit references,
     * event batches, scalar owner fallbacks.
     */
    public static long[][] snapshot() {
        long[][] result = new long[32][COLUMNS];
        for (int source = 0; source < 32; source++)
            for (int column = 0; column < COLUMNS; column++)
                result[source][column] = COUNTERS.get(source * COLUMNS + column);
        return result;
    }
}
