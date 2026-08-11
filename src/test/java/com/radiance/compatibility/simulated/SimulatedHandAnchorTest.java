package com.radiance.compatibility.simulated;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SimulatedHandAnchorTest {
    @Test void endpointUsesExactlyTheHeldGeometryTransformForAllFovsAndRotations() {
        for (int fov : new int[]{30, 70, 100, 110}) {
            for (float yaw : new float[]{0, 0.6F, 2.4F}) {
                Matrix4f view = new Matrix4f().rotateX(0.4F).rotateY(yaw);
                double scale = Math.tan(Math.toRadians(fov / 2.0)) / Math.tan(Math.toRadians(70 / 2.0));
                Vector3d captured = new Vector3d(.35 * scale, -.25 * scale, -.9);
                Vector3d world = SimulatedHandAnchor.offset(captured, view);
                Vector3d backAtHand = new Vector3d(world).mulPosition(view);
                assertEquals(captured.x, backAtHand.x, 1e-6);
                assertEquals(captured.y, backAtHand.y, 1e-6);
                assertEquals(captured.z, backAtHand.z, 1e-6);
            }
        }
    }
}
