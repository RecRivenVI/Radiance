package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 8 节"验证"。
class ValidationChecksTest extends ComplianceFixture {
    @Test
    void validationContentsAreDefinedByTheProject() throws IOException {
        compliant();
        validation("conformance-archive");
        files.put(
                "validations/conformance-archive/validation.toml",
                "tool = \"launch_runner\"\ntargets = [\""
                        + NEOFORGE
                        + "\"]\n\n[archive]\nentry = \"mod.json\"\n");
        files.put(
                "validations/conformance-archive/task/scenarios/archive.toml",
                "entries = [\"mod.json\"]\n");
        files.put("validations/conformance-archive/task/fixtures/input.txt", "input\n");
        files.put(
                "validations/conformance-archive/result/archive/check.txt",
                "FAIL: missing entry\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void validationResultsStayAsTheToolWroteThem() throws IOException {
        compliant();
        validation("smoke-crash");
        String result = "validations/smoke-crash/result/";
        files.put(result + "latest.log", "Exception\r\n\tat Main.run(Main.java:1) \r\n");
        files.put(result + "report.md", "no heading\n\n[missing](nowhere.md)\n");
        files.put(result + "summary.txt", "﻿no final newline");
        files.put("validations/smoke-crash/task/notes.md", "# 说明\n\n[missing](nowhere.md)\n");
        files.put("validations/smoke-crash/task/plan.txt", "tab\there\n");
        assertEquals(
                List.of("C-04 validations/smoke-crash/task/plan.txt"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationCodeIsReported() throws IOException {
        compliant();
        validation("smoke-launch");
        files.put("validations/smoke-launch/task/scripts/check.py", "print('launch')\n");
        files.put("validations/smoke-launch/task/Runner.JAVA", "class Runner {}\n");
        files.put("validations/smoke-launch/result/run.ps1", "exit 0\n");
        files.put("validations/smoke-launch/task/scenario.toml", "steps = []\n");
        files.put("validations/smoke-launch/instance/mods/probe.jar", "generated\n");
        assertEquals(
                List.of(
                        "V-04 validations/smoke-launch/result/run.ps1",
                        "V-04 validations/smoke-launch/task/Runner.JAVA",
                        "V-04 validations/smoke-launch/task/scripts/check.py"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationLayoutViolationsAreReported() throws IOException {
        compliant();
        files.put("validations/notes.txt", "notes\n");
        validation("Smoke-bad");
        files.put("validations/Smoke-bad/extra.txt", "extra\n");
        files.put("validations/Smoke-bad/task", "not a directory\n");
        validation("smoke-missing");
        files.remove("validations/smoke-missing/validation.toml");
        validation("smoke-nested");
        files.remove("validations/smoke-nested/validation.md");
        files.put("validations/smoke-nested/validation.md/nested.md", "# 验证\n");
        assertEquals(Set.of("V-01", "V-03"), rules());
    }

    @Test
    void validationVocabularyCanBeExtended() throws IOException {
        compliant();
        validation("audit-archive");
        List<Finding> findings = run(Map.of());
        assertEquals(List.of(Rule.V02), findings.stream().map(Finding::rule).toList());
        assertTrue(new Report(findings, false, files.size()).passed());
        assertFalse(new Report(findings, true, files.size()).passed());
        assertEquals(List.of(), run(Map.of("validations", List.of("audit"))));
    }

    @Test
    void validationInstancesAreExcludedWithoutGit() throws IOException {
        compliant();
        validation("smoke-runtime");
        files.put("validations/smoke-runtime/instance/custom/options.txt", "generated\r\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void validationsNameTheirToolAndTargets() throws IOException {
        compliant();
        component("archive_format");
        validation("smoke-empty");
        files.put("validations/smoke-empty/validation.toml", "[scenario]\nsteps = []\n");
        validation("smoke-unknown");
        files.put(
                "validations/smoke-unknown/validation.toml",
                "tool = \"archive_format\"\ntargets = [\"26.9-neoforge\", 1]\n");
        validation("smoke-shape");
        files.put(
                "validations/smoke-shape/validation.toml",
                "tool = [\"launch_runner\"]\ntargets = \"" + NEOFORGE + "\"\n");
        validation("smoke-broken");
        files.put("validations/smoke-broken/validation.toml", "tool = [\n");
        assertEquals(
                List.of(
                        "V-08 validations/smoke-broken/validation.toml",
                        "V-08 validations/smoke-empty/validation.toml",
                        "V-08 validations/smoke-empty/validation.toml",
                        "V-08 validations/smoke-shape/validation.toml",
                        "V-08 validations/smoke-shape/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml",
                        "V-08 validations/smoke-unknown/validation.toml"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void validationDescriptionsStateGoalCommandAndCriteria() throws IOException {
        compliant();
        validation("smoke-sections");
        files.put("validations/smoke-sections/validation.md", "# 验证\n\n## 目标\n\n启动。\n");
        validation("smoke-tilde");
        files.put(
                "validations/smoke-tilde/validation.md",
                "# 验证\n\n## 目标\n\n启动。\n\n## 运行\n\n~~~powershell\n.\\gradlew.bat run\n~~~\n\n## 通过标准\n\n没有错误。\n");
        validation("smoke-nested");
        files.put(
                "validations/smoke-nested/validation.md",
                "# 验证\n\n## 目标\n\n````markdown\n```text\n## 运行\n```\n````\n\n## 运行\n\n```powershell\n.\\gradlew.bat run\n```\n\n## 通过标准\n\n没有错误。\n");
        validation("smoke-command");
        files.put(
                "validations/smoke-command/validation.md",
                "# 验证\n\n## 目标\n\n启动。\n\n## 运行\n\n运行任务。\n\n## 通过标准\n\n没有错误。\n");
        assertEquals(
                List.of(
                        "V-09 validations/smoke-command/validation.md: \"## 运行\"一节应用代码块写出执行这项验证的命令",
                        "V-09 validations/smoke-sections/validation.md: 缺少小节 ## 运行",
                        "V-09 validations/smoke-sections/validation.md: 缺少小节 ## 通过标准"),
                run(Map.of()).stream()
                        .map(
                                finding ->
                                        finding.rule().id()
                                                + " "
                                                + finding.path()
                                                + ": "
                                                + finding.message())
                        .sorted()
                        .toList());
    }
}
