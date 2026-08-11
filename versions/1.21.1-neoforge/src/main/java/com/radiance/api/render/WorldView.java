package com.radiance.api.render;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;

import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** Immutable world-view parameters. Matrices and atmosphere values are copied at construction. */
public record WorldView(
        Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix,
        Matrix4f projectionMatrix,
        Matrix4f glintMatrix,
        float gameTime,
        int overlayTextureID,
        boolean firstPerson,
        float fadeStart,
        float fadeEnd,
        float[] atmosphereColor,
        int environmentFlags,
        int skyType,
        float glintAlpha,
        int endSkyTextureID,
        int endPortalTextureID,
        int lightMapTextureID) {
    public WorldView {
        viewMatrix = new Matrix4f(viewMatrix);
        effectedViewMatrix = new Matrix4f(effectedViewMatrix);
        projectionMatrix = new Matrix4f(projectionMatrix);
        glintMatrix = new Matrix4f(glintMatrix);
        atmosphereColor = atmosphereColor.clone();
        if (atmosphereColor.length != 4)
            throw new IllegalArgumentException("Atmosphere requires RGBA");
    }

    public Matrix4f viewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public Matrix4f effectedViewMatrix() {
        return new Matrix4f(effectedViewMatrix);
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public Matrix4f glintMatrix() {
        return new Matrix4f(glintMatrix);
    }

    public float[] atmosphereColor() {
        return atmosphereColor.clone();
    }

    public static void origin(double x, double y, double z) {
        com.radiance.client.proxy.world.PlayerProxy.setCameraPos(x, y, z);
    }

    public void submit() {
        try (MemoryStack stack = stackPush()) {
            int size = 704;
            ByteBuffer bb = stack.calloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            viewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            effectedViewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            baseAddr += Float.BYTES * 16 * 3; // skip the inverse
            baseAddr += Float.BYTES * 2; // skip the jitter

            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES; // skip seed

            glintMatrix.get(baseAddr, bb);
            baseAddr += 64;
            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, firstPerson ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, fadeStart);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fadeEnd);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, atmosphereColor[0]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, atmosphereColor[1]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, atmosphereColor[2]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, atmosphereColor[3]);
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, environmentFlags);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, skyType);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, glintAlpha);
            baseAddr += Float.BYTES;
            baseAddr += Integer.BYTES;

            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, lightMapTextureID);
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

            // HDR camera effects are filled later by ScreenEffectRenderer interception.  Clear
            // every slot here so frames without an overlay never reuse a previous frame's state.
            for (int i = 0; i < 4; i++) {
                bb.putInt(baseAddr, -1);
                baseAddr += Integer.BYTES;
            }
            for (int i = 0; i < 6 * 4; i++) {
                bb.putFloat(baseAddr, 0.0F);
                baseAddr += Float.BYTES;
            }

            com.radiance.client.proxy.vulkan.BufferProxy.updateWorldUniform(addr);
        }
    }
}
