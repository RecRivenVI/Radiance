package com.radiance.client.proxy.vulkan;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import com.radiance.client.texture.EmissionRecorder;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryUtil;

public class TextureProxy {

    public static final TextureTasks TASKS = new TextureTasks(TextureProxy.class);

    private static final int GL_TEXTURE0 = 33984;
    private static final int[] boundTextureIds = createUnboundTextureUnits();
    private static final long[] legacyBindingSequences = new long[boundTextureIds.length];
    private static final long[] shaderBindingSequences = new long[boundTextureIds.length];
    private static long bindingSequence;
    private static int activeTextureUnit;

    private static int[] createUnboundTextureUnits() {
        int[] textureIds = new int[32];
        Arrays.fill(textureIds, -1);
        return textureIds;
    }

    private record EmissionTileKey(int textureId, long tileKey) {
    }

    private static final Map<EmissionTileKey, EmissionRecorder.TileUpdate> emissionTileCache =
        new ConcurrentHashMap<>();

    private static native int generateTextureIdNative();

    public static synchronized int generateTextureId() {
        int id = generateTextureIdNative();
        TASKS.register(id);
        return id;
    }

    private synchronized static native void releaseTextureIdNative(int id, int fallbackId);

    public static synchronized void releaseTextureId(int id, int fallbackId) {
        releaseTextureId(TASKS.owner(id), fallbackId);
    }

    public static synchronized void releaseTextureId(TextureTasks.Owner owner, int fallbackId) {
        if (owner == null) return;
        int id = owner.id();
        if (id == fallbackId) {
            return;
        }
        TASKS.release(owner, () -> {
            clearEmissionTiles(id);
            TextureTracker.release(id);
            for (int unit = 0; unit < boundTextureIds.length; unit++)
                if (boundTextureIds[unit] == id) boundTextureIds[unit] = -1;
            releaseTextureIdNative(id, fallbackId);
        });
    }

    public static synchronized void activeTexture(int textureUnit) {
        int index = textureUnit - GL_TEXTURE0;
        if (index < 0 || index >= boundTextureIds.length) {
            throw new IllegalArgumentException("Unsupported texture unit: " + textureUnit);
        }
        activeTextureUnit = index;
    }

    public static synchronized void bindTexture(int textureId) {
        boundTextureIds[activeTextureUnit] = textureId;
        legacyBindingSequences[activeTextureUnit] = ++bindingSequence;
    }

    public static synchronized int boundTexture() {
        return boundTextureIds[activeTextureUnit];
    }

    /**
     * Returns the texture selected on an OpenGL-style texture unit.  Some external shader
     * frameworks still bind textures through {@code activeTexture}/{@code bindTexture} instead
     * of Minecraft's {@code RenderSystem.setShaderTexture} slots.  Radiance mirrors those calls
     * even without an OpenGL context, so Vulkan shader bridges can consume the same state.
     */
    public static synchronized int boundTexture(int textureUnit) {
        if (textureUnit < 0 || textureUnit >= boundTextureIds.length) {
            return 0;
        }
        return boundTextureIds[textureUnit];
    }

    /** Records a Minecraft shader-slot write after RenderSystem has resolved the texture id. */
    public static synchronized void shaderTexture(int textureUnit) {
        if (textureUnit < 0 || textureUnit >= shaderBindingSequences.length) {
            return;
        }
        shaderBindingSequences[textureUnit] = ++bindingSequence;
    }

    /**
     * Resolves the texture visible to a sampler when both Minecraft shader slots and legacy
     * OpenGL-style texture units are in use. The most recent writer owns the slot, matching the
     * single texture-unit state an OpenGL-backed client would expose to external renderers.
     */
    public static synchronized int effectiveTexture(int textureUnit, int shaderTextureId) {
        if (textureUnit < 0 || textureUnit >= boundTextureIds.length) {
            return shaderTextureId;
        }
        return chooseEffectiveTexture(boundTextureIds[textureUnit],
            legacyBindingSequences[textureUnit], shaderTextureId,
            shaderBindingSequences[textureUnit]);
    }

    static int chooseEffectiveTexture(int legacyTextureId, long legacySequence,
        int shaderTextureId, long shaderSequence) {
        if (legacySequence > shaderSequence && legacyTextureId >= 0) {
            return legacyTextureId;
        }
        if (shaderSequence > 0 || shaderTextureId > 0) {
            return shaderTextureId;
        }
        return legacyTextureId > 0 ? legacyTextureId : shaderTextureId;
    }

    private static native void prepareImageNative(int id, int mipLevels, int width,
        int height, int format);

    public static synchronized void prepareImage(int id, int mipLevels, int width, int height, int format) {
        TASKS.replaceImage(id);
        prepareImageNative(id, mipLevels, width, height, format);
    }

    public static void prepareImage(int id, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        clearEmissionTiles(id);
        prepareImage(id, mipLevels, width, height, format.getValue());
    }

    public synchronized static native void setFilter(int id, int samplingMode, int mipmapMode);
    public static native boolean isFramebufferTexture(int id);

    public synchronized static native void setClamp(int id, int addressMode);

    public synchronized static native void queueUpload(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level);

    public synchronized static native void downloadTexture(int id, int level, int width,
        int height, int channel, long dstPointer);

    private synchronized static native void uploadEmissionTileNative(int textureId, long tileKey,
        long cellsPtr, int cellCount);

    public static void uploadEmissionTile(EmissionRecorder.TileUpdate tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        emissionTileCache.put(new EmissionTileKey(tileUpdate.textureId, tileUpdate.tileKey),
            tileUpdate);
        if (!Options.collectChunkEmission) {
            return;
        }

        uploadEmissionTileToNative(tileUpdate);
    }

    public static void flushEmissionTiles() {
        if (!Options.collectChunkEmission) {
            return;
        }

        for (EmissionRecorder.TileUpdate tileUpdate : emissionTileCache.values()) {
            uploadEmissionTileToNative(tileUpdate);
        }
    }

    public static boolean hasEmissionTile(int textureId, long tileKey) {
        return emissionTileCache.containsKey(new EmissionTileKey(textureId, tileKey));
    }

    private static void clearEmissionTiles(int textureId) {
        emissionTileCache.keySet().removeIf(key -> key.textureId == textureId);
    }

    private static void uploadEmissionTileToNative(EmissionRecorder.TileUpdate tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        ByteBuffer cellsBuffer = null;
        try {
            int cellCount = tileUpdate.cells.size();
            long cellsAddr = 0L;
            if (cellCount > 0) {
                cellsBuffer = MemoryUtil.memAlloc(cellCount * 8 * Float.BYTES);
                int base = 0;
                for (EmissionRecorder.EmissionCell cell : tileUpdate.cells) {
                    cellsBuffer.putFloat(base, cell.u0);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.v0);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.u1);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.v1);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgEmission);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgR);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgG);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgB);
                    base += Float.BYTES;
                }
                cellsAddr = memAddress(cellsBuffer);
            }

            uploadEmissionTileNative(tileUpdate.textureId, tileUpdate.tileKey, cellsAddr, cellCount);
        } finally {
            if (cellsBuffer != null) {
                MemoryUtil.memFree(cellsBuffer);
            }
        }
    }

    public static void prepareImage(NativeImage.InternalGlFormat internalFormat, int id,
        int mipLevels, int width, int height) {
        switch (internalFormat) {
            case RGBA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
                break;
            case RGB:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM);
                break;
            case RG:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM);
                break;
            case RED:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM);
                break;
        }
    }

}
