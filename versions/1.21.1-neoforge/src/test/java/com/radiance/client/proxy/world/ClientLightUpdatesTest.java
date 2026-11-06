package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClientLightUpdatesTest {
    @Test
    void nestedPacketsKeepTheWholeReceiveScopeAndUnwindOnFailure() {
        assertFalse(ClientLightUpdates.receiving());
        RuntimeException failure = new RuntimeException("packet failure");
        assertSame(
                failure,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                ClientLightUpdates.receive(
                                        () -> {
                                            assertTrue(ClientLightUpdates.receiving());
                                            ClientLightUpdates.receive(
                                                    () ->
                                                            assertTrue(
                                                                    ClientLightUpdates
                                                                            .receiving()));
                                            assertTrue(ClientLightUpdates.receiving());
                                            throw failure;
                                        })));
        assertFalse(ClientLightUpdates.receiving());
        ClientLightUpdates.receive(() -> assertTrue(ClientLightUpdates.receiving()));
        assertFalse(ClientLightUpdates.receiving());
    }
}
