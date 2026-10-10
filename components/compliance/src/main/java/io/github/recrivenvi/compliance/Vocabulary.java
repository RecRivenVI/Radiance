package io.github.recrivenvi.compliance;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Vocabulary implements Serializable {
    public static final Map<String, List<String>> BUILT_IN = builtIn();

    private final Map<String, Set<String>> additions;

    public Vocabulary(Map<String, List<String>> additions) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        additions.forEach((location, words) -> copy.put(location, new LinkedHashSet<>(words)));
        this.additions = copy;
    }

    public boolean suggests(String location, String word) {
        return BUILT_IN.getOrDefault(location, List.of()).contains(word)
                || additions.getOrDefault(location, Set.of()).contains(word);
    }

    public Set<String> additions(String location) {
        return additions.getOrDefault(location, Set.of());
    }

    public String hint(String location, String word) {
        Set<String> candidates = new LinkedHashSet<>(BUILT_IN.getOrDefault(location, List.of()));
        candidates.addAll(additions(location));
        String nearest = nearest(word, candidates);
        return nearest == null ? "建议词：" + String.join(", ", candidates) : "最接近的建议词：" + nearest;
    }

    static String nearest(String word, Collection<String> candidates) {
        String best = null;
        int bestDistance = 3;
        for (String candidate : candidates) {
            int distance = distance(word, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] =
                        Math.min(
                                Math.min(current[j - 1] + 1, previous[j] + 1),
                                previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static Map<String, List<String>> builtIn() {
        Map<String, List<String>> words = new LinkedHashMap<>();
        words.put(
                "documents/usage",
                List.of(
                        "installation",
                        "configuration",
                        "guide",
                        "feature",
                        "command",
                        "compatibility",
                        "troubleshooting",
                        "faq",
                        "limitation"));
        words.put(
                "documents/development",
                List.of(
                        "setup",
                        "build",
                        "workflow",
                        "testing",
                        "debugging",
                        "porting",
                        "contribution",
                        "compatibility",
                        "procedure"));
        words.put(
                "documents/design",
                List.of(
                        "overview",
                        "concept",
                        "principle",
                        "architecture",
                        "mechanism",
                        "flow",
                        "lifecycle",
                        "security"));
        words.put(
                "documents/project",
                List.of(
                        "overview",
                        "scope",
                        "proposal",
                        "roadmap",
                        "milestone",
                        "plan",
                        "status",
                        "backlog",
                        "debt",
                        "handoff"));
        words.put(
                "documents/reference",
                List.of(
                        "api",
                        "protocol",
                        "schema",
                        "registry",
                        "command",
                        "configuration",
                        "event",
                        "format",
                        "version",
                        "dependency",
                        "upstream",
                        "glossary",
                        "example"));
        words.put(
                "documents/research",
                List.of("survey", "investigation", "audit", "review", "experiment", "incident"));
        words.put(
                "documents/release",
                List.of("release", "changelog", "upgrade", "migration", "support", "deprecation"));
        words.put(
                "validations",
                List.of(
                        "smoke",
                        "feature",
                        "visual",
                        "regression",
                        "integration",
                        "compatibility",
                        "parity",
                        "conformance",
                        "performance",
                        "stability",
                        "migration",
                        "security"));
        return Collections.unmodifiableMap(words);
    }
}
