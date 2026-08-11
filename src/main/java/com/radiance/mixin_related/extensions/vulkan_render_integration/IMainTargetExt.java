package com.radiance.mixin_related.extensions.vulkan_render_integration;

public interface IMainTargetExt {
    void radiance$initializeDefaultTarget(int width, int height, boolean clear, boolean clearError);
}
