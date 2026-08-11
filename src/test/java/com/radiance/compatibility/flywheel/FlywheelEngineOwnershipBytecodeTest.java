package com.radiance.compatibility.flywheel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.Opcodes;

class FlywheelEngineOwnershipBytecodeTest {
    @Test
    void unusedDynamicLightSectionsDoNotCrossTheNativeBoundary() throws IOException {
        ClassNode engine = read("RadianceFlywheelEngine");
        for (String methodName : new String[] {"lightSections", "onLightUpdate", "render"}) {
            MethodNode method = method(engine, methodName);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && call.owner.endsWith("NativeInstancingProxy")) {
                    assertFalse(call.name.equals("setLightSections")
                        || call.name.equals("uploadLightSection")
                        || call.name.equals("uploadLightSectionScene")
                        || call.name.equals("onLightUpdate"),
                        "dead Flywheel light-section upload returned through " + methodName);
                }
            }
        }
    }

    @Test
    void migratedHandleWritesThroughItsCurrentEngine() throws IOException {
        MethodNode write = method(read("RadianceFlywheelEngine$Handle"), "write");
        assertTrue(hasField(write, "RadianceFlywheelEngine$Handle", "engine"));
        assertTrue(hasCall(write, "NativeInstancingProxy", "updateInstance"));
    }

    @Test
    void crossEngineStealDeletesOldNativeAndAllocatesNewIdentity() throws IOException {
        MethodNode steal = method(read("RadianceFlywheelEngine$RadianceInstancer"),
            "stealInstance");
        assertTrue(hasCall(steal, "NativeInstancingProxy", "deleteInstance"));
        assertTrue(hasCall(steal, "java/util/concurrent/atomic/AtomicLong", "getAndIncrement"));
        assertTrue(hasField(steal, "RadianceFlywheelEngine$Handle", "engine"));
        assertTrue(hasField(steal, "RadianceFlywheelEngine$Handle", "id"));
        assertTrue(hasEngineIdentityBranch(steal),
            "same-engine steal must branch around native deletion and ID allocation");
    }

    @Test
    void parentEmbeddingTransformChecksDescendantContexts() throws IOException {
        ClassNode embedding = read("RadianceFlywheelEngine$Embedding");
        boolean dependencyCheck = embedding.methods.stream().anyMatch(method ->
            hasCall(method, "RadianceFlywheelEngine$Context", "dependsOn"));
        assertTrue(dependencyCheck);
    }

    @Test
    void lightingChangesInvalidateDescendantsAndHandleUploadsCurrentSceneAfterInstanceData()
        throws IOException {
        MethodNode setter = method(read("RadianceFlywheelEngine$Embedding"),
            "radiance$setEmbeddingLighting");
        assertTrue(hasCall(setter, "RadianceFlywheelEngine$Embedding", "markDependentInstancesDirty"));
        MethodNode write = method(read("RadianceFlywheelEngine$Handle"), "write");
        int instanceUpload = -1;
        int lightingUpload = -1;
        int index = 0;
        for (AbstractInsnNode instruction : write.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("NativeInstancingProxy")) {
                if (call.name.equals("updateInstance")) instanceUpload = index;
                if (call.name.equals("updateInstanceLighting")) lightingUpload = index;
            }
            index++;
        }
        assertTrue(instanceUpload >= 0 && lightingUpload > instanceUpload);
    }

    @Test
    void visualizationAvailabilityIsNotForcedOffDuringTransformedBlockEntityCapture() throws IOException {
        String resource = "com/radiance/mixins/compatibility/flywheel/FlywheelVisualizationManagerMixins.class";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(node.methods.stream().noneMatch(method -> method.name.equals(
                "radiance$useCapturedBlockEntityFallback")));
        }
    }

    private static boolean hasField(MethodNode method, String ownerSuffix, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.endsWith(ownerSuffix)
                && field.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean hasCall(MethodNode method, String ownerSuffix, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith(ownerSuffix)
                && call.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean hasEngineIdentityBranch(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump
                && (jump.getOpcode() == Opcodes.IF_ACMPEQ
                    || jump.getOpcode() == Opcodes.IF_ACMPNE)) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode node, String name) {
        MethodNode result = node.methods.stream().filter(method -> method.name.equals(name))
            .findFirst().orElse(null);
        assertNotNull(result, name);
        return result;
    }

    private static ClassNode read(String nestedName) throws IOException {
        String resource = "com/radiance/compatibility/flywheel/" + nestedName + ".class";
        try (InputStream stream = FlywheelEngineOwnershipBytecodeTest.class.getClassLoader()
            .getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
