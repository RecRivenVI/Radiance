package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Checks compiled field and method signatures, including nested records and generic types. */
class PortableContractTest {
    @Test
    void allPublicContractsHaveOnlyValuesAndTypedHandles() throws Exception {
        var directory =
                java.nio.file.Path.of(
                                TakeoverSubmission.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .resolve("com/radiance/render/contract");
        var seen = new HashSet<Class<?>>();
        try (var files = java.nio.file.Files.list(directory)) {
            for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = file.getFileName().toString().replaceFirst("\\.class$", "");
                checkBytecode(java.nio.file.Files.readAllBytes(file));
                check(
                        Class.forName(
                                "com.radiance.render.contract." + name,
                                false,
                                TakeoverSubmission.class.getClassLoader()),
                        seen);
            }
        }
        assertTrue(seen.contains(TakeoverSubmission.Program.class));
        assertThrows(AssertionError.class, () -> check(UnsafeGameObject.class, new HashSet<>()));
    }

    record UnsafeGameObject(Object gameObject) {}

    private static void check(Type type, Set<Class<?>> seen) {
        if (type instanceof ParameterizedType value) {
            check(value.getRawType(), seen);
            for (var argument : value.getActualTypeArguments()) check(argument, seen);
        } else if (type instanceof GenericArrayType value) {
            check(value.getGenericComponentType(), seen);
        } else if (type instanceof TypeVariable<?> value) {
            for (var bound : value.getBounds()) check(bound, seen);
        } else if (type instanceof WildcardType value) {
            for (var bound : value.getUpperBounds()) check(bound, seen);
            for (var bound : value.getLowerBounds()) check(bound, seen);
        } else if (type instanceof Class<?> value) {
            if (value.isArray()) {
                check(value.componentType(), seen);
                return;
            }
            if (value.isPrimitive()) return;
            // Object in record-generated equals(Object) is unavoidable; mutable payloads are not.
            assertNotEquals(Object.class, value, "Use a typed value or resource handle");
            String name = value.getName();
            if (name.startsWith("java.")) return;
            assertTrue(name.startsWith("com.radiance.render.contract."), name);
            if (!seen.add(value)) return;
            for (var field : value.getDeclaredFields()) {
                if (!field.isSynthetic()) check(field.getGenericType(), seen);
            }
            for (var constructor : value.getDeclaredConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers()))
                    for (var parameter : constructor.getGenericParameterTypes())
                        check(parameter, seen);
            }
            for (var method : value.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        || method.isSynthetic()
                        || method.getName().equals("equals")
                        || method.getName().equals("compareTo")) continue;
                check(method.getGenericReturnType(), seen);
                for (var parameter : method.getGenericParameterTypes()) check(parameter, seen);
            }
        } else fail("Unrecognized contract type " + type);
    }

    private static void checkBytecode(byte[] bytes) {
        new org.objectweb.asm.ClassReader(bytes)
                .accept(
                        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override
                            public void visit(
                                    int version,
                                    int access,
                                    String name,
                                    String signature,
                                    String parent,
                                    String[] interfaces) {
                                owner(parent);
                                for (var implemented : interfaces) owner(implemented);
                            }

                            @Override
                            public org.objectweb.asm.MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions) {
                                return new org.objectweb.asm.MethodVisitor(
                                        org.objectweb.asm.Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String target,
                                            String method,
                                            String desc,
                                            boolean itf) {
                                        owner(target);
                                    }

                                    @Override
                                    public void visitFieldInsn(
                                            int opcode, String target, String field, String desc) {
                                        owner(target);
                                    }

                                    @Override
                                    public void visitTypeInsn(int opcode, String target) {
                                        owner(target);
                                    }
                                };
                            }

                            private void owner(String name) {
                                if (name == null) return;
                                if (name.startsWith("[")) {
                                    var type =
                                            org.objectweb.asm.Type.getType(name).getElementType();
                                    if (type.getSort() != org.objectweb.asm.Type.OBJECT) return;
                                    name = type.getInternalName();
                                }
                                assertTrue(
                                        name.startsWith("java/")
                                                || name.startsWith("com/radiance/render/contract/"),
                                        name);
                            }
                        },
                        org.objectweb.asm.ClassReader.SKIP_DEBUG
                                | org.objectweb.asm.ClassReader.SKIP_FRAMES);
    }
}
