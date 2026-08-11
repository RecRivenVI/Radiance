package com.radiance.client.proxy.vulkan;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.texture.TextureTracker;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class BufferProxy {

    public static native int allocateBuffer();
    public static native int allocatePersistentBuffer();
    public static native void releasePersistentBuffer(int id);

    public static native void initializeBuffer(int id, int size, int usageFlags);

    public static native void buildIndexBuffer(int id, int type, int drawMode, int vertexCount,
        int expectedIndexCount);

    public static native void queueUpload(long ptr, int dstId);
    public static native void queuePersistentUploadRange(long ptr, int size, int dstId,
        int dstOffset);

    public static BufferInfo getBufferInfo(ByteBuffer buf) {
        if (buf == null) {
            throw new NullPointerException("Buffer upload source must not be null");
        }
        ByteBuffer b = buf.slice();
        if (!b.isDirect()) {
            throw new IllegalArgumentException("Buffer upload source must be direct");
        }

        long addr = memAddress(b);
        int size = b.remaining();
        return new BufferInfo(b, addr, size);
    }

    private static void queueUpload(ByteBuffer buf, int expectedSize, int dstId) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("Negative buffer upload size: " + expectedSize);
        }
        if (expectedSize == 0) {
            return;
        }
        BufferInfo bufferInfo = getBufferInfo(buf);
        if (bufferInfo.size != expectedSize) {
            throw new IllegalArgumentException(
                "Buffer upload size mismatch: expected " + expectedSize + ", got "
                    + bufferInfo.size);
        }
        queueUpload(bufferInfo.addr, dstId);
    }

    public static native void performQueuedUpload();

    public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
        MeshData builtBuffer) {
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        int vertexSize = drawParameters.vertexCount() * drawParameters.format().getVertexSize();
        int vertexId = allocateBuffer();
        initializeBuffer(vertexId, vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        queueUpload(builtBuffer.vertexBuffer(), vertexSize, vertexId);

        int indexSize = drawParameters.indexCount() * drawParameters.indexType().bytes;
        int indexId = allocateBuffer();
        initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        if (builtBuffer.indexBuffer() != null) {
            queueUpload(builtBuffer.indexBuffer(), indexSize, indexId);
        } else {
            int type = Constants.IndexTypes.getValue(drawParameters.indexType());
            int drawMode = Constants.DrawModes.getValue(drawParameters.mode());
            buildIndexBuffer(indexId, type, drawMode, drawParameters.vertexCount(),
                drawParameters.indexCount());
        }

        VertexIndexBufferHandle handle = new VertexIndexBufferHandle(vertexId, indexId);
        createAndUploadPatchIndexBuffer(builtBuffer, handle, false);
        return handle;
    }

    public static VertexIndexBufferHandle allocatePersistentBufferPair() {
        return allocatePersistentBufferPair(BufferProxy::allocatePersistentBuffer,
            BufferProxy::releasePersistentBuffer);
    }

    static VertexIndexBufferHandle allocatePersistentBufferPair(IntSupplier allocator,
        IntConsumer releaser) {
        int vertexId = -1;
        try {
            vertexId = allocator.getAsInt();
            if (vertexId < 0) {
                throw new IllegalStateException(
                    "Native persistent vertex buffer allocation returned " + vertexId);
            }
            int indexId = allocator.getAsInt();
            if (indexId < 0) {
                throw new IllegalStateException(
                    "Native persistent index buffer allocation returned " + indexId);
            }
            return new VertexIndexBufferHandle(vertexId, indexId);
        } catch (RuntimeException | Error failure) {
            if (vertexId >= 0) {
                try {
                    releaser.accept(vertexId);
                } catch (RuntimeException | Error releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            throw failure;
        }
    }

    public static VertexIndexBufferHandle createAndUploadPersistentVertexIndexBuffer(
        MeshData builtBuffer) {
        MeshData.DrawState drawState = builtBuffer.drawState();
        VertexIndexBufferHandle handle = allocatePersistentBufferPair();
        try {
            int vertexSize = Math.multiplyExact(drawState.vertexCount(),
                drawState.format().getVertexSize());
            initializeBuffer(handle.vertexId, vertexSize,
                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
            queueUpload(builtBuffer.vertexBuffer(), vertexSize, handle.vertexId);

            int indexSize = Math.multiplyExact(drawState.indexCount(),
                drawState.indexType().bytes);
            initializeBuffer(handle.indexId, indexSize,
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
            if (builtBuffer.indexBuffer() != null) {
                queueUpload(builtBuffer.indexBuffer(), indexSize, handle.indexId);
            } else {
                buildIndexBuffer(handle.indexId, Constants.IndexTypes.getValue(drawState.indexType()),
                    Constants.DrawModes.getValue(drawState.mode()), drawState.vertexCount(),
                    drawState.indexCount());
            }
            createAndUploadPatchIndexBuffer(builtBuffer, handle, true);
            return handle;
        } catch (RuntimeException | Error failure) {
            releasePersistentPair(handle, failure);
            throw failure;
        }
    }

    private static void createAndUploadPatchIndexBuffer(MeshData mesh,
        VertexIndexBufferHandle handle, boolean persistent) {
        MeshData.DrawState state = mesh.drawState();
        if (state.mode() != VertexFormat.Mode.QUADS || state.vertexCount() == 0) {
            return;
        }
        ByteBuffer patchIndices = buildPatchIndices(mesh.indexBuffer(), state.indexType(),
            state.indexCount(), state.vertexCount());
        int id = persistent ? allocatePersistentBuffer() : allocateBuffer();
        if (id < 0) {
            memFree(patchIndices);
            throw new IllegalStateException("Native patch index allocation returned " + id);
        }
        try {
            initializeBuffer(id, patchIndices.remaining(),
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
            queueUpload(patchIndices, patchIndices.remaining(), id);
            handle.patchIndexId = id;
            handle.patchIndexCount = state.vertexCount();
        } catch (RuntimeException | Error failure) {
            if (persistent) {
                try {
                    releasePersistentBuffer(id);
                } catch (RuntimeException | Error releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            throw failure;
        } finally {
            memFree(patchIndices);
        }
    }

    static ByteBuffer buildPatchIndices(ByteBuffer triangleIndices,
        VertexFormat.IndexType indexType, int indexCount, int vertexCount) {
        if (vertexCount < 0 || vertexCount % 4 != 0) {
            throw new IllegalArgumentException(
                "QUADS patch vertex count must be divisible by four: " + vertexCount);
        }
        ByteBuffer patches = memAlloc(Math.multiplyExact(vertexCount, indexType.bytes));
        if (triangleIndices == null) {
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                putIndex(patches, indexType, vertex);
            }
            patches.flip();
            return patches;
        }
        if (indexCount != vertexCount / 4 * 6) {
            memFree(patches);
            throw new IllegalArgumentException("QUADS triangle index count mismatch");
        }
        ByteBuffer source = triangleIndices.slice().order(triangleIndices.order());
        if (source.remaining() != indexCount * indexType.bytes) {
            memFree(patches);
            throw new IllegalArgumentException("QUADS triangle index byte count mismatch");
        }
        for (int index = 0; index < indexCount; index += 6) {
            int a = getIndex(source, indexType);
            int b = getIndex(source, indexType);
            int c = getIndex(source, indexType);
            int c2 = getIndex(source, indexType);
            int d = getIndex(source, indexType);
            int a2 = getIndex(source, indexType);
            if (c != c2 || a != a2) {
                memFree(patches);
                throw new IllegalArgumentException(
                    "QUADS indices are not the Minecraft [a,b,c,c,d,a] pattern");
            }
            if (a >= vertexCount || b >= vertexCount || c >= vertexCount || d >= vertexCount) {
                memFree(patches);
                throw new IllegalArgumentException("QUADS patch index exceeds vertex count");
            }
            putIndex(patches, indexType, a);
            putIndex(patches, indexType, b);
            putIndex(patches, indexType, c);
            putIndex(patches, indexType, d);
        }
        patches.flip();
        return patches;
    }

    private static int getIndex(ByteBuffer source, VertexFormat.IndexType type) {
        return type == VertexFormat.IndexType.SHORT
            ? Short.toUnsignedInt(source.getShort()) : source.getInt();
    }

    private static void putIndex(ByteBuffer destination, VertexFormat.IndexType type, int value) {
        if (type == VertexFormat.IndexType.SHORT) {
            if (value < 0 || value > 0xffff) {
                throw new IllegalArgumentException("Patch index exceeds unsigned short: " + value);
            }
            destination.putShort((short) value);
        } else {
            destination.putInt(value);
        }
    }

    public static int createAndUploadPersistentIndexBuffer(ByteBuffer source) {
        BufferInfo info = getBufferInfo(source);
        int indexId = allocatePersistentBuffer();
        if (indexId < 0) {
            throw new IllegalStateException(
                "Native persistent index buffer allocation returned " + indexId);
        }
        try {
            initializeBuffer(indexId, info.size, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
            queueUpload(info.buf, info.size, indexId);
            return indexId;
        } catch (RuntimeException | Error failure) {
            try {
                releasePersistentBuffer(indexId);
            } catch (RuntimeException | Error releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    public static int createAndUploadPersistentPatchIndexBuffer(ByteBuffer triangleIndices,
        VertexFormat.IndexType indexType, int indexCount, int vertexCount) {
        ByteBuffer patches = buildPatchIndices(triangleIndices, indexType, indexCount,
            vertexCount);
        try {
            return createAndUploadPersistentIndexBuffer(patches);
        } finally {
            memFree(patches);
        }
    }

    public static void releasePersistentPair(VertexIndexBufferHandle handle) {
        releasePersistentPair(handle, null, BufferProxy::releasePersistentBuffer);
    }

    private static void releasePersistentPair(VertexIndexBufferHandle handle, Throwable primary) {
        releasePersistentPair(handle, primary, BufferProxy::releasePersistentBuffer);
    }

    static void releasePersistentPair(VertexIndexBufferHandle handle, IntConsumer releaser) {
        releasePersistentPair(handle, null, releaser);
    }

    private static void releasePersistentPair(VertexIndexBufferHandle handle, Throwable primary,
        IntConsumer releaser) {
        if (handle == null) {
            return;
        }
        int vertexId = handle.vertexId;
        int indexId = handle.indexId;
        int patchIndexId = handle.patchIndexId;
        handle.vertexId = -1;
        handle.indexId = -1;
        handle.patchIndexId = -1;
        handle.patchIndexCount = 0;
        Throwable failure = primary;
        if (vertexId >= 0) {
            try {
                releaser.accept(vertexId);
            } catch (RuntimeException | Error releaseFailure) {
                if (failure == null) {
                    failure = releaseFailure;
                } else {
                    failure.addSuppressed(releaseFailure);
                }
            }
        }
        if (indexId >= 0) {
            try {
                releaser.accept(indexId);
            } catch (RuntimeException | Error releaseFailure) {
                if (failure == null) {
                    failure = releaseFailure;
                } else {
                    failure.addSuppressed(releaseFailure);
                }
            }
        }
        if (patchIndexId >= 0) {
            try {
                releaser.accept(patchIndexId);
            } catch (RuntimeException | Error releaseFailure) {
                if (failure == null) failure = releaseFailure;
                else failure.addSuppressed(releaseFailure);
            }
        }
        if (primary == null && failure != null) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) failure;
        }
    }

    public static native void updateOverlayPostUniform(long ptr);

    public static void updateOverlayPostUniform(float radius) {
        try (MemoryStack stack = stackPush()) {
            int size = 160;
            ByteBuffer bb = stack.calloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            float[] blurDir = {1.0f, 1.0f};
            for (int i = 0; i < 2; i++) {
                bb.putFloat(baseAddr, blurDir[i]);
                baseAddr += Float.BYTES;
            }

            bb.putFloat(baseAddr, radius);
            baseAddr += Float.BYTES;

            float radiusMultiplier = 1.0f;
            bb.putFloat(baseAddr, radiusMultiplier);

            updateOverlayPostUniform(addr);
        }
    }

    public static native void updateDiagramPostUniform(long ptr);

    public static void updateDiagramPostUniform(int x, int y, int width, int height,
        float paletteOffset, float fadeScale, int lineColor, int lineShadowColor,
        int logicalWidth, int logicalHeight) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer bb = stack.calloc(160);
            long addr = memAddress(bb);
            new Matrix4f().get(0, bb);

            int baseAddr = 96;
            bb.putFloat(baseAddr, x);
            bb.putFloat(baseAddr + 4, y);
            bb.putFloat(baseAddr + 8, width);
            bb.putFloat(baseAddr + 12, height);

            putColor(bb, baseAddr + 16, lineColor);
            putColor(bb, baseAddr + 32, lineShadowColor);

            bb.putFloat(baseAddr + 48, paletteOffset);
            bb.putFloat(baseAddr + 52, fadeScale);
            bb.putFloat(baseAddr + 56, logicalWidth);
            bb.putFloat(baseAddr + 60, logicalHeight);
            updateDiagramPostUniform(addr);
        }
    }

    private static void putColor(ByteBuffer buffer, int offset, int color) {
        buffer.putFloat(offset, ((color >> 16) & 0xFF) / 255.0F);
        buffer.putFloat(offset + 4, ((color >> 8) & 0xFF) / 255.0F);
        buffer.putFloat(offset + 8, (color & 0xFF) / 255.0F);
        buffer.putFloat(offset + 12, 1.0F);
    }

    public static native void updateWorldUniform(long ptr);

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID,
        ClientLevel world, int endSkyTextureID, int endPortalTextureID, int lightMapTextureID) {
        try (MemoryStack stack = stackPush()) {
            int size = 704;
            ByteBuffer bb = stack.malloc(size);
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

            float gameTime = RenderSystem.getShaderGameTime();
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES; // skip seed

            RenderStateShard.setupGlintTexturing(0.16F);
            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;
            RenderSystem.resetTextureMatrix();

            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, camera.isDetached() ? 0 : 1);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, RenderSystem.getShaderFogStart());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, RenderSystem.getShaderFogEnd());
            baseAddr += Float.BYTES;

            float[] fogColor = RenderSystem.getShaderFogColor();
            bb.putFloat(baseAddr, fogColor[0]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[1]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[2]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[3]);
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, RenderSystem.getShaderFogShape().getIndex());
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.effects().skyType().ordinal());
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, RenderSystem.getShaderGlintAlpha());
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

            updateWorldUniform(addr);
        }
    }

    public static native void updateSkyUniform(long ptr);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
        Vector3f sunDirection, int skyType, boolean sunRisingOrSetting, boolean skyDark,
        boolean hasBlindnessOrDarkness, int submersionType, int moonPhase, float rainGradient,
        float starBrightness, int sunTextureID, int moonTextureID) {
        try (MemoryStack stack = stackPush()) {
            int size = 80;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            bb.putFloat(baseAddr, baseColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorB);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, skyType);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, horizonColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorA);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, sunDirection.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.z);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunRisingOrSetting ? 1 : 0);
            baseAddr += Integer.BYTES;

            bb.putInt(baseAddr, skyDark ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, hasBlindnessOrDarkness ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, submersionType);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonPhase);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, rainGradient);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonTextureID);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, starBrightness);

            updateSkyUniform(addr);
        }
    }

    public static native void updateMapping(long ptr);

    public static void updateMapping() {
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int size = elementCount * Integer.BYTES * 3;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, -1, size);
            IntBuffer intView = bb.asIntBuffer();

            for (Map.Entry<Integer, Integer> specularEntry : TextureTracker.GLID2SpecularGLID.entrySet()) {
                int sourceID = specularEntry.getKey();
                int targetID = specularEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3, targetID);
                } else {
                    throw new RuntimeException(
                        "Specular mapping sourceID " + sourceID + " out of index [0, " + (
                            elementCount - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> normalEntry : TextureTracker.GLID2NormalGLID.entrySet()) {
                int sourceID = normalEntry.getKey();
                int targetID = normalEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 1, targetID);
                } else {
                    throw new RuntimeException(
                        "Normal mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> flagEntry : TextureTracker.GLID2FlagGLID.entrySet()) {
                int sourceID = flagEntry.getKey();
                int targetID = flagEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 2, targetID);
                } else {
                    throw new RuntimeException(
                        "Flag mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            updateMapping(addr);
        }
    }

    public static void updateEmission() {
        // Emission tiles are uploaded immediately through TextureProxy during texture upload.
    }

    public record BufferInfo(ByteBuffer buf, long addr, int size) {

    }

    public static class VertexIndexBufferHandle {

        public int vertexId;
        public int indexId;
        public int patchIndexId;
        public int patchIndexCount;

        public VertexIndexBufferHandle(int vertexId, int indexId) {
            this(vertexId, indexId, -1, 0);
        }

        public VertexIndexBufferHandle(int vertexId, int indexId, int patchIndexId,
            int patchIndexCount) {
            this.vertexId = vertexId;
            this.indexId = indexId;
            this.patchIndexId = patchIndexId;
            this.patchIndexCount = patchIndexCount;
        }
    }
}
