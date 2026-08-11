package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 10 节"代码与格式"。
class CodeChecksTest extends ComplianceFixture {
    @Test
    void textViolationsAreReported() throws IOException {
        compliant();
        files.put("documents/development/setup-bom.md", "﻿# 配置\n");
        files.put("documents/development/setup-crlf.md", "# 配置\r\n");
        files.put("documents/development/setup-end.md", "# 配置\n\n");
        files.put("documents/development/setup-space.md", "# 配置 \n");
        files.put("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=x\n");
        assertEquals(Set.of("C-01", "C-02", "C-03", "C-04", "C-06"), rules());
    }

    @Test
    void gitBinaryAttributesSkipTextRules() throws IOException, InterruptedException {
        assumeTrue(git("init", "-q") == 0, "git is not available");
        compliant();
        component("archive_format");
        Path noise = root.resolve("components/archive_format/data/noise.raw");
        Files.createDirectories(noise.getParent());
        Files.write(noise, new byte[] {(byte) 0xff, (byte) 0xfd, (byte) 0xfb, ' ', '\n'});
        assertEquals(Set.of("C-01"), rules());
        files.put(".gitattributes", "* text=auto eol=lf\n*.raw binary\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void onlyBatchFilesUseCrlf() throws IOException {
        compliant();
        component("acceptance_gates");
        files.put("components/acceptance_gates/scripts/run.cmd", "@echo off\r\n");
        files.put("components/acceptance_gates/scripts/run.ps1", "Write-Output 1\n");
        assertEquals(List.of(), run(Map.of()));
        files.put("components/acceptance_gates/scripts/run.cmd", "@echo off\n");
        files.put("components/acceptance_gates/scripts/run.ps1", "Write-Output 1\r\n");
        assertEquals(
                List.of(
                        "C-02 components/acceptance_gates/scripts/run.cmd",
                        "C-02 components/acceptance_gates/scripts/run.ps1",
                        "C-03 components/acceptance_gates/scripts/run.cmd"),
                run(Map.of()).stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
    }
}
