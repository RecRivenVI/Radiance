package com.radiance.compatibility.simulated;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class SimulatedEndSeaShadowTest {
    private static final Path JARS = Path.of("build", "radiance-compatibility-compile",
        "6.0.10+mc1.21.1_2.0.5+mc1.21.1_1.3.2+mc1.21.1_4.3.2");

    @Test
    void restoresEveryStateAfterDrawFailureAndPreservesCompilerError() {
        List<String> restored = new ArrayList<>();
        SimulatedRenderRecovery recovery = new SimulatedRenderRecovery();
        recovery.add(() -> restored.add("shadow flag"));
        recovery.add(() -> { restored.add("shader"); throw new IllegalStateException("clear failed"); });
        recovery.add(() -> restored.add("targets"));
        recovery.add(() -> restored.add("viewport"));
        IllegalArgumentException compileFailure = new IllegalArgumentException("real shader error");
        assertSame(compileFailure, assertThrows(IllegalArgumentException.class,
            () -> recovery.run(() -> { throw compileFailure; })));
        assertEquals(List.of("shadow flag", "shader", "targets", "viewport"), restored);
        assertEquals("clear failed", compileFailure.getSuppressed()[0].getMessage());
    }

    @Test
    void restoresOnSuccessAndPropagatesFirstCleanupFailure() {
        List<String> events = new ArrayList<>();
        SimulatedRenderRecovery recovery = new SimulatedRenderRecovery();
        IllegalStateException failure = new IllegalStateException("bind failed");
        recovery.add(() -> { events.add("read"); throw failure; });
        recovery.add(() -> events.add("draw"));
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> recovery.run(() -> events.add("real group and five spread draws"))));
        assertEquals(List.of("real group and five spread draws", "read", "draw"), events);
    }

    @Test
    void fixedInjectedFieldsHaveExactDescriptorsAndStaticness() throws Exception {
        ClassNode shadow = packaged("dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar",
            "dev/simulated_team/simulated/content/end_sea/EndSeaShadowRenderer");
        assertField(shadow, "isRenderingShadowMap", "Z", true);
        ClassNode visual = packaged("dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar",
            "dev/simulated_team/simulated/neoforge/mixin/diagram/VisualizationManagerImplMixin");
        assertField(visual, "sable$drawingDiagram", "Z", true);
        var sableResource = getClass().getClassLoader().getResource(
            "dev/ryanhcode/sable/mixin/sublevel_render/BlockEntityRenderDispatcherMixin.class");
        assertNotNull(sableResource);
        assertTrue(sableResource.toExternalForm().contains("2.0.5"), sableResource.toExternalForm());
        ClassNode dispatcher;
        try (InputStream stream = sableResource.openStream()) { dispatcher = read(stream); }
        assertField(dispatcher, "sable$cameraPos", "Lnet/minecraft/world/phys/Vec3;", false);
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
            "net/minecraft/client/renderer/MultiBufferSource$BufferSource.class")) {
            assertNotNull(stream);
            ClassNode buffers = read(stream);
            assertField(buffers, "startedBuilders", "Ljava/util/Map;", false);
            assertField(buffers, "sharedBuffer", "Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;", false);
            assertField(buffers, "fixedBuffers", "Ljava/util/SequencedMap;", false);
            assertField(buffers, "lastSharedType", "Lnet/minecraft/client/renderer/RenderType;", false);
        }
    }

    @Test
    void originalGroupDrawCallsMatchEveryRequiredWrapperTarget() throws Exception {
        ClassNode group = packaged("dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar",
            "dev/simulated_team/simulated/util/SimpleSubLevelGroupRenderer");
        List<String> calls = new ArrayList<>();
        var method = group.methods.stream().filter(m -> m.name.equals("renderGroup")).findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call.owner + ";" + call.name + call.desc);
        }
        for (String required : List.of(
            "org/joml/Matrix4fStack;pushMatrix()Lorg/joml/Matrix4fStack;",
            "org/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;",
            "net/minecraft/client/renderer/RenderType;setupRenderState()V",
            "net/minecraft/client/renderer/RenderType;clearRenderState()V",
            "net/minecraft/client/renderer/ShaderInstance;apply()V",
            "net/minecraft/client/renderer/ShaderInstance;clear()V",
            "net/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
            assertTrue(calls.contains(required), required);
    }

    private static void assertField(ClassNode node, String name, String descriptor, boolean isStatic) {
        var field = node.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
        assertEquals(descriptor, field.desc, node.name + "." + name);
        assertEquals(isStatic, (field.access & Opcodes.ACC_STATIC) != 0);
    }

    private static ClassNode packaged(String jar, String name) throws Exception {
        try (ZipFile archive = new ZipFile(JARS.resolve(jar).toFile())) {
            var entry = archive.getEntry(name + ".class");
            assertNotNull(entry, name);
            try (InputStream stream = archive.getInputStream(entry)) { return read(stream); }
        }
    }

    private static ClassNode read(InputStream stream) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }
}
