package com.radiance.mixins;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Check the actual patched class against the selectors our armor translation consumes. */
class NeoForgeArmorTargetTest {
    private static ClassNode read(String name) throws IOException {
        try (var input = NeoForgeArmorTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    @Test
    void armorGlintSelectorsExistInTheActiveNeoForgePatch() throws IOException {
        var mixin = read("com/radiance/mixins/vulkan_render_integration/HumanoidArmorLayerMixins");
        var target = read("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer");
        int checked = 0;
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                        && !annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!annotation.values.get(i).equals("method")) continue;
                    @SuppressWarnings("unchecked")
                    var selectors = (List<String>) annotation.values.get(i + 1);
                    for (String selector : selectors) {
                        checked++;
                        assertTrue(target.methods.stream().anyMatch(m -> (m.name + m.desc).equals(selector)),
                            () -> "The active NeoForge patch lacks armor target " + selector);
                    }
                }
            }
        }
        assertTrue(checked >= 3, "Check HEAD, base/glint operation and RETURN selectors");
    }
}
