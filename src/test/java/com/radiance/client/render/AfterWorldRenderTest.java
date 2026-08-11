package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AfterWorldRenderTest {
    @AfterEach void cleanup() { AfterWorldRender.cancel(); }

    @Test void runsAfterFuseAndDoesNotRequeueDuringFlush() {
        List<String> order = new ArrayList<>();
        AfterWorldRender.begin();
        assertTrue(AfterWorldRender.defer(() -> {
            assertFalse(AfterWorldRender.defer(() -> fail("Recursive post queue")));
            order.add("post");
        }));
        order.add("fuse");
        AfterWorldRender.flush();
        assertEquals(List.of("fuse", "post"), order);
    }

    @Test void failedPostDoesNotLeakActionsIntoNextFrame() {
        AfterWorldRender.begin();
        AfterWorldRender.defer(() -> { throw new IllegalStateException("post failed"); });
        AfterWorldRender.defer(() -> fail("Must not execute after failure"));
        assertThrows(IllegalStateException.class, AfterWorldRender::flush);
        AfterWorldRender.begin();
        AfterWorldRender.flush();
    }

    @Test void abortedWorldNeverRunsPostActions() {
        AfterWorldRender.begin();
        AfterWorldRender.defer(() -> fail("Aborted world"));
        AfterWorldRender.cancel();
        AfterWorldRender.flush();
    }

    @Test void shadowAndFixedConsumersPrecedeSeaAndFinalPost() {
        List<String> order = new ArrayList<>();
        AfterWorldRender.begin();
        AfterWorldRender.defer(100, () -> order.add("sea"));
        AfterWorldRender.defer(0, () -> order.add("fixed"));
        AfterWorldRender.defer(200, () -> order.add("post"));
        AfterWorldRender.defer(0, () -> order.add("shadow"));
        AfterWorldRender.flush();
        assertEquals(List.of("fixed", "shadow", "sea", "post"), order);
    }
}
