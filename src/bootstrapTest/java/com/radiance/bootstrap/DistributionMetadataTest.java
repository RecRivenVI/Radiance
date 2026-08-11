package com.radiance.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.jarhandling.JarContents;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.neoforged.fml.loading.TransformerDiscovererConstants;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
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
            try (var reader = new InputStreamReader(outer.getInputStream(outer.getEntry("mcmod.info")),
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
                for (var entry = nested.getNextEntry(); entry != null; entry = nested.getNextEntry()) {
                    if (entry.getName().equals("META-INF/neoforge.mods.toml")) {
                        metadata = new TomlParser().parse(new String(nested.readAllBytes(), StandardCharsets.UTF_8));
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
            assertEquals(((String) game.get("description")).trim(), display.get("description").getAsString());
            assertEquals((String) game.get("displayURL"), display.get("url").getAsString());
            assertEquals((String) metadata.get("license"), display.get("license").getAsString());
            assertEquals((String) metadata.get("issueTrackerURL"), display.get("issueTrackerURL").getAsString());
            var authors = display.getAsJsonArray("authorList").asList().stream().map(e -> e.getAsString()).toList();
            assertEquals((String) game.get("authors"), String.join(", ", authors));
            List<Config> dependencies = metadata.get("dependencies.radiance");
            Config neoForge = dependencies.stream().filter(c -> "neoforge".equals(c.get("modId"))).findFirst().orElseThrow();
            assertEquals("[" + System.getProperty("radiance.test.neoforge") + "]", neoForge.get("versionRange"));
            assertNotNull(gameIcon);
            try (var icon = outer.getInputStream(outer.getEntry(display.get("logoFile").getAsString()))) {
                assertArrayEquals(gameIcon, icon.readAllBytes());
            }
        }
    }

    @Test
    void displayFacadePreservesServiceClassificationAndDoesNotDeclareSecondGameMod() throws Exception {
        // The pinned NeoForge service classifier decides this before normal GAME discovery.
        assertTrue(TransformerDiscovererConstants.shouldLoadInServiceLayer(distribution()));
        try (var contents = JarContents.of(distribution())) {
            assertTrue(contents.findFile("mcmod.info").isPresent());
            assertTrue(contents.findFile("META-INF/neoforge.mods.toml").isEmpty());
            assertNull(JarModsDotTomlModFileReader.createModFile(contents, ModFileDiscoveryAttributes.DEFAULT));
        }
    }
}
