package com.radiance.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import net.neoforged.neoforgespi.ILaunchContext;

final class BootstrapResources {
    private static final String RUNTIME_INDEX = "/META-INF/radiance/runtime.index";
    private static final Object LOAD_LOCK = new Object();
    private static volatile Path runtimeDirectory;
    private static volatile boolean nativeLoaded;

    private BootstrapResources() {}

    static Path gameDirectory(ILaunchContext context) {
        return context.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseGet(() -> Path.of("."))
                .toAbsolutePath().normalize();
    }

    static Path prepareAndLoad(Path gameDirectory) {
        synchronized (LOAD_LOCK) {
            if (runtimeDirectory == null) {
                runtimeDirectory = extractRuntime(gameDirectory);
            }
            if (!nativeLoaded) {
                loadNativeLibraries(runtimeDirectory);
                nativeLoaded = true;
            }
            return runtimeDirectory;
        }
    }

    static Path runtimeDirectory() {
        return runtimeDirectory;
    }

    private static Path extractRuntime(Path gameDirectory) {
        byte[] indexBytes;
        try (InputStream stream = BootstrapResources.class.getResourceAsStream(RUNTIME_INDEX)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bootstrap runtime index " + RUNTIME_INDEX);
            }
            indexBytes = stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read the bootstrap runtime index", e);
        }

        String runtimeId = sha256(indexBytes);
        Path root = gameDirectory.resolve(".radiance").resolve("runtime").resolve(runtimeId);
        Path lockPath = root.getParent().resolve(runtimeId + ".lock");
        try {
            Files.createDirectories(root);
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                for (ResourceEntry entry : parseIndex(indexBytes)) {
                    Path target = safeResolve(root, entry.path());
                    copyVerifiedResource("/" + entry.path(), target, entry.sha256());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to extract the Radiance runtime into " + root, e);
        }
        return root;
    }

    static void copyVerifiedResource(String resourceName, Path target, String expectedSha256)
            throws IOException {
        if (Files.isRegularFile(target)) {
            String actual = sha256(target);
            if (!actual.equals(expectedSha256)) {
                throw new IOException("Content-addressed file has unexpected contents: " + target);
            }
            return;
        }

        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part-"
                + ProcessHandle.current().pid() + "-" + Thread.currentThread().threadId());
        try (InputStream source = BootstrapResources.class.getResourceAsStream(resourceName)) {
            if (source == null) {
                throw new IOException("Missing embedded resource " + resourceName);
            }
            Files.copy(source, temporary);
        }
        String actual = sha256(temporary);
        if (!actual.equals(expectedSha256)) {
            throw new IOException("Embedded resource failed SHA-256 validation: " + resourceName);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target);
        }
    }

    private static void loadNativeLibraries(Path directory) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            Path xess = directory.resolve("libxess.dll");
            if (Files.isRegularFile(xess)) {
                System.load(xess.toString());
            }
            Path core = directory.resolve("core.dll");
            System.load(core.toString());
        } else if (os.contains("linux")) {
            Path core = directory.resolve("libcore.so");
            System.load(core.toString());
        } else {
            throw new IllegalStateException("Radiance bootstrap does not support " + os);
        }
    }

    private static List<ResourceEntry> parseIndex(byte[] bytes) {
        List<ResourceEntry> result = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(bytes), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator != 64) {
                    throw new IllegalStateException("Invalid runtime index entry: " + line);
                }
                String hash = line.substring(0, separator);
                String path = line.substring(separator + 2);
                if (!hash.matches("[0-9a-f]{64}") || path.isBlank()) {
                    throw new IllegalStateException("Invalid runtime index entry: " + line);
                }
                result.add(new ResourceEntry(hash, path));
            }
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        if (result.isEmpty()) throw new IllegalStateException("Radiance runtime index is empty");
        return List.copyOf(result);
    }

    private static Path safeResolve(Path root, String relative) {
        Path target = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalStateException("Unsafe embedded runtime path: " + relative);
        }
        return target;
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = stream.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private record ResourceEntry(String sha256, String path) {}
}
