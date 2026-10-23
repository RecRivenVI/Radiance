package com.radiance.compatibility.voxy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class VoxyRootCoverageTest {
    @Test
    void sourceRadius64IsCompleteWithoutResidentSizedRootArray() {
        var coverage = VoxyRootCoverage.create(.5, -.5, 32768, -64, 320);
        assertTrue(coverage.rows().length <= VoxyRootCoverage.MAX_ROWS * 3);
        var visited = new HashSet<String>();
        coverage.visitNearFirst(
                .5, -.5, (x, z) -> assertTrue(visited.add(x + "," + z), "duplicate column"));
        assertTrue(visited.size() * 2 > 8192);
        for (int x = -66; x <= 66; x++)
            for (int z = -66; z <= 66; z++) {
                double dx = Math.max(0, Math.abs(x * 512.0 + 256 - .5) - 256);
                double dz = Math.max(0, Math.abs(z * 512.0 + 256 + .5) - 256);
                boolean expected = dx * dx + dz * dz <= 32768.0 * 32768;
                assertEquals(expected, coverage.containsColumn(x, z));
                assertEquals(expected, visited.contains(x + "," + z));
            }
        int[] copy = coverage.rows();
        copy[0] = 999;
        assertNotEquals(999, coverage.rows()[0]);
    }

    @Test
    void negativeBoundariesAndSourceRangeRemainExact() {
        for (double x : new double[] {-512.5, -512, -.5, 0, 512.5}) {
            var c = VoxyRootCoverage.create(x, x, 512, -64, 320);
            assertTrue(c.containsColumn((int) Math.floor(x / 512), (int) Math.floor(x / 512)));
            assertEquals(-1, c.minY);
            assertEquals(0, c.maxY);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> VoxyRootCoverage.create(0, 0, 32769, -64, 320));
        assertThrows(
                IllegalArgumentException.class,
                () -> VoxyRootCoverage.create(Double.NaN, 0, 1, -64, 320));
        assertThrows(
                IllegalArgumentException.class,
                () -> VoxyRootCoverage.create(0, 0, 1, -65537, 320));
    }
}
