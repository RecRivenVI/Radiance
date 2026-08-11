package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

class VeilMixinCompatibilityTest {
    private static final String TARGET = "net.minecraft.client.renderer.GameRenderer";
    private static final String HANDLER = "modifyExpressionValue$zcg000$veil$getShader";
    private static final String DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";

    @Test
    void replacementPreservesOriginalShaderForStaticAndInstanceHandlers() throws Exception {
        for (boolean isStatic : new boolean[]{false, true}) {
            ClassNode node = new ClassNode();
            node.version = Opcodes.V17;
            node.access = Opcodes.ACC_PUBLIC;
            node.name = "WarmupVeilHandler";
            node.superName = "java/lang/Object";
            MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            node.methods.add(constructor);
            MethodNode handler = throwingHandler(HANDLER, DESCRIPTOR);
            handler.access = Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0);
            node.methods.add(handler);

            VeilMixinCompatibility.postApply(TARGET, node);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] bytes = writer.toByteArray();
            Class<?> type = new ClassLoader(getClass().getClassLoader()) {
                Class<?> loadHandler() { return defineClass(null, bytes, 0, bytes.length); }
            }.loadHandler();
            Object shader = new Object();
            Object receiver = isStatic ? null : type.getConstructor().newInstance();
            assertSame(shader, type.getMethod(HANDLER, Object.class).invoke(receiver, shader));
        }
    }

    @Test
    void realFramebufferLifecycleCallbacksRemainIntact() {
        String descriptor = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
        for (String name : new String[]{"initTransparency", "deinitTransparency"}) {
            ClassNode node = new ClassNode();
            MethodNode veil = throwingHandler("handler$zbh000$veil$" + name, descriptor);
            MethodNode vanilla = throwingHandler(name, "()V");
            MethodNode other = throwingHandler("handler$zbh000$other$" + name, descriptor);
            MethodNode overload = throwingHandler("handler$zbh000$veil$" + name, "()V");
            node.methods.add(veil);
            node.methods.add(vanilla);
            node.methods.add(other);
            node.methods.add(overload);
            VeilMixinCompatibility.postApply(TARGET, node);
            assertEquals(Opcodes.ATHROW, veil.instructions.getLast().getOpcode());
            VeilMixinCompatibility.postApply("net.minecraft.client.renderer.LevelRenderer", node);
            assertEquals(Opcodes.ATHROW, veil.instructions.getLast().getOpcode());
            assertEquals(Opcodes.ATHROW, vanilla.instructions.getLast().getOpcode());
            assertEquals(Opcodes.ATHROW, other.instructions.getLast().getOpcode());
            assertEquals(Opcodes.ATHROW, overload.instructions.getLast().getOpcode());
        }
    }

    @Test
    void restoredCpuAndUniformLifecycleHandlersAreNotErased() {
        for (String[] input : new String[][]{
            {TARGET, "handler$veil$updateGuiCamera", "()V"},
            {TARGET, "handler$veil$unbindGuiCamera", "()V"},
            {"net.minecraft.client.particle.ParticleEngine", "handler$veil$tick", "()V"},
            {"net.minecraft.client.particle.ParticleEngine", "handler$veil$setLevel", "()V"},
            {"net.minecraft.client.renderer.ShaderInstance", "handler$veil$setupFallbackProcessor", "()V"},
            {"net.minecraft.client.gui.components.DebugScreenOverlay", "handler$veil$modifyGameInformation", "(Ljava/util/List;)Ljava/util/List;"}
        }) {
            ClassNode node = new ClassNode();
            MethodNode method = throwingHandler(input[1], input[2]);
            node.methods.add(method);
            VeilMixinCompatibility.postApply(input[0], node);
            assertEquals(Opcodes.ATHROW, method.instructions.getLast().getOpcode(), input[1]);
        }
    }

    @Test
    void unrelatedTargetsNamesAndSignaturesRemainUntouched() {
        for (String[] input : new String[][]{
            {"other.GameRenderer", HANDLER, DESCRIPTOR},
            {TARGET, HANDLER + "Other", DESCRIPTOR},
            {TARGET, "other$getShader", DESCRIPTOR},
            {TARGET, HANDLER, "()Ljava/lang/Object;"}
        }) {
            ClassNode node = new ClassNode();
            MethodNode method = throwingHandler(input[1], input[2]);
            node.methods.add(method);
            VeilMixinCompatibility.postApply(input[0], node);
            assertEquals(Opcodes.ATHROW, method.instructions.getLast().getOpcode());
        }
    }

    private static MethodNode throwingHandler(String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        return method;
    }
}
