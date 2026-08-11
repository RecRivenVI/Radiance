package com.radiance.render.contract;

/** An authoritative dynamic mesh request. The consumer copies input before this request returns. */
public record MeshSubmission(
        long owner,
        int geometry,
        int texture,
        int format,
        int topology,
        int vertexStride,
        int vertexCount,
        long address,
        long bytes,
        TakeoverSubmission.Program program,
        DrawStateSnapshot state) {
    public MeshSubmission {
        java.util.Objects.requireNonNull(program);
        java.util.Objects.requireNonNull(state);
        if (vertexCount < 0
                || vertexStride <= 0
                || bytes < (long) vertexStride * vertexCount
                || (vertexCount > 0 && address == 0))
            throw new IllegalArgumentException("Mesh input does not cover its declared vertices");
    }
}
