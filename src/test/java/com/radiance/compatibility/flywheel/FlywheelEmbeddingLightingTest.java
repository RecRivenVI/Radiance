package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class FlywheelEmbeddingLightingTest {
    @Test
    void plotAndMissingScenesPreserveTheSuppliedSceneMatrixAndSkyScale() {
        FlywheelEmbeddingLighting state = new FlywheelEmbeddingLighting();
        Matrix4f scene = new Matrix4f().translation(8.0F, 64.0F, -3.0F).rotateY(0.4F);
        Matrix4f physical = new Matrix4f().translation(800.0F, 20.0F, 600.0F);
        state.set(scene, 7, 0.35F);
        FlywheelEmbeddingLighting.Snapshot captured = state.resolve(null, new Matrix4f(), physical);
        scene.identity();
        assertEquals(7, captured.scene());
        assertEquals(0.35F, captured.skyLightScale());
        assertEquals(new Matrix4f().translation(8.0F, 64.0F, -3.0F).rotateY(0.4F),
            captured.writeMatrix(new Matrix4f()));
        state.set(new Matrix4f().translation(2.0F, 4.0F, 6.0F), -1, 0.1F);
        FlywheelEmbeddingLighting.Snapshot missing = state.resolve(null, new Matrix4f(), physical);
        assertEquals(-1, missing.scene());
        assertEquals(0.1F, missing.skyLightScale());
        assertEquals(new Matrix4f().translation(2.0F, 4.0F, 6.0F),
            missing.writeMatrix(new Matrix4f()));
    }

    @Test
    void staticSceneUsesComposedPhysicalPoseRatherThanThePlotMatrix() {
        FlywheelEmbeddingLighting state = new FlywheelEmbeddingLighting();
        Matrix4f physical = new Matrix4f().translation(16.0F, 3.0F, 20.0F).rotateZ(0.7F);
        state.set(new Matrix4f().translation(20_000_000.0F, 2.0F, 20_000_000.0F), 0, 0.6F);
        FlywheelEmbeddingLighting.Snapshot snapshot = state.resolve(null, new Matrix4f(), physical);
        assertEquals(physical, snapshot.writeMatrix(new Matrix4f()));
        assertEquals(0, snapshot.scene());
        assertEquals(0.6F, snapshot.skyLightScale());
    }

    @Test
    void unconfiguredChildInheritsAndComposesUntilAnExplicitSceneOverridesIt() {
        FlywheelEmbeddingLighting parent = new FlywheelEmbeddingLighting();
        FlywheelEmbeddingLighting child = new FlywheelEmbeddingLighting();
        Matrix4f parentScene = new Matrix4f().translation(2.0F, 5.0F, 9.0F).rotateX(0.3F);
        Matrix4f childPose = new Matrix4f().translation(4.0F, -3.0F, 8.0F).rotateY(0.9F);
        assertNull(child.resolve(null, childPose, new Matrix4f()));
        parent.set(parentScene, 12, 0.4F);
        FlywheelEmbeddingLighting.Snapshot inherited = child.resolve(
            parent.resolve(null, new Matrix4f(), new Matrix4f()), childPose, new Matrix4f());
        assertEquals(new Matrix4f(parentScene).mul(childPose), inherited.writeMatrix(new Matrix4f()));
        assertEquals(12, inherited.scene());
        assertEquals(0.4F, inherited.skyLightScale());
        Matrix4f ownScene = new Matrix4f().translation(1.0F, 60.0F, 2.0F);
        child.set(ownScene, 13, 0.2F);
        FlywheelEmbeddingLighting.Snapshot overridden = child.resolve(inherited, childPose,
            new Matrix4f());
        assertEquals(ownScene, overridden.writeMatrix(new Matrix4f()));
        assertEquals(13, overridden.scene());
        assertEquals(0.2F, overridden.skyLightScale());
    }
}
