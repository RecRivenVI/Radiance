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

    @Test
    void classesThatReachTheGameOrDifferBetweenTargetsStay() throws IOException {
        compliant();
        String second = "1.21.1-neoforge";
        String third = "26.3-fabric";
        targets = Set.of(target, second, third);
        String root = "src/main/java/io/github/example/";
        for (String version : List.of(target, second, third)) {
            String sources = "versions/" + version + "/" + root;
            files.put(
                    sources + "api/Holder.java",
                    "package io.github.example.api;\n\npublic final class Holder {\n"
                            + "    private Provider provider;\n}\n");
            files.put(
                    sources + "api/Provider.java",
                    "package io.github.example.api;\n\nimport net.minecraft.client.model.Model;\n\n"
                            + "public interface Provider {}\n");
            files.put(
                    sources + "util/Lists.java",
                    "package io.github.example.util;\n\nimport io.github.example.api.*;\n\n"
                            + "public final class Lists {}\n");
            files.put(
                    sources + "math/Curve.java",
                    "package io.github.example.math;\n\npublic final class Curve {}\n");
            files.put(
                    sources + "math/Spline.java",
                    "package io.github.example.math;\n\n/** Uses Holder only in this comment. */\n"
                            + "public final class Spline {\n    private Curve curve;\n}\n");
            files.put(
                    sources + "item/Stack.java",
                    "package io.github.example.item;\n\npublic interface Stack {\n"
                            + (version.startsWith("1.") ? "    boolean trident();\n" : "")
                            + "}\n");
            files.put(
                    sources + "item/Living.java",
                    "package io.github.example.item;\n\npublic interface Living {\n"
                            + "    Stack hand();\n}\n");
        }
        List<String> hinted =
                run(Map.of()).stream()
                        .filter(finding -> finding.rule() == Rule.T07)
                        .map(Finding::path)
                        .toList();
        String first = "versions/" + second + "/" + root;
        assertEquals(List.of(first + "math/Curve.java", first + "math/Spline.java"), hinted);
    }

    @Test
    void classesThatUseTemplateSourcesStay() throws IOException {
        compliant();
        String other = "1.21.1-neoforge";
        targets = Set.of(target, other);
        for (String version : List.of(target, other)) {
            String java = "versions/" + version + "/src/main/java/io/github/example/api/";
            String templates = "versions/" + version + "/src/main/templates/io/github/example/api/";
            files.put(
                    java + "Holder.java",
                    "package io.github.example.api;\n\npublic final class Holder {\n"
                            + "    private Provider provider;\n}\n");
            files.put(
                    templates + "Provider.java",
                    "package io.github.example.api;\n\nimport net.minecraft.client.model.Model;\n\n"
                            + "public interface Provider {}\n");
            files.put(
                    java + "Named.java",
                    "package io.github.example.api;\n\npublic final class Named {\n"
                            + "    String id = Identity.ID;\n}\n");
            files.put(
                    templates + "Identity.java",
                    "package io.github.example.api;\n\npublic final class Identity {\n"
                            + "    public static final String ID = \"${mod_id}\";\n}\n");
        }
        assertEquals(
                List.of(),
                run(Map.of()).stream().filter(finding -> finding.rule() == Rule.T07).toList());
    }
}
