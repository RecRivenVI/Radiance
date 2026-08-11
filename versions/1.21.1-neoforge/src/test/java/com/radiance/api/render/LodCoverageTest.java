package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LodCoverageTest {
    @Test
    void requiresEverySectionIncludingNegativeCoordinatesAndHoles() {
        var coverage =
                new LodScene.Coverage(
                        new long[] {7, -1, -1, -2, 1, -1, 0, -2, 1, 0, -1, -2, 1, 0, 0, -2, 1});
        assertEquals(7, coverage.revision());
        assertTrue(coverage.coversSections(-1, -1, -1, 2));
        assertFalse(coverage.coversSections(-1, -1, -1, 3));
        assertFalse(coverage.coversSections(0, 0, 1, 2));
        var gap = new LodScene.Coverage(new long[] {8, 0, 0, -2, -1, 0, 0, 1, 2});
        assertTrue(gap.coversSections(0, 0, -1, 1));
        assertFalse(gap.coversSections(0, 0, 0, 1));
        assertFalse(new LodScene.Coverage(new long[] {9}).coversSections(0, 0, 0, 1));
    }

    @Test
    void rejectsMalformedRowsAndOverflowingQueryEndsDoNotMatch() {
        for (long[] packet :
                new long[][] {
                    {},
                    {0},
                    {1, 0},
                    {1, 0, 0, 2, 1},
                    {1, 0, 0, -1, 1, 0, 0, 1, 2},
                    {1, 1, 0, 0, 1, 0, 0, 0, 1},
                    {1, 0, (long) Integer.MAX_VALUE + 1, 0, 1}
                })
            assertThrows(IllegalArgumentException.class, () -> new LodScene.Coverage(packet));
        var edge = new LodScene.Coverage(new long[] {1, Integer.MAX_VALUE, 0, 0, 1});
        assertTrue(edge.coversSections(Integer.MAX_VALUE, 0, 0, 1));
        assertFalse(edge.coversSections(Integer.MAX_VALUE, 0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> edge.coversSections(0, 0, 0, 0));
    }
}
