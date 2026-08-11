package com.radiance.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies the nested normal Radiance mod through NeoForge's public discovery pipeline.
 */
public final class RadianceModFileCandidateLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadianceBootstrap");
    private static final String GAME_HASH = "/META-INF/radiance/game.sha256";
    private static final String GAME_PATH = "/META-INF/radiance/game.path";
    private static final Pattern GAME_PATH_PATTERN =
            Pattern.compile("META-INF/radiance/[A-Za-z0-9._-]+\\.jar");

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        String launchTarget = context.environment().getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
                .orElse(null);
        if (!RadianceGraphicsBootstrapper.isClientLaunchTarget(launchTarget)) {
            LOGGER.debug("Skipping the embedded Radiance GAME mod for launch target {}", launchTarget);
            return;
        }
        FaceStateLaunchPlugin.enableForClient();
        String expected = readMetadata(GAME_HASH);
        if (expected == null) {
            if (isDevelopmentDirectory()) {
                LOGGER.debug("No embedded GAME JAR in the development SERVICE source set; MOD_CLASSES supplies it");
                return;
            }
            throw new IllegalStateException(
                    "Packaged Radiance SERVICE JAR is missing META-INF/radiance/game.sha256");
        }
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Invalid embedded Radiance GAME JAR hash");
        }
        String gamePath = readMetadata(GAME_PATH);
        if (gamePath == null || !GAME_PATH_PATTERN.matcher(gamePath).matches()) {
            throw new IllegalStateException(
                    "Packaged Radiance SERVICE JAR is missing a valid META-INF/radiance/game.path");
        }

        Path gameDirectory = BootstrapResources.gameDirectory(context);
        Path target = gameDirectory.resolve(".radiance").resolve("bootstrap")
                .resolve(expected).resolve(gamePath.substring(gamePath.lastIndexOf('/') + 1));
        try {
            BootstrapResources.copyVerifiedResource("/" + gamePath, target, expected);
            if (!context.addLocated(target)) {
                LOGGER.debug("Embedded Radiance GAME JAR was already located: {}", target);
                return;
            }
            var attributes = ModFileDiscoveryAttributes.DEFAULT.withLocator(this);
            var result = pipeline.addJarContent(JarContents.of(target), attributes,
                    IncompatibleFileReporting.ERROR);
            if (result.isEmpty()) {
                throw new IllegalStateException("NeoForge rejected the embedded Radiance GAME JAR " + target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to materialize the embedded Radiance GAME JAR", e);
        }
    }

    private static String readMetadata(String resource) {
        try (InputStream stream = RadianceModFileCandidateLocator.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + resource, e);
        }
    }

    private static boolean isDevelopmentDirectory() {
        try {
            var source = RadianceModFileCandidateLocator.class.getProtectionDomain()
                    .getCodeSource();
            return source != null && Files.isDirectory(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "Radiance embedded GAME mod locator";
    }
}
