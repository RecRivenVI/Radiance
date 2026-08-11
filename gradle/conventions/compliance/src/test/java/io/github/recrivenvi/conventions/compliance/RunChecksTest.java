package io.github.recrivenvi.conventions.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

// 第 7 节"运行与测试"。
class RunChecksTest extends ComplianceFixture {
    @Test
    void instancesAreLimitedToRegisteredTargets() throws IOException {
        compliant();
        files.put("instances/" + target + "/client/options.txt", "generated\r\n");
        files.put("instances/" + target + "/debug/options.txt", "x\n");
        files.put("instances/1.0-forge/client/options.txt", "x\n");
        files.put("instances/notes.txt", "x\n");
        List<Finding> findings = run(Map.of());
        assertEquals(
                List.of(
                        "I-04 instances/1.0-forge",
                        "I-04 instances/" + target + "/debug",
                        "I-04 instances/notes.txt"),
                findings.stream()
                        .map(finding -> finding.rule().id() + " " + finding.path())
                        .sorted()
                        .toList());
        Map<String, String> messages = new TreeMap<>();
        for (Finding finding : findings) messages.put(finding.path(), finding.message());
        assertTrue(
                messages.get("instances/1.0-forge").startsWith("不是已登记的 Target；")
                        && messages.get("instances/1.0-forge").contains("经使用者同意后移出仓库或删除"),
                messages.toString());
        assertTrue(
                messages.get("instances/" + target + "/debug").startsWith("不是已登记的实例（client、"),
                messages.toString());
        assertEquals("这里只能有已登记 Target 的实例目录", messages.get("instances/notes.txt"));
    }

    @Test
    void fileManagerLeftoversAreIgnored() throws IOException {
        compliant();
        files.put("instances/" + target + "/client/options.txt", "x\n");
        files.put("instances/.DS_Store", "x");
        files.put("instances/" + target + "/desktop.ini", "x\n");
        assertEquals(List.of(), run(Map.of()));
    }

    @Test
    void customInstancesAreAllowed() throws IOException {
        compliant();
        instances = Set.of("client", "client-multiplayer", "server", "secondary-skins");
        files.put("instances/" + target + "/secondary-skins/options.txt", "x\n");
        assertEquals(List.of(), run(Map.of()));
        files.put("instances/" + target + "/primary-skins/options.txt", "x\n");
        assertEquals(Set.of("I-04"), rules());
    }
}
