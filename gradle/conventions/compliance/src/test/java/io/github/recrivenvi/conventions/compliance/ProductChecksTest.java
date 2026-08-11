package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 6 节"产品"。
class ProductChecksTest extends ComplianceFixture {
    @Test
    void neoForgeMetadataHasNoDisplayTest() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata)
                        .replace(
                                "description = \"${mod_description}\"\n",
                                "description = \"${mod_description}\"\ndisplayTest = \"${display_test}\"\n"));
        assertEquals(List.of(Rule.P04), run(Map.of()).stream().map(Finding::rule).toList());
    }

    @Test
    void forgeMetadataNeedsDisplayTest() throws IOException {
        target = FORGE;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/mods.toml";
        files.put(metadata, files.get(metadata).replace("displayTest = \"${display_test}\"\n", ""));
        assertEquals(List.of(Rule.P04), run(Map.of()).stream().map(Finding::rule).toList());
    }

    @Test
    void loaderMetadataIsChecked() throws IOException {
        compliant();
        String resources = "versions/" + target + "/src/main/resources/";
        files.put(resources + "fabric.mod.json", "{}\n");
        files.put(resources + "assets/example/textures/Block.png", "x\n");
        String metadata = resources + "META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata)
                        .replace("\"${mod_version}\"", "\"1.0\"")
                        .replace("[${minecraft_version}]", "[26.2]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-03", "P-04", "P-05"), ids(findings));
        assertEquals(2, findings.stream().filter(finding -> finding.rule() == Rule.P04).count());
    }

    @Test
    void fabricMetadataIsChecked() throws IOException {
        target = FABRIC;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/fabric.mod.json";
        files.put(
                metadata,
                files.get(metadata)
                        .replace("\"${mod_group}.Example\"", "\"io.github.example.Example\"")
                        .replace("\"java\": \">=${java_version}\"", "\"java\": \">=25\"")
                        .replace("[\"${mod_authors}\"]", "[\"Someone\"]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-04"), ids(findings));
        assertEquals(3, findings.size());
    }

    @Test
    void derivedProjectsLeaveTheTemplateIdentity() throws IOException {
        compliant();
        files.put(
                "gradle.properties",
                files.get("gradle.properties")
                        .replace("mod_id=example", "mod_id=ravens_mod_template"));
        List<Finding> identity =
                run(Map.of()).stream().filter(finding -> finding.rule() == Rule.P06).toList();
        assertEquals(1, identity.size());
        assertEquals(Rule.Level.WARN, identity.getFirst().rule().level());
        assertTrue(identity.getFirst().message().contains("模组 ID"), identity.toString());
    }

    @Test
    void templateNamesStayInTemplateFiles() throws IOException, InterruptedException {
        compliant();
        files.put("NOTICE", "本项目的模板插件来自 Raven's Mod Template。\n");
        files.put("licenses/ravens_mod_template-mit.txt", "MIT License\n");
        files.put(
                "documents/release/changelog-example.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [未发布]\n\n### 新增\n\n- 输出 Raven's Mod Template loaded。\n");
        int readmeLine = files.get("README.md").split("\n", -1).length + 1;
        files.put(
                "README.md",
                files.get("README.md") + "\nPackage io.github.recrivenvi.ModTemplate.\n");
        assertEquals(
                List.of("README.md:" + readmeLine, "documents/release/changelog-example.md:9"),
                run(Map.of()).stream()
                        .filter(finding -> finding.rule() == Rule.P07)
                        .map(finding -> finding.path() + ":" + finding.line())
                        .sorted()
                        .toList());
        assumeTrue(git("init", "-q") == 0);
        assumeTrue(
                git(
                                "remote",
                                "add",
                                "origin",
                                "https://github.com/RecRivenVI/Ravens-Mod-Template.git")
                        == 0);
        assertTrue(run(Map.of()).stream().noneMatch(finding -> finding.rule() == Rule.P07));
    }

    @Test
    void loaderRangesMayUseTheLoaderMinimum() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        files.put(
                metadata,
                files.get(metadata).replace("[${loader_version},)", "[${loader_minimum},)"));
        assertEquals(List.of(), run(Map.of()));
        files.put(metadata, files.get(metadata).replace("[${loader_minimum},)", "[21.1.62,)"));
        assertEquals(Set.of("P-04"), rules());
    }

    @Test
    void tomlMetadataFieldsAreCheckedWhereTheLoaderReadsThem() throws IOException {
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/META-INF/neoforge.mods.toml";
        String original = files.get(metadata);
        files.put(
                metadata,
                original.replace("[[dependencies.${mod_id}]]", "[[dependencies.unrelated]]"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("P-04"), ids(findings));
        assertEquals(3, findings.size(), findings.toString());
        files.put(
                metadata,
                original.replace("[[dependencies.${mod_id}]]", "[[dependencies.\"${mod_id}\"]]"));
        assertEquals(List.of(), run(Map.of()));
        files.put(
                metadata,
                original.replace("modId = \"${mod_id}\"", "modId = \"wrong\"")
                        + "\n[[mods]]\nmodId = \"${mod_id}\"\n");
        assertEquals(List.of("必须有且只有一个 [[mods]] 条目"), messages(Rule.P04));
        files.put(metadata, original.replace("license = \"${mod_license}\"", "license = \"MIT\""));
        assertEquals(List.of("license 必须是 \"${mod_license}\""), messages(Rule.P04));
        files.put(metadata, original + "modId = \"twice\"\n");
        assertEquals(Set.of("P-04"), rules());
    }

    @Test
    void fabricMetadataFieldsAreReadAtTheTopLevel() throws IOException {
        target = FABRIC;
        compliant();
        String metadata = "versions/" + target + "/src/main/resources/fabric.mod.json";
        String original = files.get(metadata);
        files.put(
                metadata,
                original.replace("\"id\": \"${mod_id}\"", "\"id\": \"wrong_mod\"")
                        .replace(
                                "\"schemaVersion\": 1,",
                                "\"schemaVersion\": 1,\n    \"custom\": {\"id\": \"${mod_id}\"},"));
        assertEquals(List.of("id 必须是 \"${mod_id}\""), messages(Rule.P04));
        files.put(
                metadata,
                original.replace(
                        "\"main\": [\"${mod_group}.Example\"]",
                        "\"main\": [{\"adapter\": \"kotlin\", \"value\": \"${mod_group}.Example\"}],"
                                + " \"client\": [\"org.other.Client\"]"));
        assertEquals(
                List.of("entrypoints.client 中的入口 org.other.Client 必须以 ${mod_group}. 开头"),
                messages(Rule.P04));
        files.put(
                metadata,
                original.replace(
                        "\"minecraft\": \"${minecraft_version}\"",
                        "\"minecraft\": [\"~${minecraft_version}\", \"26.2.1\"]"));
        assertEquals(List.of(), run(Map.of()));
        files.put(metadata, original.replace("\"id\": \"${mod_id}\"", "\"id\": ${mod_id}"));
        assertEquals(Set.of("P-01", "P-04"), rules());
    }
}
