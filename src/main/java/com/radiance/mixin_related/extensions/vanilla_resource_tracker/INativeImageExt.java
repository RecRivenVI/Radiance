package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

public interface INativeImageExt {

    int radiance$getTargetID();

    void radiance$setTargetID(int id);

    com.radiance.client.texture.TextureTasks.Owner radiance$getTargetOwner();

    void radiance$setTargetOwner(com.radiance.client.texture.TextureTasks.Owner owner);

    ResourceLocation radiance$getIdentifier();

    void radiance$setIdentifier(ResourceLocation id);

    NativeImage radiance$getSpecularNativeImage();

    void radiance$setSpecularNativeImage(NativeImage image);

    NativeImage radiance$getNormalNativeImage();

    void radiance$setNormalNativeImage(NativeImage image);

    NativeImage radiance$getFlagNativeImage();

    void radiance$setFlagNativeImage(NativeImage image);
}
