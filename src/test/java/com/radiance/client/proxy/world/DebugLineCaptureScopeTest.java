package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.radiance.client.vertex.StorageVertexConsumerProvider;
import org.junit.jupiter.api.Test;

class DebugLineCaptureScopeTest {

    @Test
    void nestedCaptureRestoresThePreviousDebugDestination() {
        StorageVertexConsumerProvider outer = new StorageVertexConsumerProvider(0);
        StorageVertexConsumerProvider inner = new StorageVertexConsumerProvider(0);

        assertNull(EntityProxy.activeDebugLineCapture());

        try (EntityProxy.DebugLineCaptureScope ignored =
                 EntityProxy.beginDebugLineCapture(outer)) {
            assertSame(outer, EntityProxy.activeDebugLineCapture());

            try (EntityProxy.DebugLineCaptureScope nested =
                     EntityProxy.beginDebugLineCapture(inner)) {
                assertSame(inner, EntityProxy.activeDebugLineCapture());
            }

            assertSame(outer, EntityProxy.activeDebugLineCapture());
        }

        assertNull(EntityProxy.activeDebugLineCapture());
        inner.close();
        outer.close();
    }
}
