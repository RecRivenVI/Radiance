package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.render.RenderCaptureContract;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

class DrawScopeReflectionTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void actualProbeBytecodeNamesResolveAndOpenTheLiveScopeApi() throws Exception {
        var node = new ClassNode();
        try (var stream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("com/radiance/audit/SpringMenuProbe.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var method =
                node.methods.stream()
                        .filter(m -> m.name.equals("enterVulkanGuiScope"))
                        .findFirst()
                        .orElseThrow();
        var names = new ArrayList<String>();
        for (var instruction : method.instructions)
            if (instruction instanceof LdcInsnNode ldc
                    && ldc.cst instanceof String value
                    && value.startsWith("com.radiance.")) names.add(value);
        assertEquals(2, names.size());
        var contract = Class.forName(names.get(0));
        var kinds = Class.forName(names.get(1));
        var gui = Enum.valueOf((Class<Enum>) kinds, "GUI");
        try (var scope =
                (AutoCloseable)
                        contract.getMethod("enter", kinds, String.class)
                                .invoke(null, gui, "probe-reflection-test")) {
            assertEquals("GUI", RenderCaptureContract.currentScope().kind().name());
        }
        assertEquals("OUTSIDE", RenderCaptureContract.currentScope().kind().name());
    }
}
