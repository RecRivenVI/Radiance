package com.radiance.bootstrap;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import cpw.mods.modlauncher.api.ITransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
class FaceStateLaunchPluginTest {
    @TempDir Path classes;

    @Test
    void targetDiscoveryDoesNotTransformUnrelatedOptionalCompatibilityClasses() throws Exception {
        writeClass("example/OptionalCompat", "missing/OptionalBase", false);
        writeClass("example/Renderer", "java/lang/Object", true);
        writeClass("com/mojang/blaze3d/platform/GlStateManager", "java/lang/Object", true);
        assertEquals(Set.of(ITransformer.Target.targetClass("example.Renderer")),
            FaceStateLaunchPlugin.collectTargets(classes));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("missing.OptionalBase"));
    }

    private void writeClass(String name, String parent, boolean faceCall) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "draw", "()V", null, null);
        method.visitCode();
        if (faceCall) {
            method.visitIntInsn(Opcodes.SIPUSH, 1028);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11C", "glCullFace", "(I)V", false);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        Path file = classes.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }
    @Test
    void realCallSitesPreserveArgumentsAndUnrelatedCalls() {
        ClassNode c = new ClassNode();
        c.name = "example/Renderer";
        MethodNode m = new MethodNode(Opcodes.ACC_STATIC, "draw", "()V", null, null);
        c.methods.add(m);
        m.instructions.add(new IntInsnNode(Opcodes.SIPUSH, 1028));
        MethodInsnNode face = new MethodInsnNode(
            Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11C", "glCullFace", "(I)V", false);
        m.instructions.add(face);
        MethodInsnNode unrelated =
            new MethodInsnNode(Opcodes.INVOKESTATIC, "example/Owner", "glCullFace", "(I)V", false);
        m.instructions.add(unrelated);
        assertTrue(FaceStateLaunchPlugin.rewrite(c));
        assertEquals("com/radiance/client/render/DirectFaceState", face.owner);
        assertEquals("(I)V", face.desc);
        assertEquals(1028, ((IntInsnNode) m.instructions.getFirst()).operand);
        assertEquals("example/Owner", unrelated.owner);
        assertFalse(FaceStateLaunchPlugin.rewrite(c));
    }
    @Test
    void bootstrapAndLwjglAreOutsideTransformation() {
        var plugin = new FaceStateLaunchPlugin();
        assertFalse(FaceStateLaunchPlugin.eligible("org/lwjgl/opengl/GL11"));
        assertFalse(FaceStateLaunchPlugin.eligible("net/neoforged/fml/earlydisplay/Window"));
        ClassNode cachedState = new ClassNode();
        cachedState.name = "com/mojang/blaze3d/platform/GlStateManager$BooleanState";
        MethodNode method = new MethodNode();
        var enable = new MethodInsnNode(
            Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnable", "(I)V", false);
        method.instructions.add(enable);
        cachedState.methods.add(method);
        assertFalse(FaceStateLaunchPlugin.rewrite(cachedState));
        assertEquals("org/lwjgl/opengl/GL11", enable.owner);
    }
}
