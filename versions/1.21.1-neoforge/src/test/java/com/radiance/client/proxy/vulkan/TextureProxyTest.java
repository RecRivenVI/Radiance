package com.radiance.client.proxy.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextureProxyTest {

    @Test
    void laterLegacyBindingWins() {
        assertEquals(17, TextureProxy.chooseEffectiveTexture(17, 4, 23, 3));
    }

    @Test
    void laterShaderSlotBindingWins() {
        assertEquals(23, TextureProxy.chooseEffectiveTexture(17, 3, 23, 4));
    }

    @Test
    void shaderSlotIsTheFallbackBeforeEitherWriterWasObserved() {
        assertEquals(23, TextureProxy.chooseEffectiveTexture(-1, 0, 23, 0));
    }
}
