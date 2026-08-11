package com.radiance.compatibility.simulated;

import com.radiance.client.constant.Constants;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.compatibility.catnip.CatnipWorldGeometryCapture;
import net.createmod.catnip.render.SuperRenderTypeBuffer;

/** Captures the original staff producer, including its animated LineOutline geometry. */
public final class SimulatedStaffCapture implements AutoCloseable {
    private static final ThreadLocal<SimulatedStaffCapture> CURRENT = new ThreadLocal<>();
    private final SimulatedStaffCapture previous = CURRENT.get();
    public final StorageVertexConsumerProvider locks = new StorageVertexConsumerProvider(4096, 1.0F);
    private final CatnipWorldGeometryCapture beams = new CatnipWorldGeometryCapture(
        SimulatedStaffCapture.class, "radiance/outliner/physics_staff", 1.0F);
    private boolean locksSubmitted;

    public SimulatedStaffCapture() { CURRENT.set(this); }

    public static SuperRenderTypeBuffer beamBuffer(SuperRenderTypeBuffer original) {
        var scope = CURRENT.get();
        return scope == null ? original : scope.beams;
    }

    public void submit() {
        beams.submit();
        locksSubmitted = true; // submitCaptured owns/always closes the storage.
        WorldMeshSink.submitCaptured(locks, SimulatedStaffCapture.class,
            "radiance/physics_staff/locks", Constants.RayTracingFlags.PRIORITY_ONLY);
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        try { beams.close(); } finally { if (!locksSubmitted) locks.close(); }
    }
}
