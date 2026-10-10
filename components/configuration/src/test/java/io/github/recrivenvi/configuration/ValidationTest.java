package io.github.recrivenvi.configuration;

import static io.github.recrivenvi.configuration.Repositories.FABRIC;
import static io.github.recrivenvi.configuration.Repositories.NEOFORGE;
import static io.github.recrivenvi.configuration.Repositories.PROJECT;
import static io.github.recrivenvi.configuration.Repositories.ROOT;
import static io.github.recrivenvi.configuration.Repositories.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationTest {
    private static final Instance CLIENT = new Instance("client", Variant.CLIENT);
    private static final Instance SERVER = new Instance("server", Variant.SERVER);
    private static final String SKINS =
            "[skins]\nversion = \"15.0\"\ndescription = \"CustomSkinLoader\"\nsha256 = [\""
                    + "a".repeat(64)
                    + "\"]\n";
    private static final String HEADER =
            "tool = \"spawn_checker\"\ntargets = [\"" + NEOFORGE + "\"]\n";

    private static RepositoryConfiguration repository(String validation) {
        return repository("", "", "", Map.of("smoke-launch", validation));
    }

    private static RepositoryConfiguration repository(
            String inputs, String shared, String local, Map<String, String> validations) {
        return create(PROJECT, inputs, shared, local, Map.of(), validations);
    }

    private static String message(Runnable action) {
        return assertThrows(IllegalArgumentException.class, action::run).getMessage();
    }

    @Test
    void rolesAndRegisteredTargetsComeFromValidationToml() {
        RepositoryConfiguration repository =
                repository(
                        """
                        tool = "spawn_checker"
                        targets = ["26.2-neoforge", "1.0-unknown", "26.2-neoforge"]
                        scenario = "project-defined"

                        [instances.server]
                        port = 25570

                        [instances.client]
                        memory-max = "3G"
                        game-args = ["--quickPlayMultiplayer", "127.0.0.1:25570"]
                        """);
        Validation validation = repository.validation("smoke-launch");
        assertEquals(List.of(NEOFORGE), validation.targets());
        assertEquals(List.of(SERVER, CLIENT), validation.roles());
        LaunchSettings client = repository.launchSettings(validation, NEOFORGE, CLIENT);
        assertEquals(
                new File(ROOT, "validations/smoke-launch/instance/26.2-neoforge/client"),
                client.directory());
        assertEquals(List.of("-Xms3G", "-Xmx3G"), client.jvmArguments());
        assertEquals(
                List.of(
                        "--width",
                        "854",
                        "--height",
                        "480",
                        "--username",
                        "DevPlayer",
                        "--quickPlayMultiplayer",
                        "127.0.0.1:25570"),
                client.gameArguments());
        assertEquals(
                List.of("--nogui", "--port", "25570"),
                repository.launchSettings(validation, NEOFORGE, SERVER).gameArguments());
        assertEquals(new File(ROOT, "validations/smoke-launch"), validation.directory(ROOT));
    }

    @Test
    void validationInstancesIgnorePersonalAndSharedPreferences() {
        RepositoryConfiguration repository =
                repository(
                        "",
                        "[all]\nmemory-max = \"8G\"\n",
                        "[client]\nplayer-name = \"Alice\"\nwidth = 1920\n",
                        Map.of("smoke-launch", HEADER + "[instances.client]\n"));
        LaunchSettings settings =
                repository.launchSettings(repository.validation("smoke-launch"), NEOFORGE, CLIENT);
        assertEquals(List.of("-Xms2G", "-Xmx2G"), settings.jvmArguments());
        assertEquals(
                List.of("--width", "854", "--height", "480", "--username", "DevPlayer"),
                settings.gameArguments());
        assertEquals(
                List.of("-Xms8G", "-Xmx8G"),
                repository.launchSettings(NEOFORGE, CLIENT).jvmArguments());
    }

    @Test
    void describeNamesTheValidationFile() {
        RepositoryConfiguration repository =
                repository(HEADER + "[instances.server]\nmemory-max = \"2G\"\n");
        List<String> lines =
                repository.describeLaunchSettings(
                        repository.validation("smoke-launch"), NEOFORGE, SERVER);
        assertTrue(
                lines.contains(
                        "memory-max = \"2G\"  (validations/smoke-launch/validation.toml"
                                + " [instances.server])"),
                lines.toString());
    }

    @Test
    void validationsWithoutInstancesHaveNoRuns() {
        RepositoryConfiguration repository =
                repository("", "", "", Map.of("conformance-jar", "tool = \"x\"\ntargets = []\n"));
        Validation validation = repository.validation("conformance-jar");
        assertTrue(validation.targets().isEmpty());
        assertTrue(validation.roles().isEmpty());
    }

    @Test
    void rolesAreTheBuiltInInstances() {
        assertTrue(
                message(() -> repository(HEADER + "[instances.clinet]\n"))
                        .contains(
                                "validations/smoke-launch/validation.toml [instances.clinet]: 应为"
                                        + " client、client-multiplayer 或 server；是否想写 client？"),
                message(() -> repository(HEADER + "[instances.clinet]\n")));
        String variant =
                message(
                        () ->
                                repository(
                                        "",
                                        "[variants.secondary]\nbase = \"client\"\n",
                                        "",
                                        Map.of(
                                                "smoke-launch",
                                                HEADER + "[instances.secondary]\n")));
        assertTrue(variant.contains("[instances.secondary]: 应为 client"), variant);
    }

    @Test
    void instanceSettingsFollowTheDailyRules() {
        assertTrue(
                message(() -> repository(HEADER + "[instances.client]\nport = 25570\n"))
                        .contains("[instances.client.port]: port 不适用于 client"));
        assertTrue(
                message(() -> repository(HEADER + "[instances.server]\nwidth = 800\n"))
                        .contains("width 不适用于 server"));
        assertTrue(
                message(() -> repository(HEADER + "[instances.client]\nmemroy-max = \"2G\"\n"))
                        .contains("未知的实例设置；是否想写 memory-max？"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                HEADER
                                                        + "[instances.client]\nmemory-min ="
                                                        + " \"4G\"\nmemory-max = \"2G\"\n"))
                        .contains("client 的 memory-min 4G 超过 memory-max 2G"));
        assertTrue(
                message(() -> repository(HEADER + "[instances.client]\nmods = [\"skins\"]\n"))
                        .contains("skins 没有在 inputs.toml 中登记"));
        Map<String, String> validations =
                Map.of("smoke-launch", HEADER + "[instances.client]\nmods = [\"skins\"]\n");
        RepositoryConfiguration repository = repository(SKINS, "", "", validations);
        assertEquals(
                List.of("skins"),
                repository
                        .launchSettings(repository.validation("smoke-launch"), NEOFORGE, CLIENT)
                        .mods());
    }

    @Test
    void instancesMustBeTablesOfTables() {
        assertTrue(
                message(() -> repository(HEADER + "instances = 1\n"))
                        .contains("validation.toml [instances]: 应为表"));
        assertTrue(
                message(() -> repository(HEADER + "[instances]\nclient = 1\n"))
                        .contains("[instances.client]: 应为表"));
    }

    @Test
    void instancesNeedTargets() {
        String expected = "[instances]: targets 没有列出 Target";
        assertTrue(
                message(() -> repository("tool = \"x\"\ntargets = []\n[instances.client]\n"))
                        .contains(expected));
        assertTrue(
                message(() -> repository("tool = \"x\"\n[instances.client]\n")).contains(expected));
    }

    @Test
    void launchSettingsNeedAListedTargetAndADeclaredRole() {
        RepositoryConfiguration repository = repository(HEADER + "[instances.client]\n");
        Validation validation = repository.validation("smoke-launch");
        assertTrue(
                message(() -> repository.launchSettings(validation, FABRIC, CLIENT))
                        .contains("[targets]: 没有列出已登记的 Target 26.3-fabric"));
        assertTrue(
                message(() -> repository.launchSettings(validation, NEOFORGE, SERVER))
                        .contains("没有声明 [instances.server]；已声明的角色：client"));
        assertTrue(
                message(() -> repository.validation("smoke-lunch"))
                        .contains(
                                "未知的验证：smoke-lunch；验证是 validations/ 中带 validation.toml"
                                        + " 的目录；是否想写 smoke-launch？"));
    }

    @Test
    void taskNamesAreCamelCaseAndUnique() {
        Map<String, String> validations = new LinkedHashMap<>();
        validations.put("visual-title_screen", HEADER);
        validations.put("smoke-launch2", HEADER);
        RepositoryConfiguration repository = repository("", "", "", validations);
        assertEquals("VisualTitleScreen", repository.validation("visual-title_screen").camelName());
        assertEquals("SmokeLaunch2", repository.validation("smoke-launch2").camelName());
        validations.put("visual_title-screen", HEADER);
        assertTrue(
                message(() -> repository("", "", "", validations))
                        .contains(
                                "validations/visual_title-screen: 与 validations/visual-title_screen"
                                        + " 生成相同的任务名 VisualTitleScreen"));
    }

    @Test
    void validationsAreListedByName() {
        Map<String, String> validations = new LinkedHashMap<>();
        validations.put("smoke-b", HEADER);
        validations.put("smoke-a", HEADER);
        assertEquals(
                List.of("smoke-a", "smoke-b"),
                repository("", "", "", validations).validations().stream()
                        .map(Validation::name)
                        .toList());
    }
}
