package com.radiance.compatibility.flywheel;

import org.joml.Matrix4fc;

/** Independent of Sable so Flywheel-only embeddings do not link an absent mod interface. */
public interface FlywheelEmbeddingLightingAccess {
    void radiance$setEmbeddingLighting(Matrix4fc sceneMatrix, int scene, float skyLightScale);
}
