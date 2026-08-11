package com.radiance.compatibility.veil;

import foundry.veil.api.client.render.vertex.VertexArray;
import java.nio.ByteBuffer;

/** Veil-facing construction and static operations for Vulkan-backed vertex arrays. */
public final class VeilVertexArrayAdapter {
    private VeilVertexArrayAdapter() {
    }

    public static VertexArray create() {
        return new RadianceVertexArray();
    }

    public static VertexArray[] createMany(int count) {
        if (count < 0) {
            throw new NegativeArraySizeException(Integer.toString(count));
        }
        VertexArray[] arrays = new VertexArray[count];
        createInto(arrays);
        return arrays;
    }

    public static void createInto(VertexArray[] arrays) {
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = create();
        }
    }

    public static void upload(int buffer, ByteBuffer data) {
        RadianceVertexArray.uploadBuffer(buffer, data);
    }

    public static void unbind() {
        VeilVertexArrayBridge.unbind();
    }
}
