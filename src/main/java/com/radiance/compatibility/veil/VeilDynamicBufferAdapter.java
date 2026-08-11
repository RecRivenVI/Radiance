package com.radiance.compatibility.veil;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import com.mojang.blaze3d.platform.TextureUtil;
import java.nio.IntBuffer;

/** Allocates Veil dynamic-buffer images in MCVR instead of OpenGL. */
public final class VeilDynamicBufferAdapter {
    private VeilDynamicBufferAdapter() {
    }

    public static void allocate(DynamicBufferType type, int textureId, int width, int height) {
        FramebufferProxy.prepareAttachmentTexture(textureId, 1, width, height,
            type.getInternalFormat());
        TextureProxy.setFilter(textureId, VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue(),
            VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        TextureProxy.setClamp(textureId,
            VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());
    }

    public static void allocateTextureIds(IntBuffer textures) {
        for (int i = textures.position(); i < textures.limit(); i++) {
            textures.put(i, TextureUtil.generateTextureId());
        }
    }

    public static void releaseTextureIds(IntBuffer textures) {
        for (int i = textures.position(); i < textures.limit(); i++) {
            TextureUtil.releaseTextureId(textures.get(i));
        }
    }
}
