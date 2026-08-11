package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ScreenEffectCoordinatorTest {
    @Test
    void mapsOnlyTheNativePostEffectsImplementedByMcvr() {
        assertEquals(ScreenEffectCoordinator.EntityPostEffect.CREEPER,
            ScreenEffectCoordinator.EntityPostEffect.resolve(
                ResourceLocation.withDefaultNamespace("shaders/post/creeper.json")));
        assertEquals(ScreenEffectCoordinator.EntityPostEffect.SPIDER,
            ScreenEffectCoordinator.EntityPostEffect.resolve(
                ResourceLocation.withDefaultNamespace("shaders/post/spider.json")));
        assertEquals(ScreenEffectCoordinator.EntityPostEffect.INVERT,
            ScreenEffectCoordinator.EntityPostEffect.resolve(
                ResourceLocation.withDefaultNamespace("shaders/post/invert.json")));
        assertEquals(ScreenEffectCoordinator.EntityPostEffect.NONE,
            ScreenEffectCoordinator.EntityPostEffect.resolve(
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/custom.json")));
    }

    @Test
    void noneCannotBeSubmittedToTheNativePostPipeline() {
        assertFalse(ScreenEffectCoordinator.EntityPostEffect.NONE.supported());
        assertTrue(ScreenEffectCoordinator.EntityPostEffect.CREEPER.supported());
        assertEquals(0, ScreenEffectCoordinator.EntityPostEffect.CREEPER.nativeId());
        assertEquals(1, ScreenEffectCoordinator.EntityPostEffect.SPIDER.nativeId());
        assertEquals(2, ScreenEffectCoordinator.EntityPostEffect.INVERT.nativeId());
    }
}
