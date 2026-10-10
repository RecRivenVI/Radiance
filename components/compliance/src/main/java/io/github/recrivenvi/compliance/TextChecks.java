package io.github.recrivenvi.compliance;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

final class TextChecks {
    // cmd.exe 读取 LF 换行的批处理文件时会弄错标签与 GOTO 目标。
    static final Set<String> WINDOWS = Set.of("bat", "cmd");

    private TextChecks() {}

    static void check(Repository repository, List<Finding> findings) {
        for (String file : repository.files()) {
            if (Repository.result(file)) continue;
            byte[] bytes = repository.bytes(file);
            if (repository.binary(file, bytes)) continue;
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb
                    && (bytes[2] & 0xff) == 0xbf)
                findings.add(Finding.of(Rule.C01, file, "删除字节顺序标记"));
            String text;
            try {
                text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
            } catch (CharacterCodingException e) {
                findings.add(Finding.of(Rule.C01, file, "不是有效的 UTF-8"));
                continue;
            }
            lineEndings(file, text, findings);
            whitespace(file, text, findings);
        }
        wrapper(repository, findings);
    }

    private static void lineEndings(String file, String text, List<Finding> findings) {
        boolean windows = WINDOWS.contains(file.substring(file.lastIndexOf('.') + 1));
        String lineBreak = windows ? "\r\n" : "\n";
        String stray = windows ? text.replace("\r\n", "") : text.replace("\n", "");
        if (stray.indexOf('\r') >= 0 || (windows && stray.indexOf('\n') >= 0))
            findings.add(Finding.of(Rule.C02, file, windows ? "使用 CRLF 换行" : "使用 LF 换行"));
        if (text.isEmpty()) return;
        if (!text.endsWith(lineBreak) || text.endsWith(lineBreak + lineBreak))
            findings.add(Finding.of(Rule.C03, file, "文件以一个换行结束"));
    }

    private static void whitespace(String file, String text, List<Finding> findings) {
        List<String> lines = text.replace("\r", "").lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.indexOf('\t') >= 0
                    || (!line.isEmpty()
                            && Character.isWhitespace(line.charAt(line.length() - 1)))) {
                findings.add(new Finding(Rule.C04, file, i + 1, "删除制表符与行尾空白"));
                return;
            }
        }
    }

    private static void wrapper(Repository repository, List<Finding> findings) {
        String properties = "gradle/wrapper/gradle-wrapper.properties";
        if (repository.exists(properties)
                && !repository.text(properties).contains("distributionSha256Sum="))
            findings.add(Finding.of(Rule.C06, properties, "固定 distributionSha256Sum"));
        String mode = repository.gitMode("gradlew");
        if (mode != null && !mode.equals("100755"))
            findings.add(
                    Finding.of(
                            Rule.C06,
                            "gradlew",
                            "在 Git 中把 gradlew 标记为可执行：git add --chmod=+x gradlew"));
    }
}
