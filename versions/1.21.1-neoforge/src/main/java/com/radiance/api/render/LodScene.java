package com.radiance.api.render;

import com.radiance.api.render.internal.LodSceneNative;
import com.radiance.render.contract.RenderAbi;
import java.nio.*;
import org.lwjgl.system.MemoryUtil;

/**
 * Owned sparse LOD scene. Canonical quads are compact grid rectangles, not source vertex bytes. All
 * inputs are borrowed synchronously; versions/BLAS and in-flight ownership remain in native. Node
 * key: signed x21/y19/z21 and level3, in a 32-cell grid; level is at most four.
 */
public final class LodScene implements AutoCloseable {
    private final long epoch;
    private boolean closed;

    public LodScene(long epoch) {
        if (epoch <= 0) throw new IllegalArgumentException("Invalid scene epoch");
        this.epoch = epoch;
        LodSceneNative.begin(epoch, RenderAbi.LOD_INPUT_VERSION);
    }

    private void live() {
        if (closed) throw new IllegalStateException("LOD scene closed");
    }

    public static long node(int level, int x, int y, int z) {
        if (level < 0
                || level > 4
                || x < -(1 << 20)
                || x >= (1 << 20)
                || y < -(1 << 18)
                || y >= (1 << 18)
                || z < -(1 << 20)
                || z >= (1 << 20)) throw new IllegalArgumentException("LOD node coordinate range");
        return ((long) level << 61)
                | (((long) z & 0x1fffff) << 40)
                | (((long) y & 0x7ffff) << 21)
                | ((long) x & 0x1fffff);
    }

    public void models(ByteBuffer records, IntBuffer colours) {
        live();
        if (!records.isDirect()
                || !colours.isDirect()
                || records.remaining() % RenderAbi.LOD_MODEL_BYTES != 0)
            throw new IllegalArgumentException("LOD material extent");
        LodSceneNative.models(
                epoch,
                MemoryUtil.memAddress(records),
                records.remaining() / RenderAbi.LOD_MODEL_BYTES,
                MemoryUtil.memAddress(colours),
                colours.remaining());
    }

    public boolean section(long node, long version, ByteBuffer quads) {
        live();
        if (!quads.isDirect() || quads.remaining() % RenderAbi.LOD_QUAD_BYTES != 0)
            throw new IllegalArgumentException("LOD rectangle extent");
        return LodSceneNative.section(
                epoch,
                node,
                version,
                MemoryUtil.memAddress(quads),
                quads.remaining() / RenderAbi.LOD_QUAD_BYTES);
    }

    public void select(long[] nodes, int[] rootRows, int minY, int maxY) {
        live();
        LodSceneNative.select(epoch, nodes, rootRows, minY, maxY);
    }

    public void retain(long[] nodes) {
        live();
        LodSceneNative.retain(epoch, nodes);
    }

    public long[] counters() {
        live();
        return LodSceneNative.counters(epoch);
    }

    /** Current primary coverage only. Retained handoff geometry is not a new near publication. */
    public Coverage coverage(long previousRevision) {
        live();
        long[] rows = LodSceneNative.coverage(epoch, previousRevision);
        return rows == null ? null : new Coverage(rows);
    }

    /** Immutable run-length rows of complete 16-block sections, ordered by x, y, then z. */
    public static final class Coverage {
        private final long[] rows;

        Coverage(long[] rows) {
            if (rows.length == 0 || (rows.length - 1) % 4 != 0 || rows[0] <= 0)
                throw new IllegalArgumentException("Invalid published coverage packet");
            for (int i = 1; i < rows.length; i += 4) {
                for (int j = 0; j < 4; j++)
                    if (rows[i + j] < Integer.MIN_VALUE || rows[i + j] > Integer.MAX_VALUE)
                        throw new IllegalArgumentException("Coverage coordinate range");
                if (rows[i + 3] < rows[i + 2])
                    throw new IllegalArgumentException("Reversed coverage row");
                if (i > 1
                        && (rows[i] < rows[i - 4]
                                || rows[i] == rows[i - 4]
                                        && (rows[i + 1] < rows[i - 3]
                                                || rows[i + 1] == rows[i - 3]
                                                        && rows[i + 2] <= rows[i - 1])))
                    throw new IllegalArgumentException("Overlapping or unsorted coverage rows");
            }
            this.rows = rows;
        }

        public long revision() {
            return rows[0];
        }

        public boolean coversSections(int x, int y, int z, int width) {
            if (width <= 0 || width > 32)
                throw new IllegalArgumentException("Coverage query width");
            for (long dx = x; dx < (long) x + width; dx++)
                for (long dy = y; dy < (long) y + width; dy++)
                    if (!contains(dx, dy, z, (long) z + width - 1)) return false;
            return true;
        }

        private boolean contains(long x, long y, long firstZ, long lastZ) {
            int low = 0, high = (rows.length - 1) / 4;
            while (low < high) {
                int middle = (low + high) >>> 1, at = 1 + middle * 4;
                if (rows[at] < x
                        || rows[at] == x
                                && (rows[at + 1] < y
                                        || rows[at + 1] == y && rows[at + 2] <= firstZ))
                    low = middle + 1;
                else high = middle;
            }
            if (low == 0) return false;
            int at = 1 + (low - 1) * 4;
            return rows[at] == x
                    && rows[at + 1] == y
                    && rows[at + 2] <= firstZ
                    && rows[at + 3] >= lastZ;
        }
    }

    public static void nearEmpty(int slot, int x, int y, int z) {
        LodSceneNative.nearEmpty(slot, x, y, z);
    }

    public void close() {
        if (!closed) {
            LodSceneNative.end(epoch);
            closed = true;
        }
    }
}
