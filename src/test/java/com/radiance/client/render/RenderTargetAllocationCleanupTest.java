package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderTargetAllocationCleanupTest {

    @Test
    void releasesEveryAllocatedResourceInDependencyOrder() {
        List<String> actions = new ArrayList<>();

        RenderTargetAllocationCleanup.release(7, 11, 13,
            () -> actions.add("unbind"), id -> actions.add("texture:" + id),
            id -> actions.add("framebuffer:" + id));

        assertEquals(List.of("unbind", "texture:13", "texture:11", "framebuffer:7"), actions);
    }

    @Test
    void skipsResourcesThatWereNeverAllocated() {
        List<String> actions = new ArrayList<>();

        RenderTargetAllocationCleanup.release(-1, 11, -1,
            () -> actions.add("unbind"), id -> actions.add("texture:" + id),
            id -> actions.add("framebuffer:" + id));

        assertEquals(List.of("texture:11"), actions);
    }

    @Test
    void continuesCleanupAfterFailureAndRethrowsTheFirstFailure() {
        List<String> actions = new ArrayList<>();
        RuntimeException expected = new RuntimeException("depth release failed");

        RuntimeException actual = assertThrows(RuntimeException.class,
            () -> RenderTargetAllocationCleanup.release(7, 11, 13,
                () -> actions.add("unbind"), id -> {
                    actions.add("texture:" + id);
                    if (id == 13) {
                        throw expected;
                    }
                }, id -> actions.add("framebuffer:" + id)));

        assertSame(expected, actual);
        assertEquals(List.of("unbind", "texture:13", "texture:11", "framebuffer:7"), actions);
    }
}
