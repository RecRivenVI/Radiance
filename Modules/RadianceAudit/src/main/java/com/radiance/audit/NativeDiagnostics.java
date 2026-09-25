package com.radiance.audit;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;

/** Optional local collector. No vendor DLL is packaged and no library is searched by bare name. */
public final class NativeDiagnostics {
    private static boolean initialized;
    private static boolean attached;
    private static boolean lifecycle;
    private NativeDiagnostics() {}
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        if (!AuditConfiguration.enabled()) return;
        String library = System.getProperty("radiance.audit.nativeLibrary", "");
        boolean requested = Boolean.getBoolean("radiance.audit.nativePerformance")
            || Boolean.getBoolean("radiance.audit.capture") || ExperimentAccess.permitted() || FrameProfiler.requested();
        if (library.isBlank() && !requested) return;
        try {
            Path path = library.isBlank() ? extractBundledLibrary() : Path.of(library);
            if (!path.isAbsolute()) throw new IllegalArgumentException("Native audit library path must be absolute");
            System.load(path.normalize().toString());
            int flags = Boolean.getBoolean("radiance.audit.nativePerformance") ? 3 : 0;
            if (Boolean.getBoolean("radiance.audit.capture")) flags |= 4;
            if (ExperimentAccess.permitted()) flags |= 8;
            if (!install(flags)) throw new IllegalStateException("MCVR audit ABI mismatch or collector already installed");
            attached = true;
            lifecycle = (flags & 8) != 0;
            LogUtils.getLogger().info("Native diagnostic collector attached: flags={} library={}", flags, path);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("Native diagnostic collector unavailable; normal rendering unchanged", failure);
        }
    }
    static boolean lifecycleEnabled() { return lifecycle; }
    private static Path extractBundledLibrary() throws java.io.IOException, java.security.NoSuchAlgorithmException {
        try (var input = NativeDiagnostics.class.getResourceAsStream(
                "/META-INF/natives/windows-x86_64/radiance-audit-native.dll")) {
            if (input == null) throw new java.io.IOException("No native diagnostic collector for this platform");
            byte[] bytes = input.readAllBytes();
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            Path directory = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath().resolve("radiance-audit/runtime").resolve(hash);
            java.nio.file.Files.createDirectories(directory);
            Path library = directory.resolve("radiance-audit-native.dll");
            if (!java.nio.file.Files.exists(library)) java.nio.file.Files.write(library, bytes,
                java.nio.file.StandardOpenOption.CREATE_NEW);
            if (!java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(library)))
                throw new java.io.IOException("Diagnostic runtime hash mismatch: " + library);
            return library;
        }
    }
    public static void poll() {
        if (!attached) return;
        String report = drain();
        if (report != null) LogUtils.getLogger().info("[Radiance Audit/native] {}", report);
    }
    private static native String drain();
    private static native boolean install(int flags);
    static synchronized boolean attachProfiler() {
        if (!attached) initialized = false; // An explicit capture may load the collector after passive startup.
        initialize();
        return attached && installProfile();
    }
    private static native boolean installProfile();
    static native void profileFrame(long frame, boolean active);
    static native String drainProfile();
}
