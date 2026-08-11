package com.radiance.bootstrap;

import java.nio.file.Path;

/**
 * Public, loader-neutral bridge from the GAME module to the SERVICE bootstrap.
 */
public final class BootstrapState {
    private static volatile RadianceImmediateWindowProvider provider;

    private BootstrapState() {}

    static void register(RadianceImmediateWindowProvider value) {
        provider = value;
    }

    public static boolean isInitialized() {
        RadianceImmediateWindowProvider current = provider;
        return current != null && current.isNativeInitialized();
    }

    public static RadianceImmediateWindowProvider requireProvider() {
        RadianceImmediateWindowProvider current = provider;
        if (current == null) {
            throw new IllegalStateException("Radiance SERVICE bootstrap is not active");
        }
        return current;
    }

    public static Path runtimeDirectory() {
        Path path = BootstrapResources.runtimeDirectory();
        if (path == null) {
            throw new IllegalStateException("Radiance runtime has not been extracted");
        }
        return path;
    }

    /**
     * Extracts the shared runtime and loads its native library once without creating an
     * early window. GAME uses this path when earlyWindowControl is disabled.
     */
    public static Path prepareGameRuntime(Path gameDirectory) {
        if (gameDirectory == null) {
            throw new NullPointerException("gameDirectory");
        }
        return BootstrapResources.prepareAndLoad(gameDirectory.toAbsolutePath().normalize());
    }

    public static long windowHandle() {
        return requireProvider().windowHandle();
    }

    /** Runs the official early-window repaint hook while GAME waits for native initialization. */
    public static void tickLoading() {
        RadianceImmediateWindowProvider current = provider;
        if (current != null && current.isNativeInitialized()) current.periodicTick();
    }

    public static void bindGameNatives(Class<?> type, String[] names, String[] descriptors,
            String[] symbols) {
        NativeRuntime.bindGameNatives(type, names, descriptors, symbols);
    }
}
