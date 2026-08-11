package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 第 5 节"Target"。
class TargetChecksTest extends ComplianceFixture {
    @Test
    void targetScriptsTakeVersionsFromFacts() throws IOException {
        compliant();
        String script = "versions/" + target + "/build.gradle.kts";
        files.put(
                script,
                files.get(script)
                        + "\ndependencies {\n"
                        + "    compileOnly(target.module(\"maven.modrinth:sodium\", \"sodium_version\"))\n"
                        + "    implementation(libs.snakeyaml)\n}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                script,
                files.get(script)
                        + "dependencies {\n"
                        + "    compileOnly(\"maven.modrinth:iris:\" + target.fact(\"iris_version\"))\n}\n");
        assertEquals(Set.of("T-02"), rules());
    }

    @Test
    void targetScriptVersionsAreFoundInStringsAndCode() throws IOException {
        compliant();
        String script = "versions/" + target + "/build.gradle.kts";
        String plugins = files.get(script);
        files.put(
                script,
                plugins
                        + "\n// version \"2.0\" of the run setup\n"
                        + "println(project.version)\n"
                        + "val url = \"https://maven.example.com:8443/releases\" // 1.21\n"
                        + "repositories {\n"
                        + "    maven(\"https://192.168.1.10/repository/releases\")\n"
                        + "}\n"
                        + "/* The loader \"0.19.5\" comes from\n   target.properties. */\n"
                        + "dependencies {\n"
                        + "    compileOnly(target.module(\"maven.modrinth:sodium\", \"sodium_version\"))\n"
                        + "}\n");
        assertEquals(List.of(), run(Map.of()));
        files.put(
                script,
                plugins
                        + "val requiredMinecraft = \"1.21.1\"\n"
                        + "dependencies {\n"
                        + "    compileOnly(\"maven.modrinth:iris:${target.fact(\"iris_version\")}\")\n"
                        + "}\n"
                        + "val sodium = \"maven.modrinth:sodium:$sodiumVersion\"\n");
        List<Finding> findings = run(Map.of());
        assertEquals(Set.of("T-02"), ids(findings));
        assertEquals(List.of(4, 6, 8), findings.stream().map(Finding::line).sorted().toList());
    }

    @Test
    void identicalPlainJavaAcrossTargetsIsHinted() throws IOException {
        compliant();
        String other = "1.21.1-neoforge";
        targets = Set.of(target, other);
        String plain = "package io.github.example.math;\n\npublic final class Curve {}\n";
        String game =
                "package io.github.example;\n\nimport net.minecraft.world.level.Level;\n\nclass Hook {}\n";
        for (String version : List.of(target, other)) {
            String sources = "versions/" + version + "/src/main/java/io/github/example/";
            files.put(sources + "math/Curve.java", plain);
            files.put(sources + "Hook.java", game);
            files.put(sources + "package-info.java", "package io.github.example;\n");
            files.put(
                    "versions/" + version + "/src/probe/java/io/github/example/probe/Results.java",
                    plain.replace("math", "probe"));
        }
        List<Finding> hints =
                run(Map.of()).stream().filter(finding -> finding.rule() == Rule.T07).toList();
        assertEquals(
                List.of("versions/" + other + "/src/main/java/io/github/example/math/Curve.java"),
                hints.stream().map(Finding::path).toList());
        assertTrue(new Report(hints, false, files.size()).passed());
    }
}
