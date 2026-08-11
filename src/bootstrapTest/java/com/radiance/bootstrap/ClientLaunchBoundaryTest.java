package com.radiance.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ClientLaunchBoundaryTest {
    @Test
    void onlyPhysicalClientLaunchTargetsAreAccepted() {
        assertTrue(RadianceGraphicsBootstrapper.isClientLaunchTarget("neoforgeclient"));
        assertTrue(RadianceGraphicsBootstrapper.isClientLaunchTarget("neoForgeClientDev"));
        assertFalse(RadianceGraphicsBootstrapper.isClientLaunchTarget("neoforgeserver"));
        assertFalse(RadianceGraphicsBootstrapper.isClientLaunchTarget("data"));
        assertFalse(RadianceGraphicsBootstrapper.isClientLaunchTarget(null));
    }
}
