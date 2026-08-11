package com.radiance.compatibility.flywheel;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Scene metadata supplied by the original Sable embedding consumers. */
final class FlywheelEmbeddingLighting {
    private final Matrix4f sceneMatrix = new Matrix4f();
    private int scene;
    private float skyLightScale;
    private boolean configured;

    void set(Matrix4fc sceneMatrix, int scene, float skyLightScale) {
        this.sceneMatrix.set(sceneMatrix);
        this.scene = scene;
        this.skyLightScale = skyLightScale;
        this.configured = true;
    }

    Snapshot resolve(Snapshot parent, Matrix4fc localPose, Matrix4fc composedPose) {
        if (configured) {
            // Original Sable chooses poseComposed for static scene 0, and its own scene matrix
            // otherwise. -1 remains a missing scene, rather than being substituted with 0.
            return new Snapshot(scene, skyLightScale,
                scene == 0 ? composedPose : sceneMatrix);
        }
        if (parent != null) {
            Matrix4f matrix = new Matrix4f();
            parent.writeMatrix(matrix).mul(localPose);
            return new Snapshot(parent.scene(), parent.skyLightScale(), matrix);
        }
        return null;
    }

    static final class Snapshot {
        private final int scene;
        private final float skyLightScale;
        private final Matrix4f matrix;

        Snapshot(int scene, float skyLightScale, Matrix4fc matrix) {
            this.scene = scene;
            this.skyLightScale = skyLightScale;
            this.matrix = new Matrix4f(matrix);
        }

        int scene() { return scene; }
        float skyLightScale() { return skyLightScale; }
        Matrix4f writeMatrix(Matrix4f destination) { return destination.set(matrix); }
    }
}
