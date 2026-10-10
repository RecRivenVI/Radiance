package io.github.recrivenvi.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Markdown {
    private static final Pattern LINK =
            Pattern.compile("\\[[^\\]]*]\\(<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern IMAGE =
            Pattern.compile("!\\[([^\\]]*)]\\(<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

    record Link(String target, int line) {}

    record Image(String alt, String target, int line) {}

    record Table(List<String> header, List<List<String>> rows, int line) {}

    private final List<String> lines;
    private final boolean[] code;

    Markdown(String text) {
        String content = text.startsWith("﻿") ? text.substring(1) : text;
        this.lines = List.of(content.replace("\r", "").split("\n", -1));
        this.code = new boolean[lines.size()];
        boolean fenced = false;
        for (int i = 0; i < lines.size(); i++) {
            boolean fence = lines.get(i).stripLeading().startsWith("```");
            code[i] = fenced || fence;
            if (fence) fenced = !fenced;
        }
    }

    List<String> lines() {
        return lines;
    }

    boolean inCode(int index) {
        return code[index];
    }

    List<Integer> headings(int level) {
        String prefix = "#".repeat(level) + " ";
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++)
            if (!code[i] && lines.get(i).startsWith(prefix)) found.add(i);
        return found;
    }

    List<String> sectionTitles() {
        List<String> titles = new ArrayList<>();
        for (int index : headings(2)) titles.add(lines.get(index).substring(3).strip());
        return titles;
    }

    List<String> section(String title) {
        List<Integer> sections = headings(2);
        for (int i = 0; i < sections.size(); i++) {
            int start = sections.get(i);
            if (!lines.get(start).substring(3).strip().equals(title)) continue;
            int end = i + 1 < sections.size() ? sections.get(i + 1) : lines.size();
            return lines.subList(start + 1, end);
        }
        return null;
    }

    List<Link> links() {
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (code[i]) continue;
            Matcher matcher = LINK.matcher(INLINE_CODE.matcher(lines.get(i)).replaceAll(""));
            while (matcher.find()) links.add(new Link(matcher.group(1), i + 1));
        }
        return links;
    }

    List<Image> images() {
        List<Image> images = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (code[i]) continue;
            Matcher matcher = IMAGE.matcher(INLINE_CODE.matcher(lines.get(i)).replaceAll(""));
            while (matcher.find())
                images.add(new Image(matcher.group(1).strip(), matcher.group(2), i + 1));
        }
        return images;
    }

    List<String> firstParagraph() {
        List<String> paragraph = new ArrayList<>();
        int i = headings(1).isEmpty() ? 0 : headings(1).getFirst() + 1;
        while (i < lines.size() && lines.get(i).isBlank()) i++;
        while (i < lines.size() && !lines.get(i).isBlank() && !code[i])
            paragraph.add(lines.get(i++));
        return paragraph;
    }

    List<Table> tables() {
        List<Table> tables = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            if (code[i] || !lines.get(i).strip().startsWith("|")) {
                i++;
                continue;
            }
            int start = i;
            List<List<String>> rows = new ArrayList<>();
            while (i < lines.size() && !code[i] && lines.get(i).strip().startsWith("|"))
                rows.add(cells(lines.get(i++)));
            if (rows.size() >= 2)
                tables.add(new Table(rows.getFirst(), rows.subList(2, rows.size()), start + 1));
        }
        return tables;
    }

    static List<String> backticked(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = BACKTICKED.matcher(text);
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }

    private static List<String> cells(String line) {
        String content = line.strip();
        if (content.startsWith("|")) content = content.substring(1);
        if (content.endsWith("|")) content = content.substring(0, content.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : content.split("(?<!\\\\)\\|", -1)) cells.add(cell.strip());
        return cells;
    }
}
