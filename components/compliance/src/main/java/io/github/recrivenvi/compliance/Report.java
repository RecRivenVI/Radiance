package io.github.recrivenvi.compliance;

import java.util.ArrayList;
import java.util.List;

final class Report {
    private final List<Finding> findings;
    private final boolean strict;
    private final int files;

    Report(List<Finding> findings, boolean strict, int files) {
        this.findings = findings;
        this.strict = strict;
        this.files = files;
    }

    long failures() {
        return findings.stream()
                .filter(finding -> finding.rule().level() == Rule.Level.FAIL)
                .count();
    }

    long warnings() {
        return findings.size() - failures();
    }

    boolean passed() {
        return failures() == 0 && (!strict || warnings() == 0);
    }

    String summary() {
        return "规范 "
                + SpecificationChecks.VERSION
                + " 合规检查"
                + (strict ? "（严格模式）" : "")
                + "：失败 "
                + failures()
                + "，警告 "
                + warnings()
                + "，检查了 "
                + files
                + " 个文件";
    }

    List<String> consoleLines() {
        List<String> lines = new ArrayList<>();
        for (Finding finding : findings)
            lines.add(
                    finding.rule().level().label()
                            + " "
                            + finding.rule().id()
                            + " "
                            + finding.location()
                            + ": "
                            + finding.message());
        return lines;
    }

    List<String> annotations() {
        List<String> lines = new ArrayList<>();
        for (Finding finding : findings) {
            String command =
                    finding.rule().level() == Rule.Level.FAIL || strict ? "error" : "warning";
            StringBuilder line =
                    new StringBuilder("::")
                            .append(command)
                            .append(" file=")
                            .append(property(finding.path()));
            if (finding.line() > 0) line.append(",line=").append(finding.line());
            line.append(",title=")
                    .append(property(finding.rule().id() + " " + finding.rule().title()))
                    .append("::")
                    .append(message(finding.message()));
            lines.add(line.toString());
        }
        return lines;
    }

    String markdown() {
        StringBuilder markdown = new StringBuilder("## 合规检查\n\n").append(summary()).append("\n");
        if (findings.isEmpty()) return markdown.toString();
        markdown.append("\n| 级别 | 规则 | 位置 | 说明 |\n| --- | --- | --- | --- |\n");
        for (Finding finding : findings)
            markdown.append("| ")
                    .append(finding.rule().level().label())
                    .append(" | ")
                    .append(finding.rule().id())
                    .append(" | `")
                    .append(finding.location())
                    .append("` | ")
                    .append(finding.message().replace("|", "\\|"))
                    .append(" |\n");
        return markdown.toString();
    }

    String json() {
        StringBuilder json = new StringBuilder("{\n");
        json.append("    \"specification\": ")
                .append(quote(SpecificationChecks.VERSION))
                .append(",\n");
        json.append("    \"strict\": ").append(strict).append(",\n");
        json.append("    \"passed\": ").append(passed()).append(",\n");
        json.append("    \"failures\": ").append(failures()).append(",\n");
        json.append("    \"warnings\": ").append(warnings()).append(",\n");
        json.append("    \"files\": ").append(files).append(",\n");
        json.append("    \"findings\": [");
        for (int i = 0; i < findings.size(); i++) {
            Finding finding = findings.get(i);
            json.append(i == 0 ? "\n" : ",\n")
                    .append("        {\"rule\": ")
                    .append(quote(finding.rule().id()))
                    .append(", \"level\": ")
                    .append(quote(finding.rule().level().name()))
                    .append(", \"path\": ")
                    .append(quote(finding.path()))
                    .append(", \"line\": ")
                    .append(finding.line())
                    .append(", \"message\": ")
                    .append(quote(finding.message()))
                    .append("}");
        }
        return json.append(findings.isEmpty() ? "]\n}\n" : "\n    ]\n}\n").toString();
    }

    private static String property(String value) {
        return message(value).replace(":", "%3A").replace(",", "%2C");
    }

    private static String message(String value) {
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) builder.append("\\u%04x".formatted((int) character));
                    else builder.append(character);
                }
            }
        }
        return builder.append('"').toString();
    }
}
