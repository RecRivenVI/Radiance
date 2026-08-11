package io.github.recrivenvi.conventions.platform;

import io.github.recrivenvi.conventions.configuration.Json;
import io.github.recrivenvi.conventions.configuration.RepositoryConfiguration;
import io.github.recrivenvi.conventions.configuration.SideRequirement;
import java.util.LinkedHashMap;
import java.util.Map;

final class Metadata {
    private Metadata() {}

    static Map<String, String> of(RepositoryConfiguration repository, String target) {
        Map<String, String> values = new LinkedHashMap<>(repository.metadata(target));
        SideRequirement client = repository.getClientSide();
        SideRequirement server = repository.getServerSide();
        values.put("fabric_environment", fabricEnvironment(server));
        values.put("display_test", displayTest(client, server));
        Map<String, String> escaped = new LinkedHashMap<>();
        values.forEach((key, value) -> escaped.put(key, escape(value)));
        return escaped;
    }

    // Fabric 的 environment 只能排除独立服务端；服务端可选或必装时两端都加载。
    static String fabricEnvironment(SideRequirement server) {
        return server == SideRequirement.UNUSED ? "client" : "*";
    }

    // Forge 的显示标记：MATCH_VERSION 要求两端版本相同；IGNORE_ALL_VERSION 接受任何服务端，
    // 但装在服务端时没装的客户端会看到多出的模组；IGNORE_SERVER_VERSION 接受任何服务端，
    // 装在服务端时也不要求客户端安装。
    static String displayTest(SideRequirement client, SideRequirement server) {
        if (client == SideRequirement.REQUIRED && server == SideRequirement.REQUIRED)
            return "MATCH_VERSION";
        return client == SideRequirement.REQUIRED ? "IGNORE_ALL_VERSION" : "IGNORE_SERVER_VERSION";
    }

    static String escape(String value) {
        String quoted = Json.quote(value);
        return quoted.substring(1, quoted.length() - 1);
    }
}
