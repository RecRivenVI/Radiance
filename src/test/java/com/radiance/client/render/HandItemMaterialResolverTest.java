package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HandItemMaterialResolverTest {

    @Test
    void worldLayerMatchingUsesRenderTypeIdentity() {
        Object transmission = new Object();
        assertTrue(HandItemMaterialResolver.containsIdentity(
            List.of(new Object(), transmission), transmission));
        assertFalse(HandItemMaterialResolver.containsIdentity(
            List.of(new Object(), new Object()), transmission));
    }
}
