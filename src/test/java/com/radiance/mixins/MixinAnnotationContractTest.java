package com.radiance.mixins;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MixinAnnotationContractTest {
    private static final Pattern UNIQUE_SHADOW = Pattern.compile(
        "@Unique\\s+(?:@Final\\s+)?@Shadow|@Shadow\\s+(?:@Final\\s+)?@Unique");

    @Test
    void shadowMembersAreNeverMarkedUnique() throws IOException {
        Path mixins = Path.of("src", "main", "java", "com", "radiance", "mixins");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(mixins)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    if (UNIQUE_SHADOW.matcher(Files.readString(path, StandardCharsets.UTF_8))
                        .find()) {
                        violations.add(path.toString());
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        assertTrue(violations.isEmpty(), () -> "Mixin members cannot be both @Unique and @Shadow: "
            + violations);
    }
}
