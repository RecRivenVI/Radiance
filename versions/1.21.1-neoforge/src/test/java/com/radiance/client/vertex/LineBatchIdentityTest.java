package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.render.LineSources;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

class LineBatchIdentityTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of());
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void sameLayerKeepsDistinctRegisteredSourcesAndRetainsBothAllocations() {
        var storage =
                new StorageVertexConsumerProvider(1) {
                    @Override
                    protected com.radiance.render.contract.DrawStateSnapshot captureDrawState(
                            RenderType layer) {
                        return com.radiance.render.contract.DrawStateSnapshot.IDENTITY;
                    }
                };
        try {
            var original = storage.getBuffer(RenderType.lines());
            original.addVertex(0, 0, 0).setColor(1, 2, 3, 4).setNormal(0, 1, 0);
            original.addVertex(0, 1, 0).setColor(5, 6, 7, 8).setNormal(0, 1, 0);
            try (var source = LineSources.enter(LineSources.SIMULATED_LEVER)) {
                var lever = storage.getBuffer(RenderType.lines());
                assertNotSame(original, lever);
                lever.addVertex(1, 0, 0).setColor(20, 30, 40, 50).setNormal(0, 1, 0);
                lever.addVertex(1, 1, 0).setColor(30, 40, 50, 60).setNormal(0, 1, 0);
            }
            storage.debugLines(LineSources.DEBUG);
            assertEquals(2, storage.getLayerBatches().size());
            boolean foundLever = false;
            for (var batch : storage.getLayerBatches()) {
                var line = (CapturedLineVertexConsumer) batch.getValue();
                if (line.source == LineSources.SIMULATED_LEVER) foundLever = true;
                try (var mesh = line.build()) {
                    assertEquals(4, mesh.drawState().vertexCount());
                    assertEquals(1, line.emission);
                }
            }
            assertTrue(foundLever, "debug classification must not erase the producer");
        } finally {
            storage.close();
        }
        storage.close();
    }

    @Test
    void nestedSameSourceStillRequiresLifoAndRestoresOnException() {
        var outer = LineSources.enter(LineSources.SABLE);
        var inner = LineSources.enter(LineSources.SABLE);
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertSame(LineSources.ENTITY, LineSources.current(LineSources.ENTITY));
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (var ignored = LineSources.enter(LineSources.VEIL_EVENT)) {
                        throw new IllegalArgumentException("source callback failed");
                    }
                });
        assertSame(LineSources.ENTITY, LineSources.current(LineSources.ENTITY));
    }
}
