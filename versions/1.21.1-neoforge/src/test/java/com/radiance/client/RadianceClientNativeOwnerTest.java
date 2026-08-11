package com.radiance.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

final class RadianceClientNativeOwnerTest {
    @Test
    void everyGameNativeDeclarationHasAServiceToGameBinding() throws Exception {
        Field ownersField = RadianceClient.class.getDeclaredField("GAME_NATIVE_OWNERS");
        ownersField.setAccessible(true);
        var registered =
                Arrays.stream((Class<?>[]) ownersField.get(null))
                        .map(type -> type.getName().replace('.', '/'))
                        .collect(Collectors.toSet());
        Path classes =
                Path.of(
                        RadianceClient.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        assertTrue(Files.isDirectory(classes), "This check scans the compiled GAME source set");
        try (var files = Files.walk(classes.resolve("com/radiance"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var node = new ClassNode();
                new ClassReader(Files.readAllBytes(file))
                        .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                if (node.methods.stream()
                        .anyMatch(method -> (method.access & Opcodes.ACC_NATIVE) != 0))
                    assertTrue(
                            registered.contains(node.name),
                            "Missing GAME native owner: " + node.name);
            }
        }
    }

    @Test
    void publicScreenProgramsAreBoundAcrossServiceAndGameClassLoaders() throws Exception {
        Field owners = RadianceClient.class.getDeclaredField("GAME_NATIVE_OWNERS");
        owners.setAccessible(true);
        assertTrue(
                java.util.Arrays.asList((Class<?>[]) owners.get(null))
                        .contains(com.radiance.api.render.internal.ScreenEffectsNative.class));
    }

    @Test
    void everyRegisteredOwnerActuallyDeclaresANativeMethod() throws Exception {
        Field ownersField = RadianceClient.class.getDeclaredField("GAME_NATIVE_OWNERS");
        ownersField.setAccessible(true);
        for (Class<?> owner : (Class<?>[]) ownersField.get(null)) {
            boolean hasNative = false;
            for (Method method : owner.getDeclaredMethods()) {
                hasNative |= Modifier.isNative(method.getModifiers());
            }
            assertTrue(hasNative, () -> "JNI owner has no native methods: " + owner.getName());
        }
    }
}
