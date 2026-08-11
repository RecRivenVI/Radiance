package com.radiance.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Frustum view used by world-geometry producers during the Radiance takeover.
 *
 * <p>Raster render-stage callbacks normally receive the camera frustum and may discard geometry
 * behind the camera. That geometry can still be reached by reflection, refraction and indirect
 * rays, so Radiance exposes an unbounded view while capturing world geometry for path tracing.</p>
 */
public final class UnboundedFrustum extends Frustum {

    public static final UnboundedFrustum INSTANCE = new UnboundedFrustum();

    private UnboundedFrustum() {
        super(new Matrix4f(), new Matrix4f());
    }

    @Override
    public boolean isVisible(AABB bounds) {
        return true;
    }
}
