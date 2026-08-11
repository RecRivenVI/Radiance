package com.radiance.client.proxy.vulkan;

/**
 * Hardware strings captured from NeoForge's real OpenGL early window before Radiance replaces
 * it. Minecraft's F3 screen historically reports these driver strings, so retaining them avoids
 * fabricating OpenGL-looking values from Vulkan device properties after the context is gone.
 */
public final class RendererDiagnostics {

    private static volatile String openGlVendor;
    private static volatile String openGlRenderer;
    private static volatile String openGlVersion;

    private RendererDiagnostics() {
    }

    public static void captureOpenGlStrings(String vendor, String renderer, String version) {
        if (vendor != null && !vendor.isBlank()) {
            openGlVendor = vendor;
        }
        if (renderer != null && !renderer.isBlank()) {
            openGlRenderer = renderer;
        }
        if (version != null && !version.isBlank()) {
            openGlVersion = version;
        }
    }

    public static String backendString(int name) {
        String captured = switch (name) {
            case 7936 -> openGlVendor;
            case 7937 -> openGlRenderer;
            case 7938 -> openGlVersion;
            default -> null;
        };
        return captured != null ? captured : RendererProxy.backendString(name);
    }
}
