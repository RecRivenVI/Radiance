package io.github.recrivenvi.configuration;

import java.util.Collection;

final class Suggestions {
    private Suggestions() {}

    static String hint(String value, Collection<String> candidates) {
        String best = null;
        int bestDistance = 3;
        for (String candidate : candidates) {
            int distance = distance(value.toLowerCase(), candidate.toLowerCase());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? "" : "；是否想写 " + best + "？";
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
}
