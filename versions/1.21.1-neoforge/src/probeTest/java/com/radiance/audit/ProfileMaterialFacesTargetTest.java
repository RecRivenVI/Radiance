package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Locks the real bytecode targets, including overloads, rather than grepping source names. */
class ProfileMaterialFacesTargetTest {
    private static ClassNode read(String name) throws Exception {
        try (var input =
                ProfileMaterialFacesTargetTest.class
                        .getClassLoader()
                        .getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static Object value(AnnotationNode node, String name) {
        return node.values.get(node.values.indexOf(name) + 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allFourOperationsSelectAnExactLiveCaptureMethod() throws Exception {
        var target = read("com/radiance/client/render/MaterialFaces");
        var mixin = read("com/radiance/audit/mixin/ProfileMaterialFacesMixin");
        int operations = 0;
        for (var method : mixin.methods) {
            var annotations = new java.util.ArrayList<AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null)
                annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) {
                if (!annotation.desc.endsWith("/WrapOperation;")) continue;
                operations++;
                for (String selector : (List<String>) value(annotation, "method")) {
                    var candidates =
                            target.methods.stream()
                                    .filter(m -> (m.name + m.desc).equals(selector))
                                    .toList();
                    assertEquals(
                            1,
                            candidates.size(),
                            "selector must include the exact descriptor: " + selector);
                    var points = (List<AnnotationNode>) value(annotation, "at");
                    assertFalse(points.isEmpty());
                    for (var at : points) {
                        String invocation = (String) value(at, "target");
                        boolean found = false;
                        for (var instruction : candidates.getFirst().instructions) {
                            if (instruction instanceof MethodInsnNode call
                                    && invocation.equals(
                                            "L" + call.owner + ";" + call.name + call.desc))
                                found = true;
                        }
                        assertTrue(found, "missing live operation: " + invocation);
                    }
                }
            }
        }
        assertEquals(4, operations);
    }
}
