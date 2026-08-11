package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Compiled contracts only: does not initialize RenderType or pretend to provide a mod loader. */
class PBRVertexConsumerCrumblingTest {
    @Test
    void compiledMaterialChecksActualCrumblingStateAndReturnsExistingMode22() throws Exception {
        MethodNode alpha = read("com/radiance/client/vertex/PBRVertexConsumer").methods.stream()
            .filter(method -> method.name.equals("getAlphaMode")).findFirst().orElseThrow();
        AbstractInsnNode field = field(alpha, "CRUMBLING_TRANSPARENCY");
        assertNotNull(field);
        AbstractInsnNode cursor = field.getNext();
        while (!(cursor instanceof MethodInsnNode)) cursor = cursor.getNext();
        assertEquals("equals", ((MethodInsnNode) cursor).name);
        cursor = nextOpcode(cursor);
        assertInstanceOf(JumpInsnNode.class, cursor);
        assertEquals(Opcodes.IFEQ, cursor.getOpcode());
        cursor = nextOpcode(cursor);
        assertInstanceOf(IntInsnNode.class, cursor);
        assertEquals(22, ((IntInsnNode) cursor).operand);
        assertEquals(Opcodes.IRETURN, nextOpcode(cursor).getOpcode());
        boolean coverage = false;
        for (var instruction : alpha.instructions) {
            if (instruction instanceof IntInsnNode value && value.operand == 10
                && nextOpcode(instruction).getOpcode() == Opcodes.IRETURN) coverage = true;
        }
        assertTrue(coverage);
    }

    @Test
    void actualVanillaCrumblingBuilderAndCompiledWorldRouteUseThatSameState() throws Exception {
        ClassNode vanilla = read("net/minecraft/client/renderer/RenderType");
        MethodNode builder = vanilla.methods.stream().filter(method -> {
            for (var instruction : method.instructions)
                if (instruction instanceof LdcInsnNode value && "crumbling".equals(value.cst)) return true;
            return false;
        }).findFirst().orElseThrow();
        assertNotNull(field(builder, "BLOCK"));
        assertNotNull(field(builder, "QUADS"));
        FieldInsnNode transparency = field(builder, "CRUMBLING_TRANSPARENCY");
        assertNotNull(transparency);
        assertEquals("Lnet/minecraft/client/renderer/RenderStateShard$TransparencyStateShard;",
            transparency.desc);
        boolean assignsTransparency = false;
        for (var instruction : builder.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("setTransparencyState"))
                assignsTransparency = true;
        assertTrue(assignsTransparency);
        MethodNode geometry = read("com/radiance/client/constant/Constants$GeometryTypes").methods.stream()
            .filter(method -> method.name.equals("getGeometryType")).findFirst().orElseThrow();
        AbstractInsnNode route = field(geometry, "CRUMBLING_TRANSPARENCY");
        assertNotNull(route);
        while (!(route instanceof JumpInsnNode)) route = route.getNext();
        route = nextOpcode(route);
        assertInstanceOf(FieldInsnNode.class, route);
        assertEquals("WORLD_TRANSPARENT", ((FieldInsnNode) route).name);
        assertEquals(Opcodes.ARETURN, nextOpcode(route).getOpcode());
    }

    private static FieldInsnNode field(MethodNode method, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals(name)) return field;
        return null;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream stream = PBRVertexConsumerCrumblingTest.class.getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
