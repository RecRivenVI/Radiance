package io.github.recrivenvi.configuration;

import static io.github.recrivenvi.configuration.Repositories.FABRIC;
import static io.github.recrivenvi.configuration.Repositories.NEOFORGE;
import static io.github.recrivenvi.configuration.Repositories.ROOT;
import static io.github.recrivenvi.configuration.Repositories.daily;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DailyInstancesTest {
    private static final Instance CLIENT = new Instance("client", Variant.CLIENT);
    private static final Instance CLIENT_MULTIPLAYER =
            new Instance("client-multiplayer", Variant.CLIENT_MULTIPLAYER);
    private static final Instance SERVER = new Instance("server", Variant.SERVER);
    private static final String TABLES =
            """
            [all]
            memory-max = "1G"

            [client]
            memory-max = "2G"

            [targets."26.2-neoforge".all]
            memory-max = "3G"

            [targets."26.2-neoforge".client]
            memory-max = "4G"
            """;

    private static List<String> jvm(
            RepositoryConfiguration properties, String target, Instance variant) {
        return properties.launchSettings(target, variant).jvmArguments();
    }

    private static List<String> game(
            RepositoryConfiguration properties, String target, Instance variant) {
        return properties.launchSettings(target, variant).gameArguments();
    }

    private static String message(String shared, String local) {
        return assertThrows(ConfigurationException.class, () -> daily(shared, local)).getMessage();
    }

    @Test
    void everySettingHasMinecraftDefaults() {
        RepositoryConfiguration properties = daily("", "");
        assertEquals(List.of("-Xms2G", "-Xmx2G"), jvm(properties, NEOFORGE, CLIENT));
        assertEquals(List.of("-Xms1G", "-Xmx1G"), jvm(properties, NEOFORGE, SERVER));
        assertEquals(
                List.of("--width", "854", "--height", "480", "--username", "DevPlayer"),
                game(properties, NEOFORGE, CLIENT));
        assertEquals(
                List.of("--width", "854", "--height", "480", "--username", "DevGuest"),
                game(properties, NEOFORGE, CLIENT_MULTIPLAYER));
        assertEquals(List.of("--nogui", "--port", "25565"), game(properties, NEOFORGE, SERVER));
    }

    @Test
    void theMostSpecificTableWins() {
        RepositoryConfiguration properties = daily(TABLES, "");
        assertEquals(List.of("-Xms4G", "-Xmx4G"), jvm(properties, NEOFORGE, CLIENT));
        assertEquals(List.of("-Xms3G", "-Xmx3G"), jvm(properties, NEOFORGE, SERVER));
        assertEquals(List.of("-Xms2G", "-Xmx2G"), jvm(properties, FABRIC, CLIENT));
        assertEquals(List.of("-Xms1G", "-Xmx1G"), jvm(properties, FABRIC, SERVER));
    }

    @Test
    void memoryBoundsAreSeparate() {
        RepositoryConfiguration properties =
                daily("[client]\nmemory-min = \"1G\"\nmemory-max = \"6G\"\n", "");
        assertEquals(List.of("-Xms1G", "-Xmx6G"), jvm(properties, NEOFORGE, CLIENT));
        assertTrue(
                message("[client]\nmemory-min = \"4G\"\nmemory-max = \"2G\"\n", "")
                        .contains("26.2-neoforge client 的 memory-min 4G 超过 memory-max 2G"));
    }

    @Test
    void localValuesReplaceSharedValuesInTheSameTableOnly() {
        RepositoryConfiguration sameTable =
                daily(TABLES, "[targets.\"26.2-neoforge\".client]\nmemory-max = \"8G\"\n");
        assertEquals(List.of("-Xms8G", "-Xmx8G"), jvm(sameTable, NEOFORGE, CLIENT));
        RepositoryConfiguration broaderTable = daily(TABLES, "[all]\nmemory-max = \"8G\"\n");
        assertEquals(List.of("-Xms4G", "-Xmx4G"), jvm(broaderTable, NEOFORGE, CLIENT));
        assertEquals(List.of("-Xms8G", "-Xmx8G"), jvm(broaderTable, FABRIC, SERVER));
    }

    @Test
    void localArraysReplaceSharedArrays() {
        RepositoryConfiguration properties =
                daily(
                        "[all]\njvm-args = [\"-Da=1\", \"-Db=2\"]\n",
                        "[all]\njvm-args = [\"-Dc=3\"]\n");
        assertEquals(List.of("-Xms1G", "-Xmx1G", "-Dc=3"), jvm(properties, NEOFORGE, SERVER));
        RepositoryConfiguration emptied =
                daily("[all]\njvm-args = [\"-Da=1\"]\n", "[all]\njvm-args = []\n");
        assertEquals(List.of("-Xms1G", "-Xmx1G"), jvm(emptied, NEOFORGE, SERVER));
    }

    @Test
    void clientsReceiveWindowAndPlayerArguments() {
        RepositoryConfiguration properties =
                daily(
                        """
                        [all]
                        width = 1280
                        height = 720
                        game-args = ["--demo"]

                        [client-multiplayer]
                        player-name = "Guest_2"
                        """,
                        "");
        assertEquals(
                List.of("--width", "1280", "--height", "720", "--username", "Guest_2", "--demo"),
                game(properties, NEOFORGE, CLIENT_MULTIPLAYER));
        assertEquals(
                List.of("--nogui", "--port", "25565", "--demo"),
                game(properties, NEOFORGE, SERVER));
    }

    @Test
    void serversReceiveThePort() {
        RepositoryConfiguration properties = daily("[server]\nport = 25570\n", "");
        assertEquals(List.of("--nogui", "--port", "25570"), game(properties, NEOFORGE, SERVER));
    }

    @Test
    void instancesLiveUnderTheInstancesDirectory() {
        assertEquals(
                new File(ROOT, "instances/" + NEOFORGE + "/client-multiplayer"),
                daily("", "").launchSettings(NEOFORGE, CLIENT_MULTIPLAYER).directory());
    }

    @Test
    void describeNamesTheSourceOfEachValue() {
        List<String> lines =
                daily(TABLES, "[targets.\"26.2-neoforge\".client]\nwidth = 800\n")
                        .describeLaunchSettings(NEOFORGE, CLIENT);
        assertTrue(
                lines.contains(
                        "memory-max = \"4G\"  (instances.toml [targets.\"26.2-neoforge\".client])"),
                lines.toString());
        assertTrue(lines.contains("memory-min = \"4G\"  (默认值，等于 memory-max)"), lines.toString());
        assertTrue(
                lines.contains("width = 800  (local.toml [targets.\"26.2-neoforge\".client])"),
                lines.toString());
        assertTrue(lines.contains("height = 480  (默认值)"), lines.toString());
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("port")));
    }

    @Test
    void settingsMustApplyToTheirTable() {
        assertTrue(message("[server]\nwidth = 800\n", "").contains("width 不适用于 server"));
        assertTrue(message("[client]\nport = 25570\n", "").contains("port 不适用于 client"));
    }

    @Test
    void unknownNamesFailWithSuggestions() {
        assertTrue(message("[client]\nmemmory-max = \"2G\"\n", "").contains("是否想写 memory-max？"));
        assertTrue(message("[clinet]\nmemory-max = \"2G\"\n", "").contains("是否想写 client？"));
        assertTrue(
                message("[targets.\"26.2-neoforg\".client]\nmemory-max = \"2G\"\n", "")
                        .contains("是否想写 26.2-neoforge？"));
        assertTrue(
                message("[targets.\"26.2-neoforge\".clients]\nmemory-max = \"2G\"\n", "")
                        .contains("是否想写 client？"));
    }

    @Test
    void targetTablesNameRegisteredTargetsAndInstances() {
        assertTrue(
                message("[targets.\"1.20.1-forge\".client]\nwidth = 800\n", "")
                        .contains("未登记的 Target；Target 已移除时删除这张表"));
        assertTrue(message("", "[old-skins]\nwidth = 800\n").contains("额外实例已移除时删除这张表"));
        assertTrue(
                message("[targets.\"26.2-neoforge\".box]\nwidth = 800\n", "").contains("未知的实例表"));
    }

    @Test
    void eulaBelongsOnlyInLocalToml() {
        assertTrue(message("eula = true\n", "").contains("eula 只能写在 local.toml"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "memory-max = \"4g\"",
                "memory-max = \"4\"",
                "memory-max = 4",
                "memory-min = \"0G\"",
                "memory-max = \"8589934592G\"",
                "memory-max = \"99999999999999999999K\"",
                "width = 0",
                "width = \"800\"",
                "player-name = \"ab\"",
                "player-name = \"has space\"",
                "jvm-args = [\"-Xmx4G\"]",
                "jvm-args = [\"-Xms1G\"]",
                "jvm-args = [\"-XX:PermSize=128M\"]",
                "jvm-args = \"-Da=1\"",
                "game-args = [\"--username\", \"Other\"]",
                "game-args = [\"--gameDir=elsewhere\"]",
                "game-args = [\"nogui\"]",
                "game-args = [1]"
            })
    void invalidValuesFailEvenWhenOverridden(String line) {
        String local =
                """
                [all]
                memory-max = "2G"
                memory-min = "1G"
                width = 640
                player-name = "Valid"
                jvm-args = []
                game-args = []
                """;
        String message = message("[all]\n" + line + "\n", local);
        assertTrue(message.startsWith("instances.toml [all."), message);
    }

    @Test
    void memoryBeyondTheRepresentableRangeIsRejected() {
        assertTrue(
                message("[client]\nmemory-min = \"8589934592G\"\nmemory-max = \"4G\"\n", "")
                        .contains("[client.memory-min]: 8589934592G 超出可以表示的范围"));
    }

    @Test
    void removedPermanentGenerationOptionsExplainTheReplacement() {
        assertTrue(
                message("[all]\njvm-args = [\"-XX:MaxPermSize=256M\"]\n", "")
                        .contains("已在 Java 8 中移除"));
    }

    @Test
    void portMustBeInRange() {
        assertTrue(message("[server]\nport = 70000\n", "").contains("1 到 65535"));
    }

    @Test
    void invalidTomlReportsThePosition() {
        String message = message("[all]\nmemory-max = \"2G\"\nmemory-max = \"3G\"\n", "");
        assertTrue(message.startsWith("instances.toml: 第 3 行"), message);
    }
}
