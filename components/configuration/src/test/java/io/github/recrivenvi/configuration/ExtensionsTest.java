package io.github.recrivenvi.configuration;

import static io.github.recrivenvi.configuration.Repositories.FABRIC;
import static io.github.recrivenvi.configuration.Repositories.PROJECT;
import static io.github.recrivenvi.configuration.Repositories.ROOT;
import static io.github.recrivenvi.configuration.Repositories.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionsTest {
    private static final String LEGACY = "1.21.4-fabric";
    private static final String SKINS =
            "[skins]\nversion = \"15.0\"\ndescription = \"CustomSkinLoader\"\nsha256 = [\""
                    + "a".repeat(64)
                    + "\"]\n";

    @TempDir Path directory;

    private static RepositoryConfiguration repository(String inputs, String shared, String local) {
        return create(PROJECT, inputs, shared, local, Map.of());
    }

    private static String message(Runnable action) {
        return assertThrows(ConfigurationException.class, action::run).getMessage();
    }

    @Test
    void customInstancesInheritFromTheirBase() {
        RepositoryConfiguration repository =
                repository(
                        "",
                        """
                        [variants.secondary-skins]
                        base = "client-multiplayer"

                        [client-multiplayer]
                        player-name = "Guest"

                        [secondary-skins]
                        width = 800
                        """,
                        "");
        Instance instance = repository.instance("secondary-skins");
        assertEquals(Variant.CLIENT_MULTIPLAYER, instance.base());
        assertEquals(
                List.of("client", "client-multiplayer", "server", "secondary-skins"),
                repository.instances().stream().map(Instance::id).toList());
        LaunchSettings settings = repository.launchSettings(FABRIC, instance);
        assertEquals(
                List.of("--width", "800", "--height", "480", "--username", "Guest"),
                settings.gameArguments());
        assertEquals(
                new File(ROOT, "instances/" + FABRIC + "/secondary-skins"), settings.directory());
    }

    @Test
    void customInstancesAreValidated() {
        assertTrue(
                message(() -> repository("", "[variants.client]\nbase = \"client\"\n", ""))
                        .contains("client 是内置实例"));
        assertTrue(
                message(() -> repository("", "[variants.Skins]\nbase = \"client\"\n", ""))
                        .contains("应为用 - 连接的小写单词"));
        assertTrue(
                message(() -> repository("", "[variants.skins]\nbase = \"player\"\n", ""))
                        .contains("应为 client、client-multiplayer 或 server"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                "",
                                                "[variants.skins]\nbase = \"client\"\n",
                                                "[variants.skins]\nbase = \"server\"\n"))
                        .contains("已在 instances.toml 中定义"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                "",
                                                "[variants.box]\nbase = \"server\"\n[box]\nwidth = 1\n",
                                                ""))
                        .contains("width 不适用于 box"));
    }

    @Test
    void instanceModsNameDeclaredInputs() {
        RepositoryConfiguration repository =
                repository(SKINS, "[client]\nmods = [\"skins\"]\n", "");
        assertEquals(
                List.of("skins"),
                repository.launchSettings(FABRIC, repository.instance("client")).mods());
        assertEquals(
                List.of(), repository.launchSettings(FABRIC, repository.instance("server")).mods());
        assertTrue(
                message(() -> repository("", "[client]\nmods = [\"skins\"]\n", ""))
                        .contains("skins 没有在 inputs.toml 中登记"));
        assertTrue(
                message(() -> repository("", "", "[inputs]\nskins = \"skins.jar\"\n"))
                        .contains("没有在 inputs.toml 中登记；输入已移除时删除这一行"));
        assertTrue(
                message(() -> repository(SKINS, "[client]\nmods = [\"skins\", \"skins\"]\n", ""))
                        .contains("skins 重复出现"));
    }

    @Test
    void inputDeclarationsAreValidated() {
        assertTrue(
                message(
                                () ->
                                        repository(
                                                "[skins]\nversion = \"1\"\nsha256 = [\""
                                                        + "a".repeat(64)
                                                        + "\"]\n",
                                                "",
                                                ""))
                        .contains("inputs.toml [skins.description]"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                "[skins]\nversion = \"1\"\ndescription = \"x\"\nsha256 = [\"ABC\"]\n",
                                                "",
                                                ""))
                        .contains("64 个小写十六进制字符"));
        assertTrue(
                message(() -> repository(SKINS, "", "[inputs]\nskin = \"x.jar\"\n"))
                        .contains("是否想写 skins？"));
        assertTrue(
                message(() -> repository(SKINS, "[inputs]\nskins = \"x.jar\"\n", ""))
                        .contains("输入路径只能写在 local.toml"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                "[skins]\ndescription = \"x\"\nsha256 = [\""
                                                        + "a".repeat(64)
                                                        + "\"]\n",
                                                "",
                                                ""))
                        .contains("inputs.toml [skins.version]"));
    }

    @Test
    void inputFilesAreCheckedAgainstTheirHashes() throws IOException {
        Path jar = directory.resolve("skins.jar");
        Files.writeString(jar, "jar content");
        String hash = sha256("jar content");
        String declared =
                "[skins]\nversion = \"15\"\ndescription = \"CustomSkinLoader 15\"\n"
                        + "source = \"https://example.com\"\n"
                        + "sha256 = [\""
                        + hash
                        + "\"]\n";
        String local = "[inputs]\nskins = '" + jar + "'\n";
        assertEquals(jar.toFile(), repository(declared, "", local).input("skins"));
        assertTrue(
                message(() -> repository(declared, "", "").input("skins"))
                        .contains("写入 CustomSkinLoader 15 的路径；获取方式：https://example.com"));
        Files.writeString(jar, "changed");
        assertTrue(
                message(() -> repository(declared, "", local).input("skins"))
                        .contains("的 SHA-256 为 " + sha256("changed")));
        Files.delete(jar);
        assertTrue(message(() -> repository(declared, "", local).input("skins")).contains("不存在"));
    }

    @Test
    void obfuscatedFabricTargetsChooseMappings() {
        String facts = "loader_version=0.19.5\njava_version=21\n";
        TargetFacts mojang =
                create(PROJECT, "", "", "", Map.of(LEGACY, facts + "mappings=mojang\n"))
                        .target(LEGACY);
        assertTrue(mojang.remapped());
        create(
                PROJECT,
                "",
                "",
                "",
                Map.of(LEGACY, facts + "mappings=yarn\nyarn_version=1.21.4+build.8\n"));
        assertEquals(
                "mojang",
                create(PROJECT, "", "", "", Map.of(LEGACY, facts)).target(LEGACY).mappings());
        assertTrue(
                message(() -> create(PROJECT, "", "", "", Map.of(LEGACY, facts + "mappings=srg\n")))
                        .contains("应为 mojang（默认）或 yarn"));
        assertTrue(
                message(
                                () ->
                                        create(
                                                PROJECT,
                                                "",
                                                "",
                                                "",
                                                Map.of(LEGACY, facts + "mappings=yarn\n")))
                        .contains("mappings=yarn 时必须填写"));
        assertTrue(
                message(
                                () ->
                                        create(
                                                PROJECT,
                                                "",
                                                "",
                                                "",
                                                Map.of("26.4-fabric", facts + "mappings=mojang\n")))
                        .contains("只有混淆的 Minecraft 版本的 Fabric Target"));
        for (String target : List.of("26.4-fabric", "26.2-neoforge"))
            assertTrue(
                    message(
                                    () ->
                                            create(
                                                    PROJECT,
                                                    "",
                                                    "",
                                                    "",
                                                    Map.of(
                                                            target,
                                                            facts
                                                                    + "yarn_version=1.21.4+build.8\n")))
                            .contains("[yarn_version]: 只有混淆的 Minecraft 版本的 Fabric Target"),
                    target);
    }

    @Test
    void modInputsAreNamedByTheirMetadata() throws IOException {
        Path jar = directory.resolve("customskinloader-15.0.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(
                    "{\"schemaVersion\": 1, \"id\": \"customskinloader\", \"version\": \"15.0\",\n"
                            .concat(
                                    " \"depends\": {\"version\": \"9\"}, \"custom\": {\"id\": \"x\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String hash = sha256(Files.readAllBytes(jar));
        String local = "[inputs]\n%s = '" + jar + "'\n";
        String declared =
                "[%s]\nversion = \"%s\"\ndescription = \"CustomSkinLoader\"\nsha256 = [\""
                        + hash
                        + "\"]\n";
        RepositoryConfiguration named =
                repository(
                        declared.formatted("customskinloader", "15.0"),
                        "",
                        local.formatted("customskinloader"));
        assertEquals(jar.toFile(), named.input("customskinloader"));
        assertTrue(
                message(
                                () ->
                                        repository(
                                                        declared.formatted("skins", "15.0"),
                                                        "",
                                                        local.formatted("skins"))
                                                .input("skins"))
                        .contains("是模组 customskinloader 15.0；把输入命名为 [customskinloader]"));
        RepositoryConfiguration wrongVersion =
                repository(
                        declared.formatted("customskinloader", "15"),
                        "",
                        local.formatted("customskinloader"));
        assertTrue(
                message(() -> wrongVersion.input("customskinloader"))
                        .contains("写 version = \"15.0\""));
        assertEquals(null, wrongVersion.availableInput("customskinloader"));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
