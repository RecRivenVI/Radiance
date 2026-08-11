package com.radiance.client.proxy.vulkan;

/** Small, native-independent policy used by the process-fatal Minecraft exit boundary. */
final class RendererShutdown {
    private RendererShutdown() {}

    static Throwable closeIfInitialized(boolean initialized, Runnable close) {
        if (!initialized) return null;
        try {
            close.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
