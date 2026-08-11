package com.radiance.compatibility.simulated;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Same CAMERA-to-world transform as WorldPrepare's held-item TLAS instance. */
public final class SimulatedHandAnchor {
    private SimulatedHandAnchor() {}

    public static Vector3d offset(Vector3dc cameraLocal, Matrix4fc worldView) {
        // queueHandRebuild has already applied the hand/world FOV ratio. Reapplying
        // itemProjMat and the producer's 100/FOV compensation moves the rope away.
        return new Vector3d(cameraLocal).mulPosition(new Matrix4f(worldView).invert());
    }

    public static Vec3 offset(Vector3dc cameraLocal) {
        var gameRenderer = Minecraft.getInstance().gameRenderer;
        Matrix4f view = ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix();
        if (view == null) return null; // No valid world camera yet; leave the original producer alone.
        Vector3d result = offset(cameraLocal, view);
        return new Vec3(result.x, result.y, result.z);
    }
}
