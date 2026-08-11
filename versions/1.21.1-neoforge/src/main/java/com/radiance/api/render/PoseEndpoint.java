package com.radiance.api.render;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Source-declared TRS. Its interpolation is explicit; final matrices remain a separate mode. */
public record PoseEndpoint(Vector3dc position, Quaterniondc rotation, Vector3dc scale) {
    public PoseEndpoint {
        position = new Vector3d(position);
        rotation = new Quaterniond(rotation);
        scale = new Vector3d(scale);
        if (!position.isFinite()
                || !rotation.isFinite()
                || !Double.isFinite(rotation.lengthSquared())
                || rotation.lengthSquared() < 1e-24
                || !scale.isFinite()
                || scale.x() == 0
                || scale.y() == 0
                || scale.z() == 0)
            throw new IllegalArgumentException("Invalid source pose endpoint");
        rotation = new Quaterniond(rotation).normalize();
    }

    @Override
    public Vector3dc position() {
        return new Vector3d(position);
    }

    @Override
    public Quaterniondc rotation() {
        return new Quaterniond(rotation);
    }

    @Override
    public Vector3dc scale() {
        return new Vector3d(scale);
    }

    double[] packet() {
        return new double[] {
            position.x(),
            position.y(),
            position.z(),
            rotation.x(),
            rotation.y(),
            rotation.z(),
            rotation.w(),
            scale.x(),
            scale.y(),
            scale.z()
        };
    }

    Matrix4d interpolate(PoseEndpoint after, double start, double end, double time) {
        if (!Double.isFinite(start)
                || !Double.isFinite(end)
                || !Double.isFinite(time)
                || end < start
                || !Double.isFinite(end - start))
            throw new IllegalArgumentException("Invalid source pose time interval");
        for (int i = 0; i < 3; i++)
            if (Math.copySign(1, scale.get(i)) != Math.copySign(1, after.scale.get(i)))
                throw new IllegalArgumentException(
                        "Source scale interpolation crosses a singular pose");
        double alpha =
                end == start || time >= end
                        ? 1
                        : time <= start ? 0 : (time - start) / (end - start);
        var result =
                new Matrix4d()
                        .translation(new Vector3d(position).lerp(after.position, alpha))
                        .rotate(new Quaterniond(rotation).slerp(after.rotation, alpha))
                        .scale(new Vector3d(scale).lerp(after.scale, alpha));
        if (!result.isFinite())
            throw new IllegalArgumentException("Source interpolation produced a non-finite pose");
        return result;
    }
}
