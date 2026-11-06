package com.radiance.adapter.vanilla;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CameraMediumHintTest {
    @Test
    void cameraOutsidePaneInSameVoxelDoesNotRequestPerPixelProbe() {
        var outside = new CameraMediumHint.Probe(.137499988, .682499886, .300000012);
        box(outside, .4375f, 0, 0, .5625f, 1, 1);
        box(outside, 0, 0, .4375f, 1, 1, .5625f);
        assertFalse(outside.inside());
        var inside = new CameraMediumHint.Probe(.5, .682499886, .3);
        box(inside, .4375f, 0, 0, .5625f, 1, 1);
        box(inside, 0, 0, .4375f, 1, 1, .5625f);
        assertTrue(inside.inside());
    }

    @Test
    void closedGlassAndOverlappingMultipartVolumesRemainEligible() {
        var glass = new CameraMediumHint.Probe(.25, .25, .25);
        box(glass, 0, 0, 0, 1, 1, 1);
        assertTrue(glass.inside());
        box(glass, .125f, 0, 0, .5f, 1, 1);
        assertTrue(glass.inside());
        var outside = new CameraMediumHint.Probe(-.2, -.3, -.4);
        box(outside, 0, 0, 0, 1, 1, 1);
        assertFalse(outside.inside());
    }

    @Test
    void boundaryAndUnsupportedDataAreConservative() {
        var boundary = new CameraMediumHint.Probe(0, .5, .5);
        box(boundary, 0, 0, 0, 1, 1, 1);
        assertTrue(boundary.inside());
        var unknown = new CameraMediumHint.Probe(.1, .2, .3);
        unknown.add(new int[4]);
        assertTrue(unknown.inside());
    }

    private static void box(
            CameraMediumHint.Probe p, float x, float y, float z, float X, float Y, float Z) {
        quad(p, x, y, z, x, Y, z, X, Y, z, X, y, z);
        quad(p, x, y, Z, X, y, Z, X, Y, Z, x, Y, Z);
        quad(p, x, y, z, x, y, Z, x, Y, Z, x, Y, z);
        quad(p, X, y, z, X, Y, z, X, Y, Z, X, y, Z);
        quad(p, x, y, z, X, y, z, X, y, Z, x, y, Z);
        quad(p, x, Y, z, x, Y, Z, X, Y, Z, X, Y, z);
    }

    private static void quad(CameraMediumHint.Probe p, float... xyz) {
        int[] vertices = new int[32];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 3; j++)
                vertices[i * 8 + j] = Float.floatToRawIntBits(xyz[i * 3 + j]);
        p.add(vertices);
    }
}
