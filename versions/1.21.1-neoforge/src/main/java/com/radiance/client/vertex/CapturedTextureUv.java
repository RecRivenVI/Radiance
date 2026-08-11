package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.radiance.render.contract.DrawStateSnapshot;

/** Applies an immutable source draw's primary UV transform before native ownership begins. */
public final class CapturedTextureUv {
    private CapturedTextureUv() {}

    public static void apply(MeshData mesh, DrawStateSnapshot state) {
        if (state == null || state.uvTransform().equals(DrawStateSnapshot.UvTransform.IDENTITY))
            return;
        var transform = state.uvTransform();
        if (!transform.finite())
            throw new IllegalArgumentException("Non-finite source texture transform");
        var format = mesh.drawState().format();
        var element =
                format.contains(PBRVertexFormatElements.PBR_TEXTURE_UV)
                        ? PBRVertexFormatElements.PBR_TEXTURE_UV
                        : VertexFormatElement.UV0;
        if (!format.contains(element)
                || element.type() != VertexFormatElement.Type.FLOAT
                || element.count() != 2)
            throw new IllegalArgumentException("Source UV transform requires float2 UV0");
        int stride = format.getVertexSize(), offset = format.getOffset(element);
        var vertices = mesh.vertexBuffer().duplicate().order(java.nio.ByteOrder.nativeOrder());
        int count = mesh.drawState().vertexCount();
        if ((long) count * stride > vertices.remaining())
            throw new IllegalArgumentException("Incomplete source UV buffer");
        int start = vertices.position();
        // Validate the whole batch before changing it; a rejected draw must retain its source
        // bytes.
        for (int i = 0; i < count; i++) {
            int at = start + i * stride + offset;
            float u = vertices.getFloat(at), v = vertices.getFloat(at + 4);
            if (!Float.isFinite(transform.u(u, v)) || !Float.isFinite(transform.v(u, v)))
                throw new IllegalArgumentException("Non-finite transformed UV0");
        }
        for (int i = 0; i < count; i++) {
            int at = start + i * stride + offset;
            float u = vertices.getFloat(at), v = vertices.getFloat(at + 4);
            vertices.putFloat(at, transform.u(u, v));
            vertices.putFloat(at + 4, transform.v(u, v));
        }
    }
}
