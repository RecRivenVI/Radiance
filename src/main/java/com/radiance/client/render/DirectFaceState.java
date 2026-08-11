package com.radiance.client.render;

/**
 * Targets of translated GL11/GL11C call sites. No context creation or state
 * query.
 */
public final class DirectFaceState {
    private DirectFaceState() {}
    public static void glCullFace(int mode) {
        MaterialFaces.cullFace(mode);
    }
    public static void glFrontFace(int winding) {
        MaterialFaces.frontFace(winding);
    }
    public static void glEnable(int capability) {
        setEnabled(capability, true, DirectFaceState::applyCapability);
    }
    public static void glDisable(int capability) {
        setEnabled(capability, false, DirectFaceState::applyCapability);
    }

    // Test seam for the actual translated call path. Unknown capabilities fail in
    // Java: falling back to LWJGL is never legal with GLFW_NO_API.
    static void setEnabled(int capability, boolean enabled,
        java.util.function.BiConsumer<Integer, Boolean> sink) {
        switch (capability) {
            case 2884, 2960, 2929, 3042, 3089, 3058, 32823, 10754 ->
                sink.accept(capability, enabled);
            default -> throw new UnsupportedOperationException(
                "Unsupported direct GL capability under Vulkan: " + capability);
        }
    }

    private static void applyCapability(int capability, boolean enabled) {
        switch (capability) {
            case 2884 -> MaterialFaces.enabled(enabled);
            case 2960 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.DepthStencilState.setStencilTestEnable(enabled);
            case 2929 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.DepthStencilState.setDepthTestEnable(enabled);
            case 3042 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.ColorBlendState.setBlendEnable(enabled);
            case 3089 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.ViewportState.setScissorEnabled(enabled);
            case 3058 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(enabled);
            case 32823 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(6914, enabled);
            case 10754 -> com.radiance.client.proxy.vulkan.PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(6913, enabled);
            default -> throw new AssertionError("Unclassified capability " + capability);
        }
    }
}
