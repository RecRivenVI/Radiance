package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DrawingCensusTest {
    @Test
    void independentlyCountsMissingDuplicateAndUnknownConsumers() {
        var census = new DrawingCensus();
        long absent = census.begin("RASTER", "font", "VULKAN_RASTER", "GUI");
        long doubled = census.begin("RASTER", "font", "VULKAN_RASTER", "GUI");
        long unknown = census.begin("RASTER", "custom", "UNKNOWN", "GUI");
        census.event(doubled, "CONSUMED", "first");
        census.event(doubled, "CONSUMED", "second");
        census.event(unknown, "CONSUMED", "native");
        census.event(absent, "END", "");
        census.event(doubled, "END", "");
        census.event(unknown, "END", "");
        assertEquals(0, census.pendingCount());
        assertEquals(1, census.snapshot().values().stream().mapToLong(c -> c.unowned).sum());
        assertEquals(1, census.snapshot().values().stream().mapToLong(c -> c.duplicate).sum());
        assertEquals(1, census.snapshot().values().stream().mapToLong(c -> c.unknown).sum());
    }
}
