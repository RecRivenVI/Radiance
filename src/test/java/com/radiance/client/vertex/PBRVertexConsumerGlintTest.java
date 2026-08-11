package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PBRVertexConsumerGlintTest {

    @Test
    void preservesVanillaItemAndEntityGlintFamilies() {
        assertEquals(PBRVertexConsumer.GLINT_MODE_ITEM,
            PBRVertexConsumer.glintModeForRenderTypeName("glint"));
        assertEquals(PBRVertexConsumer.GLINT_MODE_ITEM,
            PBRVertexConsumer.glintModeForRenderTypeName("glint_translucent"));
        assertEquals(PBRVertexConsumer.GLINT_MODE_ENTITY,
            PBRVertexConsumer.glintModeForRenderTypeName("entity_glint"));
        assertEquals(PBRVertexConsumer.GLINT_MODE_ENTITY,
            PBRVertexConsumer.glintModeForRenderTypeName("entity_glint_direct"));
        assertEquals(PBRVertexConsumer.GLINT_MODE_ENTITY,
            PBRVertexConsumer.glintModeForRenderTypeName("armor_entity_glint"));
    }
}
