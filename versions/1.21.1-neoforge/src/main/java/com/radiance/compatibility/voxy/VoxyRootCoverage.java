package com.radiance.compatibility.voxy;

import java.util.Arrays;

/** Complete requested root domain, independent of mesh residency and its backpressure. */
final class VoxyRootCoverage {
    static final int MAX_RADIUS = 64;
    static final int MAX_ROWS = 2 * MAX_RADIUS + 2;
    final int minY, maxY;
    private final int[] rows;

    private VoxyRootCoverage(int minY, int maxY, int[] rows) {
        this.minY = minY;
        this.maxY = maxY;
        this.rows = rows;
    }

    static VoxyRootCoverage create(
            double cx, double cz, double distance, int minHeight, int maxHeight) {
        if (!Double.isFinite(cx)
                || !Double.isFinite(cz)
                || !Double.isFinite(distance)
                || distance <= 0
                || distance > MAX_RADIUS * 512.0
                || Math.abs(cx) > Integer.MAX_VALUE - 32768.0
                || Math.abs(cz) > Integer.MAX_VALUE - 32768.0
                || maxHeight <= minHeight)
            throw new IllegalArgumentException("Voxy requested coverage range");
        int minY = Math.floorDiv(minHeight, 512), maxY = Math.floorDiv(maxHeight - 1, 512);
        if (minY < -128 || maxY > 127) throw new IllegalArgumentException("Voxy root Y range");
        int minX = (int) Math.floor((cx - distance) / 512),
                maxX = (int) Math.floor((cx + distance) / 512);
        int minZ = (int) Math.floor((cz - distance) / 512),
                maxZ = (int) Math.floor((cz + distance) / 512);
        int[] rows = new int[MAX_ROWS * 3];
        int at = 0;
        for (int z = minZ; z <= maxZ; z++) {
            int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
            for (int x = minX; x <= maxX; x++) {
                double dx = Math.max(0, Math.abs(x * 512.0 + 256 - cx) - 256);
                double dz = Math.max(0, Math.abs(z * 512.0 + 256 - cz) - 256);
                if (dx * dx + dz * dz <= distance * distance) {
                    first = Math.min(first, x);
                    last = x;
                }
            }
            if (first <= last) {
                rows[at++] = z;
                rows[at++] = first;
                rows[at++] = last;
            }
        }
        return new VoxyRootCoverage(minY, maxY, Arrays.copyOf(rows, at));
    }

    int[] rows() {
        return rows.clone();
    }

    long rootCount() {
        long count = 0;
        for (int i = 0; i < rows.length; i += 3) count += (long) rows[i + 2] - rows[i + 1] + 1;
        return count * (maxY - minY + 1L);
    }

    boolean containsColumn(int x, int z) {
        int low = 0, high = rows.length / 3;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (rows[middle * 3] < z) low = middle + 1;
            else high = middle;
        }
        return low < rows.length / 3
                && rows[low * 3] == z
                && rows[low * 3 + 1] <= x
                && x <= rows[low * 3 + 2];
    }

    @FunctionalInterface
    interface ColumnConsumer {
        void accept(int x, int z);
    }

    void visitNearFirst(double cx, double cz, ColumnConsumer consumer) {
        int centerX = (int) Math.floor(cx / 512), centerZ = (int) Math.floor(cz / 512);
        for (int r = 0; r <= MAX_RADIUS + 1; r++) {
            for (int x = centerX - r; x <= centerX + r; x++) {
                if (containsColumn(x, centerZ - r)) consumer.accept(x, centerZ - r);
                if (r != 0 && containsColumn(x, centerZ + r)) consumer.accept(x, centerZ + r);
            }
            for (int z = centerZ - r + 1; z < centerZ + r; z++) {
                if (containsColumn(centerX - r, z)) consumer.accept(centerX - r, z);
                if (containsColumn(centerX + r, z)) consumer.accept(centerX + r, z);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VoxyRootCoverage c
                && minY == c.minY
                && maxY == c.maxY
                && Arrays.equals(rows, c.rows);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * minY + maxY) + Arrays.hashCode(rows);
    }
}
