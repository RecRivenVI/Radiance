package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/** PT-only line input: source attributes followed by the drawing frame, copied at each vertex. */
public final class CapturedLineVertexConsumer implements VertexConsumer, LineSectionReference {
    public static final int STRIDE = com.radiance.render.contract.LineContract.VERTEX_BYTES;
    public static final VertexFormat FORMAT =
            VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("Normal", VertexFormatElement.NORMAL)
                    .padding(1)
                    .add("LineFrameX", PBRVertexFormatElements.PBR_NORM)
                    .add("LineFrameY", PBRVertexFormatElements.PBR_POS)
                    .add("LineFrameZ", PBRVertexFormatElements.PBR_POST_BASE)
                    .padding(16)
                    .build();

    public com.radiance.render.contract.LineContract.SourceKey source =
            com.radiance.client.render.LineSources.ENTITY;
    public com.radiance.render.contract.LineContract.WidthPolicy widthPolicy =
            com.radiance.render.contract.LineContract.WidthPolicy.ORDINARY;
    public float emission;

    public com.radiance.render.contract.LineContract.LineOrigin origin() {
        return new com.radiance.render.contract.LineContract.LineOrigin(
                source, widthPolicy, emission);
    }

    private final ByteBufferBuilder allocator;
    private final VertexFormat.Mode mode;
    private final boolean requiresNormal;
    private final Vector3f position = new Vector3f();
    private long pointer = -1;
    private int count;
    private int remaining;
    private boolean pending;
    private boolean capturedFrame;
    private boolean building = true;

    public CapturedLineVertexConsumer(
            ByteBufferBuilder allocator, VertexFormat.Mode mode, VertexFormat sourceFormat) {
        if (!supports(mode, sourceFormat))
            throw new IllegalArgumentException("Unsupported line input");
        this.allocator = allocator;
        this.mode = mode;
        this.requiresNormal = sourceFormat.contains(VertexFormatElement.NORMAL);
    }

    public static boolean supports(VertexFormat.Mode mode, VertexFormat format) {
        return (mode == VertexFormat.Mode.LINES
                        || mode == VertexFormat.Mode.LINE_STRIP
                        || mode == VertexFormat.Mode.DEBUG_LINES
                        || mode == VertexFormat.Mode.DEBUG_LINE_STRIP)
                && (format == DefaultVertexFormat.POSITION_COLOR_NORMAL
                        || format == DefaultVertexFormat.POSITION_COLOR);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (!building) throw new IllegalStateException("Not building!");
        endVertex();
        pointer = allocator.reserve(STRIDE);
        MemoryUtil.memSet(pointer, 0, STRIDE);
        MemoryUtil.memPutFloat(pointer, x);
        MemoryUtil.memPutFloat(pointer + 4, y);
        MemoryUtil.memPutFloat(pointer + 8, z);
        count++;
        remaining = requiresNormal ? 3 : 1;
        pending = true;
        capturedFrame = false;
        return this;
    }

    @Override
    public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        pose.transformPosition(x, y, z, position);
        addVertex(position.x, position.y, position.z);
        // Copy directions, not the inverse-transpose normal matrix. No mutable Pose escapes.
        captureFrame(pose);
        return this;
    }

    private void captureFrame(Matrix4f pose) {
        MemoryUtil.memPutFloat(pointer + 20, pose.m00());
        MemoryUtil.memPutFloat(pointer + 24, pose.m01());
        MemoryUtil.memPutFloat(pointer + 28, pose.m02());
        MemoryUtil.memPutFloat(pointer + 32, pose.m10());
        MemoryUtil.memPutFloat(pointer + 36, pose.m11());
        MemoryUtil.memPutFloat(pointer + 40, pose.m12());
        MemoryUtil.memPutFloat(pointer + 44, pose.m20());
        MemoryUtil.memPutFloat(pointer + 48, pose.m21());
        MemoryUtil.memPutFloat(pointer + 52, pose.m22());
        capturedFrame = true;
    }

    @Override
    public void sectionReference(PoseStack.Pose pose, float x, float y, float z) {
        if (!building || !pending) throw new IllegalStateException("No current line vertex");
        pose.pose().transformDirection(x, y, z, position);
        MemoryUtil.memPutFloat(pointer + 56, position.x);
        MemoryUtil.memPutFloat(pointer + 60, position.y);
        MemoryUtil.memPutFloat(pointer + 64, position.z);
        MemoryUtil.memPutInt(
                pointer + 68, com.radiance.render.contract.LineContract.EXPLICIT_REFERENCE);
    }

    @Override
    public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
        if (!building || !pending) throw new IllegalStateException("No current line vertex");
        if (!capturedFrame) captureFrame(pose.pose());
        pose.transformNormal(x, y, z, position);
        return setNormal(position.x, position.y, position.z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        if (consume(1)) {
            MemoryUtil.memPutByte(pointer + 12, (byte) red);
            MemoryUtil.memPutByte(pointer + 13, (byte) green);
            MemoryUtil.memPutByte(pointer + 14, (byte) blue);
            MemoryUtil.memPutByte(pointer + 15, (byte) alpha);
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (consume(2)) {
            MemoryUtil.memPutByte(pointer + 16, packNormal(x));
            MemoryUtil.memPutByte(pointer + 17, packNormal(y));
            MemoryUtil.memPutByte(pointer + 18, packNormal(z));
        }
        return this;
    }

    private static byte packNormal(float value) {
        return (byte) (Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
    }

    private boolean consume(int bit) {
        if (!building || !pending) throw new IllegalStateException("No current line vertex");
        boolean consume = (remaining & bit) != 0;
        remaining &= ~bit;
        return consume;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    private void endVertex() {
        if (!pending) return;
        if (remaining != 0) throw new IllegalStateException("Missing line color or normal");
        if (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP) {
            long duplicate = allocator.reserve(STRIDE);
            // reserve can relocate storage: the original is immediately before its duplicate.
            MemoryUtil.memCopy(duplicate - STRIDE, duplicate, STRIDE);
            count++;
        }
        pending = false;
    }

    public MeshData build() {
        if (!building) throw new IllegalStateException("Not building!");
        endVertex();
        building = false;
        if (count == 0) return null;
        return new MeshData(
                allocator.build(),
                new MeshData.DrawState(
                        FORMAT,
                        count,
                        mode.indexCount(count),
                        mode,
                        VertexFormat.IndexType.least(count)));
    }
}
