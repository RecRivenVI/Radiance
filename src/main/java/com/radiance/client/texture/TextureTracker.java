package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.constant.VulkanConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public class TextureTracker {

    public static Map<ResourceLocation, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2FlagGLID = new ConcurrentHashMap<>();

    public static void release(int textureId) {
        textureID2GLID.entrySet().removeIf(entry -> entry.getValue() == textureId);
        GLID2Texture.remove(textureId);
        removeAuxiliaryReference(GLID2SpecularGLID, textureId);
        removeAuxiliaryReference(GLID2NormalGLID, textureId);
        removeAuxiliaryReference(GLID2FlagGLID, textureId);
    }

    private static void removeAuxiliaryReference(Map<Integer, Integer> mapping, int textureId) {
        mapping.entrySet().removeIf(entry -> entry.getKey() == textureId || entry.getValue() == textureId);
    }

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, NativeImage.InternalGlFormat format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer);
        }

        private static int getChannel(NativeImage.InternalGlFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case RG -> 2;
                case RED -> 1;
                default -> throw new IllegalArgumentException(
                    "Unknown internal format: " + internalFormat);
            };
        }

        private static VulkanConstants.VkFormat getFormat(
            NativeImage.InternalGlFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case RG -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RED -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }
    }
}
