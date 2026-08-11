package com.radiance.render.contract;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Version-neutral line input. Source adapters own all game/API state interpretation. */
public final class LineContract {
    public static final int MAGIC = 0x31434e4c;
    public static final int VERSION = 1;
    public static final int STYLE_BYTES = 64;
    public static final int VERTEX_BYTES = 72;
    public static final int EXPLICIT_REFERENCE = 1;

    private LineContract() {}

    public record SourceKey(int id, String label) {
        public SourceKey {
            if (id <= 0) throw new IllegalArgumentException("Source identity must be registered");
            Objects.requireNonNull(label);
        }
    }

    public record Float3(float x, float y, float z) {
        public boolean finite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
        }
    }

    /** Full captured directions. The extrusion adapter orthogonalizes without losing parity. */
    public record Basis3(Float3 x, Float3 y, Float3 z) {
        public static final Basis3 IDENTITY =
                new Basis3(new Float3(1, 0, 0), new Float3(0, 1, 0), new Float3(0, 0, 1));

        public Basis3 {
            Objects.requireNonNull(x);
            Objects.requireNonNull(y);
            Objects.requireNonNull(z);
        }

        public boolean finite() {
            return x.finite() && y.finite() && z.finite();
        }

        public float determinant() {
            return x.x * (y.y * z.z - y.z * z.y)
                    - y.x * (x.y * z.z - x.z * z.y)
                    + z.x * (x.y * y.z - x.z * y.y);
        }

        public static Basis3 copyOf(float[] values) {
            if (values == null) return IDENTITY;
            if (values.length != 9)
                throw new IllegalArgumentException("Expected nine basis values");
            return new Basis3(
                    new Float3(values[0], values[1], values[2]),
                    new Float3(values[3], values[4], values[5]),
                    new Float3(values[6], values[7], values[8]));
        }
    }

    public enum Coordinates {
        WORLD,
        CAMERA,
        CAMERA_SHIFT
    }

    public record DrawSpace(Coordinates coordinates, double x, double y, double z, Basis3 owner) {
        public DrawSpace {
            Objects.requireNonNull(coordinates);
            Objects.requireNonNull(owner);
        }

        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && owner.finite();
        }
    }

    public record DrawStamp(
            long world, long frame, long resources, int stage, SourceKey source, long sequence) {
        public DrawStamp {
            Objects.requireNonNull(source);
        }
    }

    public enum Encoding {
        MINECRAFT_LINES,
        MINECRAFT_LINE_STRIP,
        DEBUG_LINES,
        DEBUG_LINE_STRIP
    }

    public enum WidthPolicy {
        ORDINARY(0.005f),
        DEBUG(0.0075f);

        private final float worldPerUnit;

        WidthPolicy(float worldPerUnit) {
            this.worldPerUnit = worldPerUnit;
        }

        public float thickness(float sourceWidth) {
            return worldPerUnit * sourceWidth;
        }
    }

    public record Material(int alphaMode, float emission, int queryVisibility) {}

    /** World geometry is opaque by product policy; the source-alpha path remains usable. */
    public enum OpacityPolicy {
        OPAQUE,
        SOURCE_ALPHA;

        public Material material(int sourceAlphaMode, float emission, int queryVisibility) {
            return new Material(
                    this == SOURCE_ALPHA ? sourceAlphaMode : 0, emission, queryVisibility);
        }
    }

    public record LineOrigin(SourceKey source, WidthPolicy widthPolicy, float emission) {}

    public record LineStyle(
            Encoding encoding, float sourceWidth, float worldThickness, Material material) {
        public boolean valid() {
            return encoding != null
                    && material != null
                    && sourceWidth > 0
                    && Float.isFinite(sourceWidth)
                    && worldThickness > 0
                    && Float.isFinite(worldThickness)
                    && material.alphaMode >= 0
                    && material.alphaMode <= 24
                    && material.emission >= 0
                    && Float.isFinite(material.emission);
        }
    }

    public record LineVertex(
            Float3 position,
            int rgba,
            Float3 sourceNormal,
            Basis3 basis,
            Float3 reference,
            boolean explicitReference) {}

    /** The owner closes this once JNI has copied its bytes, including on rejection. */
    public record LineRequest(
            DrawStamp stamp,
            DrawSpace space,
            LineStyle style,
            ByteBuffer vertices,
            int vertexCount) {
        public boolean valid() {
            return stamp != null
                    && stamp.world > 0
                    && stamp.frame > 0
                    && stamp.resources > 0
                    && stamp.stage >= 0
                    && stamp.sequence > 0
                    && space != null
                    && space.finite()
                    && style != null
                    && style.valid()
                    && vertices != null
                    && vertexCount >= 0
                    && vertices.remaining() == (long) vertexCount * VERTEX_BYTES;
        }
    }

    public enum Submission {
        ACCEPTED,
        STALE,
        UNSUPPORTED,
        NOT_WORLD_TARGET
    }

    public static void writeStyle(
            ByteBuffer output, int offset, SourceKey source, LineStyle style, Basis3 owner) {
        if (!style.valid()
                || !owner.finite()
                || !Float.isFinite(owner.determinant())
                || Math.abs(owner.determinant()) <= 1e-12f)
            throw new IllegalArgumentException("Invalid line style");
        ByteBuffer bytes = output.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(offset, MAGIC);
        bytes.putInt(offset + 4, VERSION);
        bytes.putInt(offset + 8, source.id);
        bytes.putInt(
                offset + 12,
                (owner.determinant() < 0 ? RenderAbi.LINE_MIRROR_BIT : 0)
                        | (style.material.alphaMode << RenderAbi.LINE_ALPHA_SHIFT));
        bytes.putFloat(offset + 16, style.worldThickness);
        bytes.putFloat(offset + 20, style.sourceWidth);
        Float3[] axes = {owner.x, owner.y, owner.z};
        for (int axis = 0; axis < 3; axis++) {
            bytes.putFloat(offset + 24 + axis * 12, axes[axis].x);
            bytes.putFloat(offset + 28 + axis * 12, axes[axis].y);
            bytes.putFloat(offset + 32 + axis * 12, axes[axis].z);
        }
        bytes.putFloat(offset + 60, style.material.emission);
    }
}
