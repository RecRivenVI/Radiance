package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps Veil integration policy behind the compatibility layer as new hooks are added. */
class VeilAdapterBoundaryTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path VEIL_MIXINS = MAIN.resolve(Path.of("com", "radiance", "mixins",
        "compatibility", "veil"));
    private static final Path VEIL_COMPAT = MAIN.resolve(Path.of("com", "radiance",
        "compatibility", "veil"));
    private static final Path COMPATIBILITY = MAIN.resolve(Path.of("com", "radiance",
        "compatibility"));

    @Test
    void veilMixinsDoNotReachIntoRendererImplementation() throws IOException {
        List<String> violations = findJavaSources(VEIL_MIXINS).stream()
            .filter(path -> {
                try {
                    String source = Files.readString(path);
                    return source.contains("com.radiance.client.proxy.vulkan.")
                        || source.contains("com.radiance.client.render.");
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .map(Path::toString)
            .toList();

        assertTrue(violations.isEmpty(),
            "Veil mixins must delegate renderer work to compatibility adapters: " + violations);
    }

    @Test
    void nonVeilPackagesUseOnlyTheStableFacade() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : findJavaSources(MAIN)) {
            if (path.startsWith(VEIL_COMPAT) || path.startsWith(VEIL_MIXINS)) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.startsWith("import com.radiance.compatibility.veil.")
                    && !trimmed.equals(
                        "import com.radiance.compatibility.veil.VeilAdapter;")) {
                    violations.add(path + ":" + lineNumber + " " + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "Code outside the Veil integration must use VeilAdapter: " + violations);
    }

    @Test
    void crossModCompatibilityDoesNotCallVeilDirectly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : findJavaSources(COMPATIBILITY)) {
            if (path.startsWith(VEIL_COMPAT)) {
                continue;
            }
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                if (line.contains("foundry.veil")) {
                    violations.add(path + ":" + lineNumber + " " + line.trim());
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "Cross-mod compatibility must reach Veil through VeilAdapter: " + violations);
    }

    @Test
    void veilBehaviorIsDrivenByCallsRatherThanInstalledModChecks() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(VEIL_COMPAT, VEIL_MIXINS)) {
            for (Path path : findJavaSources(root)) {
                int lineNumber = 0;
                for (String line : Files.readAllLines(path)) {
                    lineNumber++;
                    if (line.contains("ModList") || line.contains(".isLoaded(")
                        || line.contains(".getResource(")) {
                        violations.add(path + ":" + lineNumber + " " + line.trim());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "Veil behavior must translate real calls; presence checks may only gate mixin loading: "
                + violations);
    }

    @Test
    void screenEffectTranslationStartsAtTheActualVeilDrawCall() throws IOException {
        String runtime = Files.readString(VEIL_COMPAT.resolve("VeilRuntimeAdapter.java"));
        String adapter = Files.readString(VEIL_COMPAT.resolve("VeilScreenEffectAdapter.java"));
        String vanillaMixin = Files.readString(MAIN.resolve(Path.of("com", "radiance", "mixins",
            "vulkan_render_integration", "ScreenEffectRendererMixins.java")));

        assertTrue(runtime.contains("VeilScreenEffectAdapter.captureCurrentScreenQuad()"));
        assertTrue(adapter.contains("VeilShaderBridge.currentProgram()"));
        assertTrue(adapter.contains("TextureProxy.boundTexture(0)"));
        assertTrue(!vanillaMixin.contains("@Redirect(method = \"renderScreenEffect\""),
            "call-site prediction would bypass Veil before its real draw request");
    }

    private static List<Path> findJavaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
        }
    }
}
