package com.radiance.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.jarhandling.JarContents;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.neoforged.fml.loading.TransformerDiscovererConstants;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

/** Checks the real assembled distribution, not source-template spellings. */
final class DistributionMetadataTest {
    private static Path distribution() {
        return Path.of(System.getProperty("radiance.test.distribution"));
    }

    @Test
    void launcherMetadataMatchesGameDeclarationAndIcon() throws Exception {
        try (ZipFile outer = new ZipFile(distribution().toFile())) {
            JsonObject display;
            try (var reader =
                    new InputStreamReader(
                            outer.getInputStream(outer.getEntry("mcmod.info")),
                            StandardCharsets.UTF_8)) {
                var mods = JsonParser.parseReader(reader).getAsJsonArray();
                assertEquals(1, mods.size());
                display = mods.get(0).getAsJsonObject();
            }
            String gamePath;
            try (var input = outer.getInputStream(outer.getEntry("META-INF/radiance/game.path"))) {
                gamePath = new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
            }
            Config metadata = null;
            byte[] gameIcon = null;
            try (var nested = new ZipInputStream(outer.getInputStream(outer.getEntry(gamePath)))) {
                for (var entry = nested.getNextEntry();
                        entry != null;
                        entry = nested.getNextEntry()) {
                    if (entry.getName().equals("META-INF/neoforge.mods.toml")) {
                        metadata =
                                new TomlParser()
                                        .parse(
                                                new String(
                                                        nested.readAllBytes(),
                                                        StandardCharsets.UTF_8));
                    } else if (entry.getName().equals(display.get("logoFile").getAsString())) {
                        gameIcon = nested.readAllBytes();
                    }
                }
            }
            assertNotNull(metadata);
            List<Config> mods = metadata.get("mods");
            assertEquals(1, mods.size());
            Config game = mods.getFirst();
            for (var field : List.of("version", "logoFile")) {
                assertEquals((String) game.get(field), display.get(field).getAsString());
            }
            assertEquals((String) game.get("modId"), display.get("modid").getAsString());
            assertEquals((String) game.get("displayName"), display.get("name").getAsString());
            assertEquals(
                    ((String) game.get("description")).trim(),
                    display.get("description").getAsString());
            assertEquals((String) game.get("displayURL"), display.get("url").getAsString());
            assertEquals((String) metadata.get("license"), display.get("license").getAsString());
            assertEquals(
                    (String) metadata.get("issueTrackerURL"),
                    display.get("issueTrackerURL").getAsString());
            var authors =
                    display.getAsJsonArray("authorList").asList().stream()
                            .map(e -> e.getAsString())
                            .toList();
            assertEquals((String) game.get("authors"), String.join(", ", authors));
            List<Config> dependencies = metadata.get("dependencies.radiance");
            Config neoForge =
                    dependencies.stream()
                            .filter(c -> "neoforge".equals(c.get("modId")))
                            .findFirst()
                            .orElseThrow();
            String declaredRange = neoForge.get("versionRange");
            assertEquals(System.getProperty("radiance.test.neoforgeRange"), declaredRange);
            var range = VersionRange.createFromVersionSpec(declaredRange);
            for (String accepted :
                    List.of(
                            "21.1.62",
                            "21.1.228",
                            "21.1.250",
                            "21.1.251",
                            "21.1.252",
                            "21.2.0",
                            "99.0.0",
                            System.getProperty("radiance.test.neoforge"))) {
                assertTrue(range.containsVersion(new DefaultArtifactVersion(accepted)), accepted);
            }
            for (String rejected : List.of("21.1.61", "21.1.22", "21.1.1", "21.0.167")) {
                assertFalse(range.containsVersion(new DefaultArtifactVersion(rejected)), rejected);
            }
            Config minecraft =
                    dependencies.stream()
                            .filter(c -> "minecraft".equals(c.get("modId")))
                            .findFirst()
                            .orElseThrow();
            var minecraftRange = VersionRange.createFromVersionSpec(minecraft.get("versionRange"));
            assertTrue(minecraftRange.containsVersion(new DefaultArtifactVersion("1.21.1")));
            assertFalse(minecraftRange.containsVersion(new DefaultArtifactVersion("1.21.2")));
            assertFalse(minecraftRange.containsVersion(new DefaultArtifactVersion("1.21.4")));
            assertNotNull(gameIcon);
            try (var icon =
                    outer.getInputStream(outer.getEntry(display.get("logoFile").getAsString()))) {
                assertArrayEquals(gameIcon, icon.readAllBytes());
            }
        }
    }

    @Test
    void displayFacadePreservesServiceClassificationAndDoesNotDeclareSecondGameMod()
            throws Exception {
        // The pinned NeoForge service classifier decides this before normal GAME discovery.
        assertTrue(TransformerDiscovererConstants.shouldLoadInServiceLayer(distribution()));
        try (var contents = JarContents.of(distribution())) {
            assertTrue(contents.findFile("mcmod.info").isPresent());
            assertTrue(contents.findFile("META-INF/neoforge.mods.toml").isEmpty());
            assertNull(
                    JarModsDotTomlModFileReader.createModFile(
                            contents, ModFileDiscoveryAttributes.DEFAULT));
        }
    }

    @Test
    void optionalCompatibilityPinsMatchParentAndEmbeddedModDeclarations() throws Exception {
        Config metadata = null;
        try (ZipFile outer = new ZipFile(distribution().toFile())) {
            String gamePath;
            try (var input = outer.getInputStream(outer.getEntry("META-INF/radiance/game.path"))) {
                gamePath = new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
            }
            try (var game = new ZipInputStream(outer.getInputStream(outer.getEntry(gamePath)))) {
                for (var entry = game.getNextEntry(); entry != null; entry = game.getNextEntry()) {
                    if (entry.getName().equals("META-INF/neoforge.mods.toml")) {
                        metadata =
                                new TomlParser()
                                        .parse(
                                                new String(
                                                        game.readAllBytes(),
                                                        StandardCharsets.UTF_8));
                        break;
                    }
                }
            }
        }
        assertNotNull(metadata);
        List<Config> dependencies = metadata.get("dependencies.radiance");
        assertEquals(
                Set.of("minecraft", "neoforge"),
                dependencies.stream()
                        .filter(c -> "required".equals(c.get("type")))
                        .map(c -> (String) c.get("modId"))
                        .collect(Collectors.toSet()));
        var pins =
                dependencies.stream()
                        .filter(c -> "optional".equals(c.get("type")))
                        .collect(Collectors.toMap(c -> (String) c.get("modId"), c -> c));
        assertEquals(10, pins.size());
        assertFalse(dependencies.stream().anyMatch(c -> "zume".equals(c.get("modId"))));

        Map<String, String> published = new HashMap<>();
        for (String artifact :
                System.getProperty("radiance.test.compatibilityArtifacts")
                        .split(Pattern.quote(File.pathSeparator))) {
            try (var input = java.nio.file.Files.newInputStream(Path.of(artifact))) {
                collectModVersions(input, published);
            }
        }
        assertEquals(
                published.keySet(),
                pins.keySet(),
                "Pin actual mod IDs, not library or filename identities");
        for (var entry : published.entrySet()) {
            Config pin = pins.get(entry.getKey());
            assertEquals("[" + entry.getValue() + "]", pin.get("versionRange"), entry.getKey());
            assertEquals("CLIENT", pin.get("side"));
            assertEquals("NONE", pin.get("ordering"));
            var range = VersionRange.createFromVersionSpec(pin.get("versionRange"));
            assertTrue(range.containsVersion(new DefaultArtifactVersion(entry.getValue())));
            assertFalse(range.containsVersion(new DefaultArtifactVersion("0.0.1")));
            assertFalse(range.containsVersion(new DefaultArtifactVersion("99.0.0")));
        }
    }

    private static void collectModVersions(InputStream archive, Map<String, String> versions)
            throws Exception {
        try (var zip = new ZipInputStream(archive)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("META-INF/neoforge.mods.toml")) {
                    Config metadata =
                            new TomlParser()
                                    .parse(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                    List<Config> mods = metadata.get("mods");
                    for (Config mod : mods) {
                        String id = mod.get("modId"), version = mod.get("version");
                        String previous = versions.putIfAbsent(id, version);
                        assertTrue(
                                previous == null || previous.equals(version),
                                "Conflicting supplied versions of " + id);
                    }
                } else if (entry.getName().startsWith("META-INF/jarjar/")
                        && entry.getName().endsWith(".jar")) {
                    collectModVersions(new ByteArrayInputStream(zip.readAllBytes()), versions);
                }
            }
        }
    }
}
