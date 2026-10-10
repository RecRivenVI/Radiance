package io.github.recrivenvi.configuration;

import static io.github.recrivenvi.configuration.Repositories.NEOFORGE;
import static io.github.recrivenvi.configuration.Repositories.PROJECT;
import static io.github.recrivenvi.configuration.Repositories.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectFactsTest {
    private static RepositoryConfiguration project(String text) {
        return create(text, "", "");
    }

    private static String message(String text) {
        return assertThrows(ConfigurationException.class, () -> project(text)).getMessage();
    }

    @Test
    void metadataCombinesProjectAndTargetFacts() {
        Map<String, String> metadata = project(PROJECT).metadata(NEOFORGE);
        assertEquals("examplemod", metadata.get("mod_id"));
        assertEquals("26.2.0.1", metadata.get("loader_version"));
        assertEquals("26.2.0.1", metadata.get("loader_minimum"));
        assertEquals("26.2", metadata.get("minecraft_version"));
        assertEquals("neoforge", metadata.get("loader"));
        assertEquals(NEOFORGE, metadata.get("target"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mod_id",
                "mod_name",
                "mod_version",
                "mod_group",
                "mod_side",
                "mod_authors",
                "mod_license",
                "mod_description"
            })
    void everyProjectFactIsRequired(String key) {
        String text = PROJECT.replaceAll("(?m)^" + key + "=.*\\n", "");
        assertTrue(message(text).contains("[" + key + "]: 缺少必需的项目事实"));
    }

    @Test
    void projectFactsAreValidated() {
        assertTrue(message(PROJECT.replace("examplemod", "Example")).contains("[mod_id]"));
        assertTrue(
                message(PROJECT.replace("io.github.example", "io.github.Example"))
                        .contains("[mod_group]"));
        assertTrue(message(PROJECT.replace("0.1.0", "0.1 beta")).contains("[mod_version]"));
        assertTrue(
                message(PROJECT.replace("mod_side=both", "mod_side=common"))
                        .contains("[mod_side]"));
        assertTrue(message(PROJECT + "version=1\n").contains("[version]"));
        assertTrue(message(PROJECT + "mod_id=other\n").contains("[mod_id]: 重复的键"));
    }

    @Test
    void eulaComesOnlyFromLocalToml() {
        assertFalse(create(PROJECT, "", "").isEulaAccepted());
        assertTrue(create(PROJECT, "", "eula = true\n").isEulaAccepted());
        assertFalse(create(PROJECT, "", "eula = false\n").isEulaAccepted());
        assertTrue(
                assertThrows(
                                ConfigurationException.class,
                                () -> create(PROJECT, "", "eula = \"true\"\n"))
                        .getMessage()
                        .contains("local.toml [eula]: 应为 true 或 false"));
    }
}
