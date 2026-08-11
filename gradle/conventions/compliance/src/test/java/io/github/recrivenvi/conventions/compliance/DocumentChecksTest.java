package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 9 节"文档"。
class DocumentChecksTest extends ComplianceFixture {
    @Test
    void documentViolationsAreReported() throws IOException {
        compliant();
        files.put("documents/guides/setup-local.md", "# x\n");
        files.put("documents/usage/Installation.md", "# x\n");
        files.put("documents/usage/tutorial-backpack.md", "# 背包\n\n适用于 26.2。运行 gradlew build。\n");
        files.put("documents/usage/nested/guide-x.md", "# x\n");
        files.put("documents/project/adr-1-build.md", "# x\n");
        files.put("documents/design/overview-system.md", "no heading\n\n# Late\n");
        files.put("documents/reference/api-core.md", "# API\n\n[missing](api-missing.md)\n");
        files.put("documents/development/procedure-port.md", "# 移植\n\n## 步骤\n\n1. 文本。\n");
        files.put("documents/usage/images/screenshot.png", "not really an image\n");
        assertEquals(Set.of("D-01", "D-02", "D-03", "D-06", "D-07", "D-08"), rules());
    }

    @Test
    void namesJoinFieldsWithHyphensAndWordsWithUnderscores() throws IOException {
        compliant();
        String figure = "\n\n![图 1：机器](images/guide-first_machine-1.png)\n";
        files.put("documents/usage/guide-first_machine.md", "# 第一台机器\n\n适用于 26.2。" + figure);
        files.put(
                "documents/usage/guide-first_machine-en_US.md",
                "# First machine\n\nFor Minecraft 26.2." + figure);
        files.put("documents/usage/images/guide-first_machine-1.png", "image\n");
        files.put(
                "documents/project/phase-1-start.md", "# 启动\n\n## 目标\n\n## 范围\n\n## 步骤\n\n## 结果\n");
        files.put(
                "documents/project/phase-1.1-tools.md",
                "# 工具\n\n## 目标\n\n## 范围\n\n## 步骤\n\n## 结果\n");
        validation("visual-title_screen");
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/usage/guide-first-machine.md", "# 机器\n");
        files.put("documents/usage/guide-backpack.en_us.md", "# Backpack\n");
        files.put("documents/usage/guide-backpack-en_us.md", "# Backpack\n");
        files.put("README_zh-CN.md", "# 示例\n");
        files.put("components/launch-runner/build.gradle.kts", "plugins {}\n");
        validation("visual-title-screen");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-02", "L-01", "L-04", "L-08", "V-01"), ids(findings));
        assertEquals(3, findings.stream().filter(finding -> finding.rule() == Rule.D02).count());
    }

    @Test
    void decisionRecordsDeclareTheirStatus() throws IOException {
        compliant();
        String body = "\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n";
        files.put("documents/design/adr-2-cache.md", "# 缓存\n\n## 状态\n\n已被 adr-1 取代" + body);
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/design/adr-2-cache.md", "# 缓存\n\n## 状态\n\n已被 adr-9 取代" + body);
        files.put("documents/design/adr-3-store.md", "# 存储\n\n## 状态\n\n还在讨论" + body);
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-06"), ids(findings));
        assertEquals(2, findings.size());
    }

    @Test
    void translationsFollowTheirOriginal() throws IOException {
        compliant();
        files.put("documents/usage/guide-start.md", "# 入门\n\n适用于 26.2。\n\n## 准备\n\n## 步骤\n");
        files.put(
                "documents/usage/guide-start-en_US.md",
                "# Start\n\nFor Minecraft 26.2.\n\n## Preparation\n\n## Steps\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/usage/guide-start-en_US.md",
                "# Start\n\nFor Minecraft 26.2.\n\n## Preparation\n");
        files.put("documents/usage/guide-other-en_US.md", "# Other\n\nFor Minecraft 26.2.\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-13"), ids(findings));
        assertEquals(2, findings.size());
    }

    @Test
    void changelogsFollowKeepAChangelog() throws IOException {
        compliant();
        files.put(
                "documents/release/changelog-v1.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [未发布]\n\n### 新增\n\n- 文本。\n\n"
                        + "## [1.10.0] - 2026-10-01\n\n### 修复\n\n- 文本。\n\n"
                        + "## [1.9.0-beta.2] - 2026-09-01\n\n### 变更\n\n- 文本。\n\n"
                        + "## [1.9.0-beta.1] - 2026-08-01\n\n### 安全\n\n- 文本。\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/release/changelog-v1.md",
                "# 更新日志\n\n适用于 26.2。\n\n## [1.0.0] - 2026-02-30\n\n### 新功能\n\n- 文本。\n\n"
                        + "## [未发布]\n\n## [1.1.0] - 2026-03-01\n\n## 1.2.0\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-14"), ids(findings));
        assertEquals(5, findings.size());
    }

    @Test
    void playerDocumentsNameAVersionFirst() throws IOException {
        compliant();
        files.put("documents/usage/guide-start.md", "# 入门\n\n先打开游戏。\n\n适用于 26.2。\n");
        files.put("documents/usage/faq-general.md", "# 常见问题\n\n适用于 26.20 与 126.2。\n");
        files.put("documents/release/support-versions.md", "# 支持范围\n\n适用于 Minecraft 26.2。\n");
        files.put("documents/release/migration-old_saves.md", "# 旧存档迁移\n\n适用于 Minecraft 1.19.2。\n");
        files.put("documents/release/upgrade-config.md", "# 配置升级\n\n按下面的步骤升级。\n");
        List<Finding> findings = run(Map.of());
        assertEquals(
                List.of(
                        "D-15 documents/release/upgrade-config.md",
                        "D-15 documents/usage/faq-general.md",
                        "D-15 documents/usage/guide-start.md"),
                findings.stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }

    @Test
    void recordNumbersAreConsistent() throws IOException {
        compliant();
        String adr = "\n\n## 状态\n\n已接受\n\n## 背景\n\n文本。\n\n## 决策\n\n文本。\n\n## 后果\n\n文本。\n";
        String phase = "\n\n## 目标\n\n文本。\n\n## 范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 结果\n\n文本。\n";
        files.put("documents/design/adr-2-cache.md", "# 缓存" + adr);
        files.put(
                "documents/design/adr-2-cache-en_US.md",
                "# Cache\n\n## Status\n\nAccepted\n\n## Context\n\nText.\n\n## Decision\n\nText.\n\n"
                        + "## Consequences\n\nText.\n");
        files.put("documents/project/phase-1-start.md", "# 启动" + phase);
        files.put("documents/project/phase-1.1-tools.md", "# 工具" + phase);
        files.put("documents/project/phase-1.2-docs.md", "# 文档" + phase);
        assertEquals(List.of(), run(Map.of()));
        files.put("documents/design/adr-03-cache.md", "# 缓存" + adr);
        files.put("documents/design/adr-2-other.md", "# 其他" + adr);
        files.put("documents/project/phase-3-release.md", "# 发布" + phase);
        files.put("documents/project/phase-1.4-tests.md", "# 测试" + phase);
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-02"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void figuresAreNumberedAndShownInOrder() throws IOException {
        compliant();
        files.put("documents/usage/images/installation-basics-3.png", "image\n");
        files.put("documents/usage/images/installation-basics-01.png", "image\n");
        files.put(
                "documents/usage/guide-start.md",
                "# 入门\n\n适用于 26.2。\n\n![图 2：第二步](images/guide-start-2.png)\n\n![第一步](images/guide-start-1.png)\n");
        files.put("documents/usage/images/guide-start-1.png", "image\n");
        files.put("documents/usage/images/guide-start-2.png", "image\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-08"), ids(findings));
        assertEquals(5, findings.size());
    }

    @Test
    void readmesLinkEachOtherAndListTargets() throws IOException {
        compliant();
        files.put("README-zh_CN.md", readme("示例", "简体中文").replace("| 25 |", "| 21 |"));
        files.put(
                "README.md",
                readme("Example", "English | [简体中文](README-zh_CN.md)")
                        .replace(target, "1.0-forge"));
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-12"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void projectVocabularySilencesWarnings() throws IOException {
        compliant();
        files.put("documents/usage/tutorial-backpack.md", "# 背包\n\n适用于 26.2。\n");
        assertTrue(rules().contains("D-03"));
        assertFalse(
                run(Map.of("documents/usage", List.of("tutorial"))).stream()
                        .anyMatch(finding -> finding.rule() == Rule.D03));
    }

    @Test
    void researchDocumentsAreDatedAndStructured() throws IOException {
        compliant();
        files.put(
                "documents/research/audit-tick_timing.md",
                "# Tick 时序审计\n\n调查日期 2026-09-17，依据提交 abc123。\n\n"
                        + "## 问题\n\n文本。\n\n## 方法\n\n文本。\n\n## 发现\n\n文本。\n\n## 结论\n\n文本。\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/research/incident-structure_loss.md",
                "# 结构丢失\n\n发生在 2026-02-30。\n\n## 问题\n\n文本。\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("D-06", "D-16"), ids(findings));
        assertEquals(4, findings.size());
    }

    @Test
    void englishDocumentsUseEnglishSections() throws IOException {
        compliant();
        files.put(
                "documents/AGENTS.md",
                project("1.0").replace("| 文档主语言 | 简体中文 |", "| 文档主语言 | English |"));
        files.put(
                "documents/design/adr-1-build.md",
                "# Build\n\n## Status\n\nSuperseded by adr-2\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        files.put(
                "documents/design/adr-2-cache.md",
                "# Cache\n\n## Status\n\nAccepted\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        files.put(
                "documents/development/procedure-add_target.md",
                "# Add a Target\n\n## Scope\n\nText.\n\n## Steps\n\n1. Text.\n\n## Acceptance\n\nText.\n");
        files.put(
                "documents/development/procedure-add_target-zh_CN.md",
                "# 新增 Target\n\n## 适用范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 验收\n\n文本。\n");
        files.put(
                "documents/research/audit-timing.md",
                "# Timing audit\n\nRecorded on 2026-09-17.\n\n"
                        + "## Question\n\n## Method\n\n## Findings\n\n## Conclusion\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/design/adr-2-cache.md",
                "# Cache\n\n## 状态\n\n已接受\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        assertEquals(Set.of("D-06"), rules());
        files.put(
                "documents/AGENTS.md",
                project("1.0").replace("| 文档主语言 | 简体中文 |", "| 文档主语言 | 日本語 |"));
        assertTrue(rules().contains("S-05"));
    }

    @Test
    void readmeLoaderVersionsMatchExactly() throws IOException {
        compliant();
        files.put("README.md", files.get("README.md").replace("Loader 1.0 |", "Loader 1.0.9 |"));
        assertEquals(List.of(target + " 的加载器版本应为 1.0"), messages(Rule.D12));
        files.put("README.md", files.get("README.md").replace("Loader 1.0.9 |", "`1.0` |"));
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void changelogVersionsFollowSemanticVersioning() throws IOException {
        compliant();
        String header = "# 更新日志\n\n适用于 26.2。\n\n";
        files.put(
                "documents/release/changelog-v1.md",
                header
                        + "## [1.0.0+build.2] - 2026-10-02\n\n"
                        + "## [1.0.0-rc.1+build.1] - 2026-10-01\n\n"
                        + "## [1.0.0-alpha.99999999999999999999] - 2026-09-01\n\n"
                        + "## [1.0.0-alpha.2] - 2026-08-01\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                "documents/release/changelog-v1.md",
                header
                        + "## [01.0.0] - 2026-10-01\n\n"
                        + "## [1.0.0-alpha..1] - 2026-09-01\n\n"
                        + "## [1.0.0-01] - 2026-08-01\n\n"
                        + "## [1.0.0+] - 2026-07-01\n");
        assertEquals(
                List.of(
                        "01.0.0 不符合语义化版本 2.0.0",
                        "1.0.0-alpha..1 不符合语义化版本 2.0.0",
                        "1.0.0-01 不符合语义化版本 2.0.0",
                        "1.0.0+ 不符合语义化版本 2.0.0"),
                messages(Rule.D14));
        assertTrue(DocumentChecks.compare("1.0.0+b", "1.0.0+a") == 0);
        assertTrue(DocumentChecks.compare("1.0.0", "1.0.0-rc.1") > 0);
        assertTrue(DocumentChecks.compare("1.0.0-alpha.10", "1.0.0-alpha.9") > 0);
        assertTrue(DocumentChecks.compare("1.0.0-alpha.beta", "1.0.0-alpha.1") > 0);
    }

    @Test
    void specificationAppendixFollowsDocumentRulesInChinese() throws IOException {
        compliant();
        files.put(
                "documents/AGENTS.md",
                project("1.0").replace("| 文档主语言 | 简体中文 |", "| 文档主语言 | English |"));
        files.put(
                "documents/design/adr-1-build.md",
                "# Build\n\n## Status\n\nAccepted\n\n## Context\n\nText.\n\n"
                        + "## Decision\n\nText.\n\n## Consequences\n\nText.\n");
        files.put(
                "documents/development/procedure-add_target.md",
                "# Add a Target\n\n## Scope\n\nText.\n\n## Steps\n\n1. Text.\n\n## Acceptance\n\nText.\n");
        String specification = SpecificationChecks.SPECIFICATION;
        String procedure = specification + "/development/procedure-upgrade_template.md";
        files.put(procedure, "# 升级模板\n\n## 适用范围\n\n文本。\n\n## 步骤\n\n1. 文本。\n\n## 验收\n\n文本。\n");
        assertEquals(List.of(), documentFindings());
        files.put(procedure, "# 升级模板\n\n## 步骤\n\n1. 文本。\n");
        files.put(specification + "/notes/setup-misc.md", "# 杂项\n");
        files.put(specification + "/development/Setup Notes.md", "# 说明\n");
        assertEquals(
                List.of(
                        "D-01 " + specification + "/notes",
                        "D-02 " + specification + "/development/Setup Notes.md",
                        "D-06 " + procedure,
                        "D-06 " + procedure),
                documentFindings());
    }

    @Test
    void projectsKeepAMinimumDocumentSet() throws IOException {
        compliant();
        files.remove("README.md");
        files.remove("README-zh_CN.md");
        files.remove("documents/release/changelog-example.md");
        files.remove("documents/usage/installation-basics.md");
        assertEquals(
                List.of("D-17 README.md", "D-17 documents/release", "D-17 documents/usage"),
                run(Map.of()).stream()
                        .filter(finding -> finding.rule() == Rule.D17)
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }
}
