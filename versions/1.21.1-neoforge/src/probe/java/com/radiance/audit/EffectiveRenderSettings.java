package com.radiance.audit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Snapshot current module values without reloading or mutating configuration. */
final class EffectiveRenderSettings {
    private EffectiveRenderSettings() {}

    static void checkpoint(Minecraft mc, String label) throws Exception {
        String input = System.getProperty("radiance.audit.expectedRenderingConfig");
        if (input == null) return;
        JsonObject expected =
                JsonParser.parseString(Files.readString(Path.of(input))).getAsJsonObject();
        var modules = new LinkedHashMap<String, Map<String, String>>();
        for (var module : Pipeline.INSTANCE.getModules()) {
            var values = new LinkedHashMap<String, String>();
            for (var attribute : module.attributeConfigs)
                values.put(attribute.name, attribute.value);
            if (modules.put(module.name, values) != null)
                throw new IllegalStateException("Ambiguous module identity");
        }
        var options = new LinkedHashMap<String, String>();
        for (var key : expected.getAsJsonObject("options").keySet())
            options.put(key, String.valueOf(Options.class.getField(key).get(null)));
        var packs = List.copyOf(mc.getResourcePackRepository().getSelectedIds());
        var actual =
                new Gson()
                        .toJsonTree(
                                Map.of(
                                        "modules",
                                        modules,
                                        "options",
                                        options,
                                        "resourcePacks",
                                        packs))
                        .getAsJsonObject();
        Files.writeString(
                Path.of(System.getProperty("validation.result"), "effective-" + label + ".json"),
                actual + "\n");
        for (var entry : expected.getAsJsonObject("modules").entrySet())
            compare(
                    entry.getValue().getAsJsonObject(),
                    actual.getAsJsonObject("modules").getAsJsonObject(entry.getKey()),
                    entry.getKey());
        compare(expected.getAsJsonObject("options"), actual.getAsJsonObject("options"), "options");
        if (!expected.get("resourcePacks").equals(actual.get("resourcePacks")))
            throw new IllegalStateException(
                    "Effective resource pack selection differs from frozen baseline");
    }

    static void compare(JsonObject expected, JsonObject actual, String scope) {
        if (actual == null) throw new IllegalStateException("Missing effective " + scope);
        for (var value : expected.entrySet())
            if (!value.getValue().equals(actual.get(value.getKey())))
                throw new IllegalStateException(
                        "Effective rendering setting differs: "
                                + scope
                                + "/"
                                + value.getKey()
                                + " expected="
                                + value.getValue()
                                + " actual="
                                + actual.get(value.getKey()));
    }
}
