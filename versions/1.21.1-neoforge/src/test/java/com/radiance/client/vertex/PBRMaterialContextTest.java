package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PBRMaterialContextTest {

    @Test
    void nestedTransmissionScopesRestoreTheirParentState() {
        assertFalse(PBRMaterialContext.blockTransmissionActive());
        try (var outer = PBRMaterialContext.pushBlockTransmission(true)) {
            assertTrue(PBRMaterialContext.blockTransmissionActive());
            try (var ignored = PBRMaterialContext.pushBlockTransmission(true)) {
                assertTrue(PBRMaterialContext.blockTransmissionActive());
            }
            assertTrue(PBRMaterialContext.blockTransmissionActive());
        }
        assertFalse(PBRMaterialContext.blockTransmissionActive());
    }

    @Test
    void disabledScopeDoesNotChangeState() {
        try (var ignored = PBRMaterialContext.pushBlockTransmission(false)) {
            assertFalse(PBRMaterialContext.blockTransmissionActive());
        }
    }

    @Test
    void independentMaterialScopesDoNotLeakAcrossSemanticKinds() {
        try (var ignored = PBRMaterialContext.pushEntityTransmission(true)) {
            assertTrue(PBRMaterialContext.entityTransmissionActive());
            assertFalse(PBRMaterialContext.blockTransmissionActive());
        }
        assertFalse(PBRMaterialContext.entityTransmissionActive());
    }

    @Test
    void nestedEmissionScopesKeepTheStrongestValueAndRestoreTheirParent() {
        assertEquals(0.0F, PBRMaterialContext.albedoEmission());
        try (var outer = PBRMaterialContext.pushAlbedoEmission(5.0F / 15.0F)) {
            assertEquals(5.0F / 15.0F, PBRMaterialContext.albedoEmission());
            try (var stronger = PBRMaterialContext.pushAlbedoEmission(1.0F)) {
                assertEquals(1.0F, PBRMaterialContext.albedoEmission());
            }
            assertEquals(5.0F / 15.0F, PBRMaterialContext.albedoEmission());
            try (var weaker = PBRMaterialContext.pushAlbedoEmission(0.1F)) {
                assertEquals(5.0F / 15.0F, PBRMaterialContext.albedoEmission());
            }
            assertEquals(5.0F / 15.0F, PBRMaterialContext.albedoEmission());
        }
        assertEquals(0.0F, PBRMaterialContext.albedoEmission());
    }

    @Test
    void emissionScopesClampToTheVertexContract() {
        try (var ignored = PBRMaterialContext.pushAlbedoEmission(2.0F)) {
            assertEquals(1.0F, PBRMaterialContext.albedoEmission());
        }
        try (var ignored = PBRMaterialContext.pushAlbedoEmission(-1.0F)) {
            assertEquals(0.0F, PBRMaterialContext.albedoEmission());
        }
    }
}
