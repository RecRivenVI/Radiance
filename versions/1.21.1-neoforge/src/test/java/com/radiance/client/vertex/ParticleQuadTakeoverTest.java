package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ParticleQuadTakeoverTest {
    @Test
    void expiredVibrationAndDegenerateFacingUseOriginalVertexHandling() {
        assertFalse(
                ParticleQuadTakeover.instancePose(
                        ParticleQuadTakeover.transform(
                                new Quaternionf(Float.NaN, Float.NaN, Float.NaN, Float.NaN),
                                Float.NaN,
                                1,
                                2,
                                .3f)));
        assertFalse(
                ParticleQuadTakeover.instancePose(
                        ParticleQuadTakeover.transform(new Quaternionf(0, 0, 0, 0), 1, 2, 3, .3f)));
    }

    @Test
    void particleHistoryHasNativeRequiredTagAndIndependentOwnerAndQuad() {
        long a = ParticleQuadTakeover.historyIdentity(1, 0);
        assertTrue(a < 0);
        assertNotEquals(a, ParticleQuadTakeover.historyIdentity(2, 0));
        assertNotEquals(a, ParticleQuadTakeover.historyIdentity(1, 1));
        assertNotEquals(a, RigidModelCapture.historyIdentity(1, 1, 0));
    }

    @Test
    void sourceCornersMatchInstanceForRollNegativeSizeAndNonUnitFacingY() {
        for (Quaternionf rotation :
                new Quaternionf[] {
                    new Quaternionf(),
                    new Quaternionf().rotationXYZ(.6f, -.8f, .25f),
                    new Quaternionf(0, .38f, 0, .71f),
                    new Quaternionf().rotationXYZ(.4f, 1.1f, -.2f).rotateZ(.7f)
                }) {
            for (float size : new float[] {.1f, 1.5f, -.4f, 1e-14f}) {
                var instance =
                        ParticleQuadTakeover.transform(rotation, 12.125f, -.75f, -9.5f, size);
                for (int[] corner : new int[][] {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}}) {
                    var source =
                            new Vector3f(corner[0], corner[1], 0)
                                    .rotate(rotation)
                                    .mul(size)
                                    .add(12.125f, -.75f, -9.5f);
                    var result = instance.transformPosition(new Vector3f(corner[0], corner[1], 0));
                    assertTrue(source.equals(result, 3e-5f), source + " / " + result);
                }
                assertTrue(instance.isFinite());
                assertNotEquals(0, instance.determinant());
            }
        }
    }
}
