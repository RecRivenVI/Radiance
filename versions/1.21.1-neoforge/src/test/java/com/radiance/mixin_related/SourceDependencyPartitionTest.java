package com.radiance.mixin_related;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SourceDependencyPartitionTest {
    @Test
    void modifiedStateNeverQualifiesForStaticStateButDoesNotRewriteModelGeometry() {
        var keys =
                List.of(
                        MixinPlugin.BAKED_SOURCE_PROPERTY,
                        MixinPlugin.STATE_SOURCE_PROPERTY,
                        MixinPlugin.PART_SOURCE_PROPERTY,
                        MixinPlugin.PAINTING_SOURCE_PROPERTY);
        var previous = new HashMap<String, String>();
        for (var key : keys) {
            previous.put(key, System.getProperty(key));
            previous.put(key + ".reason", System.getProperty(key + ".reason"));
            System.clearProperty(key);
            System.clearProperty(key + ".reason");
        }
        try {
            var plugin = new MixinPlugin();
            var own = Set.copyOf(SourceCompatibility.Source.PAINTING.classes);
            plugin.acceptTargets(own, Set.of("net.minecraft.client.renderer.RenderType"));
            assertEquals("independent", System.getProperty(MixinPlugin.BAKED_SOURCE_PROPERTY));
            assertEquals("modified", System.getProperty(MixinPlugin.STATE_SOURCE_PROPERTY));
            assertTrue(
                    System.getProperty(MixinPlugin.STATE_SOURCE_PROPERTY + ".reason")
                            .contains("RenderType"));
            plugin.acceptTargets(own, Set.of("net.minecraft.client.resources.model.ModelManager"));
            assertEquals("modified", System.getProperty(MixinPlugin.BAKED_SOURCE_PROPERTY));
        } finally {
            previous.forEach(
                    (key, value) -> {
                        if (value == null) System.clearProperty(key);
                        else System.setProperty(key, value);
                    });
        }
    }
}
