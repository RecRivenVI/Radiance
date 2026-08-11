package com.radiance.mixin_related;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaintingSourceContractTest {
    private static final Set<String> SOURCES =
            Set.of(
                    "net.minecraft.client.renderer.entity.PaintingRenderer",
                    "net.minecraft.client.renderer.texture.TextureAtlasSprite");
    private String previous;

    @BeforeEach
    void isolate() {
        previous = System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY);
        System.clearProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY);
    }

    @AfterEach
    void restore() {
        if (previous == null) System.clearProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY);
        else System.setProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY, previous);
    }

    @Test
    void originalSelectionQualifiesWithoutMutatingTargetSets() {
        assertNull(System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
        var others = Set.of("unrelated.Renderer");
        new MixinPlugin().acceptTargets(SOURCES, others);
        assertEquals("independent", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
        assertEquals(2, SOURCES.size());
        assertEquals(Set.of("unrelated.Renderer"), others);
    }

    @Test
    void eitherForeignTargetDisablesPersistence() {
        for (String target : SOURCES) {
            System.clearProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY);
            new MixinPlugin().acceptTargets(SOURCES, Set.of(target));
            assertEquals("modified", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
        }
    }

    @Test
    void missingSourceAndLaterSelectionsCannotUndoRejection() {
        var plugin = new MixinPlugin();
        plugin.acceptTargets(Set.of(), Set.of());
        assertEquals("modified", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
        plugin.acceptTargets(SOURCES, Set.of());
        assertEquals("modified", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
    }

    @Test
    void internalAndBinaryTargetNamesHaveTheSameMeaning() {
        var internal =
                SOURCES.stream()
                        .map(s -> s.replace('.', '/'))
                        .collect(java.util.stream.Collectors.toSet());
        var plugin = new MixinPlugin();
        plugin.acceptTargets(internal, Set.of());
        assertEquals("independent", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
        plugin.acceptTargets(
                internal, Set.of("net/minecraft/client/renderer/entity/PaintingRenderer"));
        assertEquals("modified", System.getProperty(MixinPlugin.PAINTING_SOURCE_PROPERTY));
    }
}
