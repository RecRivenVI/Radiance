package com.radiance.client.proxy.vulkan;

/** Native state for explicit multi-binding Veil vertex arrays. */
public final class VertexArrayProxy {

    private VertexArrayProxy() {
    }

    public static native int allocate();
    public static native void release(int arrayId);
    public static native void defineVertexBuffer(int arrayId, int binding, int bufferId,
        int offset, int stride, boolean perInstance);
    public static native void defineAttribute(int arrayId, int location, int binding,
        int componentCount, int componentType, boolean normalized, boolean integer,
        int relativeOffset);
    public static native void removeVertexBuffer(int arrayId, int binding);
    public static native void removeAttribute(int arrayId, int location);
    public static native void clearVertexBuffers(int arrayId);
    public static native void clearAttributes(int arrayId);
    public static native void defineIndexBuffer(int arrayId, int bufferId, int indexType,
        int indexCount);
    public static native void draw(int arrayId, int shaderId, int indexCount,
        int instanceCount, long uniformPtr, int uniformSize);
    public static native void drawIndirect(int arrayId, int shaderId, int indirectBufferId,
        long indirectOffset, int drawCount, int stride, long uniformPtr, int uniformSize);
}
