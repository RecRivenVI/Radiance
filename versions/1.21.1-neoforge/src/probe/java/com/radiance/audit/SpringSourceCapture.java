package com.radiance.audit;

import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;

/** Exact Veil-processed source from one isolated original GL compiler invocation. */
public final class SpringSourceCapture {
    private static final boolean ENABLED =
            "1".equals(ExperimentAccess.getenv("RADIANCE_SPRING_SOURCE_CAPTURE"));

    private SpringSourceCapture() {}

    public static void capture(int type, VeilShaderSource source) {
        if (!ENABLED
                || source == null
                || source.sourceId() == null
                || !source.sourceId().toString().equals("simulated:spring/spring")) return;
        Path game = Path.of(System.getProperty("user.dir"));
        if (!Files.isRegularFile(game.resolve(".radiance-audit-test-instance"))
                || !Files.isRegularFile(game.resolve(".radiance-acceptance")))
            throw new IllegalStateException("Spring source capture requires isolated markers");
        String stage =
                switch (type) {
                    case 0x8B31 -> "vertex";
                    case 0x8B30 -> "fragment";
                    default ->
                            throw new IllegalStateException(
                                    "Unexpected spring shader stage " + type);
                };
        Path directory = game.resolve("radiance-audit/spring-source");
        try {
            Files.createDirectories(directory);
            byte[] bytes = source.sourceCode().getBytes(StandardCharsets.UTF_8);
            Files.write(directory.resolve(stage + ".glsl"), bytes, StandardOpenOption.CREATE_NEW);
            String hash =
                    java.util.HexFormat.of()
                            .withUpperCase()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String metadata =
                    "stage="
                            + stage
                            + "\nsourceId="
                            + source.sourceId()
                            + "\nincludes="
                            + source.includes()
                            + "\ndefinitionDependencies="
                            + source.definitionDependencies()
                            + "\nuniformBindings="
                            + source.uniformBindings()
                            + "\nutf8Bytes="
                            + bytes.length
                            + "\nsha256="
                            + hash
                            + '\n';
            Files.writeString(
                    directory.resolve(stage + ".metadata.txt"),
                    metadata,
                    StandardOpenOption.CREATE_NEW);
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "SPRING_SOURCE captured original GL {} sha256={} bytes={} includes={}",
                            stage,
                            hash,
                            bytes.length,
                            source.includes());
        } catch (FileAlreadyExistsException repeatedStage) {
            // The first compiled source is the immutable reference for this one-shot process.
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot retain original Veil spring source", failure);
        }
    }
}
