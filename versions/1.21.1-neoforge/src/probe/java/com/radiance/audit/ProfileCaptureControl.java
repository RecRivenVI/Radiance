package com.radiance.audit;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** One bounded request file for an explicitly enabled, local validation session. */
final class ProfileCaptureControl {
    record Request(String id, int seconds, String label) {}

    private ProfileCaptureControl() {}

    static Request read(Path file, String previousId, long processStartedAt) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(1025);
        }
        if (bytes.length > 1024) throw new IOException("Profile request exceeds 1024 bytes");
        var values = new Properties();
        values.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        String id = values.getProperty("id", "");
        String label = values.getProperty("label", "");
        if (!id.matches("[a-zA-Z0-9_-]{1,64}") || !label.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new IOException("Profile request requires a bounded id and scene label");
        int seconds;
        long requestedAt;
        try {
            seconds = Integer.parseInt(values.getProperty("seconds", ""));
            requestedAt = Long.parseLong(values.getProperty("requestedAt", ""));
        } catch (NumberFormatException invalid) {
            throw new IOException(
                    "Profile request duration and timestamp must be integers", invalid);
        }
        if (seconds < 1 || seconds > 120)
            throw new IOException("Profile request duration must be within 1..120 seconds");
        return id.equals(previousId) || requestedAt < processStartedAt
                ? null
                : new Request(id, seconds, label);
    }
}
