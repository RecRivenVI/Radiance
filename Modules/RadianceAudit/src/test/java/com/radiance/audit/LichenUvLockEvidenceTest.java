package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Upstream-baker evidence, not an assertion that the asymmetric two-sided surface is desirable. */
final class LichenUvLockEvidenceTest {
    @Test void unrotatedWallFacesSampleTheSamePhysicalPosition() {
        assertPair(BlockModelRotation.X0_Y0, true, false);
    }

    @Test void lockedFloorAndCeilingRotateTheOppositeFaceByHalfATurn() {
        assertPair(BlockModelRotation.X90_Y0, true, true);
        assertPair(BlockModelRotation.X270_Y0, true, true);
    }

    @Test void rotationWithoutUvLockPreservesCoincidentSurfaceCoordinates() {
        assertPair(BlockModelRotation.X90_Y0, false, false);
        assertPair(BlockModelRotation.X270_Y0, false, false);
    }

    private static void assertPair(BlockModelRotation rotation, boolean locked, boolean halfTurn) {
        // Minecraft 1.21.1 glow_lichen.json: two opposite faces at z=0.1/16, no thickness.
        var north = new BlockFaceUV(new float[]{16, 0, 0, 16}, 0);
        var south = new BlockFaceUV(new float[]{0, 0, 16, 16}, 0);
        if (locked) {
            north = FaceBakery.recomputeUVs(north, Direction.NORTH, rotation.getRotation());
            south = FaceBakery.recomputeUVs(south, Direction.SOUTH, rotation.getRotation());
        }
        // FaceInfo north vertex i and south vertex 3-i occupy the same position before AND
        // after the rigid model rotation. Compare physical positions, not observer screen UV.
        for (int i = 0; i < 4; i++) {
            assertEquals(halfTurn ? 16 - south.getU(3 - i) : south.getU(3 - i), north.getU(i), 0.00001);
            assertEquals(halfTurn ? 16 - south.getV(3 - i) : south.getV(3 - i), north.getV(i), 0.00001);
        }
    }
}
