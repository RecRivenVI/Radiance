package io.github.recrivenvi.configuration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

final class Repositories {
    static final String NEOFORGE = "26.2-neoforge";
    static final String FABRIC = "26.3-fabric";
    static final File ROOT = new File("repository").getAbsoluteFile();
    static final String PROJECT =
            """
            mod_id=examplemod
            mod_name=Example Mod
            mod_version=0.1.0
            mod_group=io.github.example
            mod_side=both
            mod_authors=Example
            mod_license=MIT
            mod_description=Example description
            org.gradle.jvmargs=-Xmx1G
            """;
    static final String TARGET_FACTS = "loader_version=26.2.0.1\njava_version=25\n";

    private Repositories() {}

    static RepositoryConfiguration daily(String shared, String local) {
        return create(PROJECT, shared, local);
    }

    static RepositoryConfiguration create(String project, String shared, String local) {
        return create(project, "", shared, local, Map.of());
    }

    static RepositoryConfiguration create(
            String project,
            String inputs,
            String shared,
            String local,
            Map<String, String> extraTargets) {
        return create(project, inputs, shared, local, extraTargets, Map.of());
    }

    static RepositoryConfiguration create(
            String project,
            String inputs,
            String shared,
            String local,
            Map<String, String> extraTargets,
            Map<String, String> validations) {
        Map<String, Map<String, Object>> tomls = new LinkedHashMap<>();
        validations.forEach(
                (name, text) ->
                        tomls.put(
                                name,
                                TomlFile.parse(
                                        text,
                                        Validation.DIRECTORY
                                                + "/"
                                                + name
                                                + "/"
                                                + Validation.FILE)));
        Map<String, Map<String, String>> targets = new LinkedHashMap<>();
        targets.put(NEOFORGE, properties(TARGET_FACTS));
        targets.put(FABRIC, properties(TARGET_FACTS));
        extraTargets.forEach((name, facts) -> targets.put(name, properties(facts)));
        return new RepositoryConfiguration(
                ROOT,
                properties(project),
                targets,
                TomlFile.parse(inputs, Inputs.FILE),
                TomlFile.parse(shared, DailyInstances.SHARED_FILE),
                TomlFile.parse(local, DailyInstances.LOCAL_FILE),
                tomls);
    }

    static Map<String, String> properties(String text) {
        return PropertiesFile.parse(text, "test.properties");
    }
}
