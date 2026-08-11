package com.radiance.audit;

import com.radiance.api.audit.AuditRuntimeIdentity;
import com.radiance.client.RadianceClient;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.platform.RadiancePlatform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in lifecycle acceptance hook for isolated development instances.
 *
 * <p>The hook is disabled unless {@code RADIANCE_LIFECYCLE_CASE} names a supported case. A
 * runtime failure additionally requires a one-shot trigger file in the selected game directory,
 * so merely installing this build cannot inject a failure.</p>
 */
public final class LifecycleAcceptance {
    public enum Case {
        DISABLED,
        G0,
        G1_RUNTIME_FATAL,
        G2_BOUNDARY
    }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean TRIGGERED = new AtomicBoolean();
    private static volatile Case activeCase = Case.DISABLED;
    private static volatile Path report;
    private static volatile Path trigger;

    private LifecycleAcceptance() {}

    public static Case parseCase(String value) {
        if (value == null || value.isBlank()) return Case.DISABLED;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "G0" -> Case.G0;
            case "G1", "G1_RUNTIME_FATAL", "G1-RUNTIME-FATAL" -> Case.G1_RUNTIME_FATAL;
            case "G2", "G2_BOUNDARY", "G2-BOUNDARY" -> Case.G2_BOUNDARY;
            default -> Case.DISABLED;
        };
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        activeCase = parseCase(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_LIFECYCLE_CASE"));
        if (activeCase == Case.DISABLED) return;

        if (activeCase != Case.G0 && !NativeDiagnostics.lifecycleEnabled()) {
            RadianceClient.LOGGER.error("Lifecycle experiment not armed: matching native diagnostic collector is required");
            activeCase = Case.DISABLED;
            return;
        }
        Path gameDirectory = RadiancePlatform.gameDirectory().toAbsolutePath().normalize();
        Path directory = gameDirectory.resolve("lifecycle-acceptance");
        report = directory.resolve(activeCase.name().toLowerCase(Locale.ROOT) + "-"
                + ProcessHandle.current().pid() + ".log");
        trigger = gameDirectory.resolve("lifecycle-acceptance.trigger");
        append("CASE_ENABLED case=" + activeCase + " gameDirectory=" + gameDirectory);
        append("RUNTIME_PATH path=" + AuditRuntimeIdentity.runtimeDirectory());

        String probe = "JNI-UTF16-楠屾敹-\ud83d\udd27";
        String returned = RendererProxy.roundTripAcceptanceString(probe);
        if (!probe.equals(returned)) {
            throw new IllegalStateException("Lifecycle acceptance JNI UTF-16 round trip failed");
        }
        if (RendererProxy.roundTripAcceptanceString(null) != null) {
            throw new IllegalStateException("Lifecycle acceptance JNI null string probe failed");
        }
        append("JNI_UTF16_ROUND_TRIP result=PASS units=" + probe.length());
    }

    public static void poll() {
        if (activeCase != Case.G1_RUNTIME_FATAL && activeCase != Case.G2_BOUNDARY) return;
        Path triggerPath = trigger;
        if (triggerPath == null || !Files.isRegularFile(triggerPath)) return;
        if (!TRIGGERED.compareAndSet(false, true)) return;
        try {
            Files.delete(triggerPath);
        } catch (IOException error) {
            TRIGGERED.set(false);
            append("TRIGGER_CONSUME result=FAIL error=" + summarize(error));
            return;
        }

        append("TRIGGER_CONSUME result=PASS case=" + activeCase);
        if (activeCase == Case.G1_RUNTIME_FATAL) {
            runRuntimeFatal(false);
        } else {
            runRuntimeFatal(true);
        }
    }

    private static void runRuntimeFatal(boolean closeTwice) {
        Throwable original;
        try {
            RendererProxy.injectRuntimeFatalForAcceptance();
            throw new AssertionError("Native acceptance failure did not propagate to Java");
        } catch (Throwable failure) {
            original = failure;
            append("JAVA_RECEIVED type=" + failure.getClass().getName()
                    + " message=" + summarize(failure));
        }

        try {
            RendererProxy.probeNormalCallAfterFatal();
            append("POST_FATAL_REJECTION result=FAIL reason=probe-body-ran");
        } catch (Throwable expected) {
            append("POST_FATAL_REJECTION result=PASS type=" + expected.getClass().getName()
                    + " message=" + summarize(expected));
        }
        append("NATIVE_STATE phase=after-fatal value=" + RendererProxy.lifecycleAcceptanceState());

        if (closeTwice) {
            RendererProxy.close();
            RendererProxy.close();
            append("NATIVE_STATE phase=after-double-close value="
                    + RendererProxy.lifecycleAcceptanceState());
        }

        if (original instanceof RuntimeException runtime) throw runtime;
        if (original instanceof Error error) throw error;
        throw new RuntimeException(original);
    }

    public static void beforeRendererClose() {
        if (activeCase == Case.DISABLED) return;
        append("NATIVE_STATE phase=before-close value=" + safeNativeState());
    }

    public static void afterRendererClose() {
        if (activeCase == Case.DISABLED) return;
        append("NATIVE_STATE phase=after-close value=" + safeNativeState());
        append("RENDERER_CLOSE complete=true");
    }

    private static String safeNativeState() {
        try {
            return RendererProxy.lifecycleAcceptanceState();
        } catch (Throwable error) {
            return "unavailable type=" + error.getClass().getName() + " message=" + summarize(error);
        }
    }

    private static synchronized void append(String message) {
        Path output = report;
        if (output == null) return;
        String line = "[" + Instant.now() + "] [" + Thread.currentThread().getName()
                + "/INFO] [Radiance/LifecycleAcceptance]: " + message + System.lineSeparator();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            RadianceClient.LOGGER.error("Unable to append lifecycle acceptance report {}", output,
                    error);
        }
        RadianceClient.LOGGER.info("[LifecycleAcceptance] {}", message);
    }

    private static String summarize(Throwable error) {
        String message = error.getMessage();
        return (message == null ? "" : message).replace('\r', ' ').replace('\n', ' ');
    }
}
