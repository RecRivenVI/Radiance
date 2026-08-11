package com.radiance.compatibility.veil;

import foundry.veil.impl.client.render.perspective.LevelPerspectiveCamera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;

/** Hides Veil's perspective-camera implementation from other compatibility modules. */
final class VeilPerspectiveCameraAdapter implements VeilAdapter.PerspectiveCamera {
    private final LevelPerspectiveCamera camera = new LevelPerspectiveCamera();

    @Override
    public void setup(Vector3dc position, Entity entity, ClientLevel level,
        Quaternionfc orientation, float partialTick) {
        this.camera.setup(position, entity, level, orientation, partialTick);
    }

    @Override
    public Quaternionf rotation() {
        return this.camera.rotation();
    }
}
