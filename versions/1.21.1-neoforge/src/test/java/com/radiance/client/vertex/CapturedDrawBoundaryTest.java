package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.render.contract.DrawStateSnapshot;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CapturedDrawBoundaryTest {
    private static RenderType layer;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        layer =
                new RenderType(
                        "draw_boundary_fixture",
                        PBRVertexFormats.PBR_TRIANGLE,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        false,
                        () -> {},
                        () -> {}) {};
    }

    static final class Provider extends StorageVertexConsumerProvider {
        int captures;
        boolean fail;
        DrawStateSnapshot state = new DrawStateSnapshot(4, 2, .25f, .5f, .75f, .3f);

        Provider() {
            super(1024);
        }

        @Override
        protected VertexConsumer createQuadConsumer(ByteBufferBuilder allocator, RenderType type) {
            return PBRVertexConsumer.dynamic(allocator, type, false);
        }

        @Override
        protected DrawStateSnapshot captureDrawState(RenderType type) {
            captures++;
            if (fail) throw new IllegalStateException("fixture callback failure");
            return state;
        }
    }

    @Test
    void explicitFlushSeparatesTheSameLayerAndSealsItsOwnState() {
        var provider = new Provider();
        try {
            var first = provider.getBuffer(layer);
            quad(first);
            var firstState = provider.state;
            provider.endBatch(layer);
            assertEquals(1, provider.captures);
            assertEquals(firstState, provider.drawState(first));
            provider.state = new DrawStateSnapshot(128, 4, .8f, .7f, .6f, .9f);
            var second = provider.getBuffer(layer);
            assertNotSame(first, second);
            quad(second);
            provider.endBatch();
            assertEquals(2, provider.captures);
            assertEquals(firstState, provider.drawState(first));
            assertEquals(provider.state, provider.drawState(second));
            assertEquals(2, provider.getLayerBatches().size());
            for (var entry : provider.getLayerBatches()) {
                try (var mesh = ((PBRVertexConsumer) entry.getValue()).end()) {
                    assertEquals(4, mesh.drawState().vertexCount());
                }
            }
        } finally {
            provider.close();
        }
        assertTrue(provider.getLayerBatches().isEmpty());
    }

    @Test
    void failedCaptureRetainsOwnershipAndRepeatedFlushCannotReapplyState() {
        var provider = new Provider();
        var consumer = provider.getBuffer(layer);
        quad(consumer);
        provider.fail = true;
        assertThrows(IllegalStateException.class, provider::endLastBatch);
        assertTrue(provider.hasBatches());
        assertNull(provider.drawState(consumer));
        provider.fail = false;
        provider.endLastBatch();
        var sealed = provider.drawState(consumer);
        provider.state = DrawStateSnapshot.IDENTITY;
        provider.endBatch();
        provider.endBatch(layer);
        assertSame(sealed, provider.drawState(consumer));
        assertEquals(2, provider.captures);
        assertEquals(1, provider.getLayerBatches().size());
        provider.close();
        assertNull(provider.drawState(consumer));
        assertFalse(provider.hasBatches());
        assertThrows(IllegalStateException.class, () -> provider.getBuffer(layer));
        provider.close();
    }

    @Test
    void emptyProviderCanFlushBeforeItsFirstRealBatchAndCloseIndependently() {
        var empty = new Provider();
        var active = new Provider();
        try {
            empty.endBatch();
            empty.endLastBatch();
            empty.endBatch(layer);
            assertFalse(empty.hasBatches());
            assertEquals(0, empty.captures);
            empty.close();
            var consumer = active.getBuffer(layer);
            quad(consumer);
            active.endBatch();
            assertEquals(1, active.captures);
            try (var mesh = ((PBRVertexConsumer) consumer).end()) {
                assertEquals(4, mesh.drawState().vertexCount());
            }
        } finally {
            empty.close();
            active.close();
        }
    }

    @Test
    void emptySubmissionRemainsUsableAndItsLaterGeometryIsNotDropped() {
        var provider = new Provider();
        var data = new EntityProxy.EntityRenderDataList();
        try {
            process(provider, data);
            assertTrue(data.isEmpty());
            assertEquals(0, provider.captures);
            quad(provider.getBuffer(layer));
            process(provider, data);
            assertEquals(1, provider.captures);
            assertEquals(1, data.size());
            assertEquals(4, data.getFirst().getFirst().builtBuffer().drawState().vertexCount());
        } finally {
            for (var entity : data) for (var draw : entity) draw.builtBuffer().close();
            provider.close();
        }
    }

    @Test
    void zeroVertexBatchStillExecutesItsStateBoundary() {
        var provider = new Provider();
        try {
            provider.getBuffer(layer);
            var data = new EntityProxy.EntityRenderDataList();
            process(provider, data);
            assertTrue(data.isEmpty());
            assertEquals(1, provider.captures);
        } finally {
            provider.close();
        }
    }

    @Test
    void rigidOnlySubmissionIsTransferredRatherThanMistakenForEmpty() throws Exception {
        var provider = new Provider();
        try {
            // This test exercises ownership transfer before native submission.
            var draw = new RigidModelCapture.Draw(null, new org.joml.Matrix4f(), 41, 0);
            provider.rigidDraws.add(draw);
            assertTrue(provider.hasCapturedRigidModels());
            var data = new EntityProxy.EntityRenderDataList();
            process(provider, data);
            var field = EntityProxy.EntityRenderDataList.class.getDeclaredField("rigidDraws");
            field.setAccessible(true);
            assertEquals(List.of(draw), field.get(data));
            assertFalse(provider.hasCapturedRigidModels());
            assertTrue(data.isEmpty());
        } finally {
            provider.close();
        }
    }

    private static void process(Provider provider, EntityProxy.EntityRenderDataList data) {
        EntityProxy.processWorldEntityRenderData(
                provider, 41, 2, 3, 4, Constants.RayTracingFlags.WORLD, true, data);
    }

    private static void quad(VertexConsumer output) {
        output.addVertex(0, 0, 0).setColor(255, 255, 255, 255);
        output.addVertex(1, 0, 0).setColor(255, 255, 255, 255);
        output.addVertex(1, 1, 0).setColor(255, 255, 255, 255);
        output.addVertex(0, 1, 0).setColor(255, 255, 255, 255);
    }
}
