package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.Json;
import io.github.recrivenvi.configuration.RepositoryConfiguration;
import io.github.recrivenvi.configuration.Side;
import java.util.LinkedHashMap;
import java.util.Map;

final class Metadata {
    private Metadata() {}

    static Map<String, String> of(RepositoryConfiguration repository, String target) {
        Map<String, String> values = new LinkedHashMap<>(repository.metadata(target));
        Side side = repository.getSide();
        values.put("fabric_environment", side == Side.CLIENT ? "client" : "*");
        values.put(
                "display_test",
                switch (side) {
                    case BOTH -> "MATCH_VERSION";
                    case CLIENT -> "IGNORE_ALL_VERSION";
                    case SERVER -> "IGNORE_SERVER_VERSION";
                });
        Map<String, String> escaped = new LinkedHashMap<>();
        values.forEach((key, value) -> escaped.put(key, escape(value)));
        return escaped;
    }

    static String escape(String value) {
        String quoted = Json.quote(value);
        return quoted.substring(1, quoted.length() - 1);
    }
}
