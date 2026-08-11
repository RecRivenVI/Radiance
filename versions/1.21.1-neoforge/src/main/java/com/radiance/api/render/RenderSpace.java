package com.radiance.api.render;

import com.radiance.api.render.internal.SpaceSceneNative;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.*;

/** A source-owned local coordinate space. Members keep local state when a parent moves. */
public final class RenderSpace implements AutoCloseable {
    private static final AtomicLong IDENTITIES = new AtomicLong();
    private static final AtomicLong VERSIONS = new AtomicLong();
    private static final Set<RenderSpace> ROOTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<RenderSpace> DIRTY =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final long identity = IDENTITIES.incrementAndGet();
    private final RenderSpace parent;
    private final Set<RenderSpace> children = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Matrix4d local = new Matrix4d(), composed = new Matrix4d();
    private final Matrix3f normal = new Matrix3f(), composedNormal = new Matrix3f();
    private long revision = VERSIONS.incrementAndGet(),
            composedRevision,
            parentRevision,
            composedVersion;
    private volatile boolean published, closed;
    private long publishedRevision;
    private int lightScene;
    private float lightSky;
    private Matrix4d lightMatrix;
    private final float[] sectionColourPlane = new float[4], sectionColourTarget = new float[4];
    private long sectionColourRevision, publishedSectionColourRevision;
    private PoseEndpoint motionBefore, motionAfter;
    private double motionStart, motionEnd, sampleTime;
    private long motionRevision, publishedMotionRevision;
    private double publishedSampleTime = Double.NaN;

    public RenderSpace() {
        this(null);
    }

    public RenderSpace(RenderSpace parent) {
        this.parent = parent;
        if (parent == null) ROOTS.add(this);
        else {
            synchronized (parent) {
                parent.live();
                parent.children.add(this);
            }
        }
    }

    public synchronized void pose(Matrix4dc matrix, Matrix3fc normalMatrix) {
        live();
        if (motionBefore == null && local.equals(matrix) && normal.equals(normalMatrix)) return;
        if (!matrix.isFinite() || !normalMatrix.isFinite())
            throw new IllegalArgumentException("Non-finite scene space pose");
        local.set(matrix);
        normal.set(normalMatrix);
        motionBefore = motionAfter = null;
        revision = VERSIONS.incrementAndGet();
        DIRTY.add(this);
    }

    /**
     * Explicit source time; endpoints never cause implicit interpolation of an arbitrary matrix.
     */
    public synchronized void motion(
            PoseEndpoint before, PoseEndpoint after, double start, double end, double time) {
        live();
        var sampled = before.interpolate(after, start, end, time);
        var sampledNormal = sampledNormal(sampled);
        motionBefore = before;
        motionAfter = after;
        motionStart = start;
        motionEnd = end;
        sampleTime = time;
        local.set(sampled);
        normal.set(sampledNormal);
        motionRevision = revision = VERSIONS.incrementAndGet();
        DIRTY.add(this);
    }

    public synchronized void sample(double time) {
        live();
        if (motionBefore == null)
            throw new IllegalStateException("Scene space has no source motion");
        if (sampleTime == time) return;
        var sampled = motionBefore.interpolate(motionAfter, motionStart, motionEnd, time);
        var sampledNormal = sampledNormal(sampled);
        sampleTime = time;
        local.set(sampled);
        normal.set(sampledNormal);
        revision = VERSIONS.incrementAndGet();
        DIRTY.add(this);
    }

    public synchronized long handle() {
        live();
        if (parent != null) parent.handle();
        if (!published || publishedRevision != revision) publish();
        return identity;
    }

    private static Matrix3f sampledNormal(Matrix4dc matrix) {
        var n = new Matrix3d(matrix).invert().transpose();
        var result =
                new Matrix3f()
                        .set(
                                (float) n.m00(),
                                (float) n.m01(),
                                (float) n.m02(),
                                (float) n.m10(),
                                (float) n.m11(),
                                (float) n.m12(),
                                (float) n.m20(),
                                (float) n.m21(),
                                (float) n.m22());
        if (!result.isFinite())
            throw new IllegalArgumentException(
                    "Source interpolation produced an invalid normal pose");
        return result;
    }

    private void publish() {
        long parentId = parent == null ? 0 : parent.handle();
        if (motionBefore == null) {
            SpaceSceneNative.put(
                    identity, parentId, local.get(new double[16]), normal.get(new float[9]));
            publishedMotionRevision = 0;
        } else if (!published || publishedMotionRevision != motionRevision) {
            SpaceSceneNative.motion(
                    identity,
                    parentId,
                    motionBefore.packet(),
                    motionAfter.packet(),
                    motionStart,
                    motionEnd,
                    sampleTime);
            publishedMotionRevision = motionRevision;
            publishedSampleTime = sampleTime;
        } else if (publishedSampleTime != sampleTime) {
            SpaceSceneNative.sample(identity, sampleTime);
            publishedSampleTime = sampleTime;
        }
        published = true;
        publishedRevision = revision;
        if (lightMatrix != null)
            SpaceSceneNative.lighting(
                    identity, lightScene, lightSky, lightMatrix.get(new double[16]));
        if (sectionColourRevision != publishedSectionColourRevision) {
            SpaceSceneNative.sectionColour(identity, sectionColourPlane, sectionColourTarget);
            publishedSectionColourRevision = sectionColourRevision;
        }
    }

    /**
     * World-space scalar plane, clamped to [0,1], mixing section vertex RGB toward a colour. Only
     * section members inherit it; entity/instance appearance and alpha are unaffected.
     */
    public synchronized void sectionColourGradient(
            float x, float y, float z, float offset, float red, float green, float blue) {
        live();
        float[] plane = {x, y, z, offset}, target = {red, green, blue, 1};
        for (float value : plane)
            if (!Float.isFinite(value))
                throw new IllegalArgumentException("Non-finite section colour plane");
        for (float value : target)
            if (!Float.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Invalid section colour target");
        if (Arrays.equals(plane, sectionColourPlane) && Arrays.equals(target, sectionColourTarget))
            return;
        System.arraycopy(plane, 0, sectionColourPlane, 0, 4);
        System.arraycopy(target, 0, sectionColourTarget, 0, 4);
        revision = VERSIONS.incrementAndGet();
        sectionColourRevision = revision;
        DIRTY.add(this);
    }

    public synchronized void clearSectionColourGradient() {
        live();
        if (sectionColourTarget[3] == 0) return;
        Arrays.fill(sectionColourPlane, 0);
        Arrays.fill(sectionColourTarget, 0);
        revision = VERSIONS.incrementAndGet();
        sectionColourRevision = revision;
        DIRTY.add(this);
    }

    public synchronized void lighting(int scene, float sky, Matrix4fc matrix) {
        live();
        if (scene < -1 || !Float.isFinite(sky) || sky < 0 || sky > 1 || !matrix.isFinite())
            throw new IllegalArgumentException("Invalid scene space lighting");
        if (lightMatrix != null
                && lightScene == scene
                && lightSky == sky
                && lightMatrix.equals(new Matrix4d(matrix))) return;
        lightScene = scene;
        lightSky = sky;
        lightMatrix = new Matrix4d(matrix);
        revision = VERSIONS.incrementAndGet();
        DIRTY.add(this);
    }

    private void compose() {
        live();
        long parentVersion = parent == null ? 0 : parent.version();
        if (composedRevision == revision && parentRevision == parentVersion) return;
        if (parent == null) {
            composed.set(local);
            composedNormal.set(normal);
        } else {
            parent.transform(composed).mul(local);
            parent.normal(composedNormal).mul(normal);
        }
        composedRevision = revision;
        parentRevision = parentVersion;
        composedVersion = VERSIONS.incrementAndGet();
    }

    public synchronized long version() {
        compose();
        return composedVersion;
    }

    public synchronized Matrix4d transform(Matrix4d destination) {
        compose();
        return destination.set(composed);
    }

    public synchronized Matrix3f normal(Matrix3f destination) {
        compose();
        return destination.set(composedNormal);
    }

    /** Maps the parent's already-applied capture basis back to local instance parameters. */
    public record Placement(RenderSpace space, Vector3dc origin, Matrix4fc captureToLocal) {
        public Placement {
            Objects.requireNonNull(space);
            origin = new Vector3d(origin);
            captureToLocal = new Matrix4f(captureToLocal);
        }
    }

    public Placement placement(Vector3dc origin, Matrix3fc capturedBasis) {
        var inverse = new Matrix3f(capturedBasis).invert();
        if (!inverse.isFinite()) throw new IllegalArgumentException("Singular capture basis");
        return new Placement(this, origin, new Matrix4f(inverse));
    }

    /** Drain producer changes on the render thread before a view consumes the scene. */
    public static void flushUpdates() {
        for (var space : List.copyOf(DIRTY)) {
            if (DIRTY.remove(space) && space.published && !space.closed) space.handle();
        }
    }

    private void live() {
        if (closed) throw new IllegalStateException("Scene space is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        if (!children.isEmpty())
            throw new IllegalStateException(
                    "Close scene members and child spaces before their parent");
        if (published) SpaceSceneNative.remove(identity);
        closed = true;
        DIRTY.remove(this);
        if (parent == null) ROOTS.remove(this);
        else parent.children.remove(this);
    }

    static void worldClosed() {
        // World invalidation retires Native scenes as a unit; do not send old handles into a new
        // world.
        for (RenderSpace space : ROOTS) space.invalidate();
        ROOTS.clear();
        DIRTY.clear();
    }

    private void invalidate() {
        children.forEach(RenderSpace::invalidate);
        children.clear();
        closed = true;
        published = false;
    }
}
