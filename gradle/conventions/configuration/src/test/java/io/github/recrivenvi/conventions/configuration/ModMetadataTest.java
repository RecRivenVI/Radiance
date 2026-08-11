package io.github.recrivenvi.conventions.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModMetadataTest {
    @TempDir Path directory;

    private List<ModMetadata.Mod> read(Map<String, String> entries) throws IOException {
        Path jar = directory.resolve("input.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return ModMetadata.read(jar.toFile());
    }

    @Test
    void fabricIdAndVersionComeFromTheTopLevelObject() throws IOException {
        String json =
                """
                {
                  "schemaVersion": 1,
                  "description": "say \\"id\\": \\"fake\\" in C:\\\\mods\\\\",
                  "authors": ["id", {"name": "x", "id": "fake"}],
                  "custom": {"id": "fake", "version": "0"},
                  "accessWidener": null,
                  "environment": "*",
                  "id": "real",
                  "version": "1.0+mc26.3"
                }
                """;
        assertEquals(
                List.of(new ModMetadata.Mod("real", "1.0+mc26.3")),
                read(Map.of("fabric.mod.json", json)));
    }

    // 有的 NeoForge 模组附带一个空的 fabric.mod.json，它不声明模组。
    @Test
    void metadataWithoutModIdIsNotAMod() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "META-INF/neoforge.mods.toml",
                "[[mods]]\nmodId = \"real\"\nversion = \"2.0\"\n\n[[mods]]\nversion = \"3.0\"\n");
        entries.put("fabric.mod.json", "");
        assertEquals(List.of(new ModMetadata.Mod("real", "2.0")), read(entries));
        assertEquals(List.of(), read(Map.of("fabric.mod.json", "{\"version\": \"1.0\"}")));
    }

    @Test
    void forgeJarVersionComesFromTheManifest() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Version: 4.5\n");
        entries.put(
                "META-INF/mods.toml",
                "[[mods]]\nmodId = \"legacy\"\nversion = \"${file.jarVersion}\"\n");
        assertEquals(List.of(new ModMetadata.Mod("legacy", "4.5")), read(entries));
    }

    @Test
    void filesThatAreNotJarsDeclareNoMods() throws IOException {
        Path file = directory.resolve("notes.jar");
        Files.writeString(file, "not a zip");
        assertEquals(List.of(), ModMetadata.read(file.toFile()));
    }
}
