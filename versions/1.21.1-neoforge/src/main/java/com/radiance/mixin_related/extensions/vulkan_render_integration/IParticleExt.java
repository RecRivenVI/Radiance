package com.radiance.mixin_related.extensions.vulkan_render_integration;

public interface IParticleExt {
    String radiance$getContentName();

    void radiance$setContentName(String contentName);

    int radiance$getLightColor(float tickDelta);
}
