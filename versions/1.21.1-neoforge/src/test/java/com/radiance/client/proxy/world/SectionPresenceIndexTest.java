package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SectionPresenceIndexTest {
    @Test
    void concurrentRecompilationKeepsTheFrameSnapshotButResetInvalidatesIt() throws Exception {
        var index = new SectionPresenceIndex<Object, List<Object>>();
        var section = new Object();
        var sign = new Object();
        index.reset(List.of(section));
        index.publish(section, List.of(sign), true);
        var frame = index.snapshot().getFirst();
        var worker = new Thread(() -> index.publish(section, List.of(sign), true));
        worker.start();
        worker.join();
        assertTrue(index.isCurrent(frame));
        assertSame(sign, frame.compiled().getFirst());
        assertSame(sign, index.snapshot().getFirst().compiled().getFirst());
        assertNotSame(frame.compiled(), index.snapshot().getFirst().compiled());
        index.remove(section);
        assertFalse(index.isCurrent(frame));
        index.publish(section, List.of(new Object()), true);
        assertFalse(index.isCurrent(frame));
        assertTrue(index.isCurrent(index.snapshot().getFirst()));
        var beforeWorldSwitch = index.snapshot().getFirst();
        index.reset(List.of(section));
        index.publish(section, List.of(sign), true);
        assertFalse(index.isCurrent(beforeWorldSwitch));
    }

    @Test
    void visitsOnlyPresentSectionsInOriginalOrderAndReusesUnchangedSnapshot() {
        var sections = new ArrayList<Object>();
        for (int i = 0; i < 26136; i++) sections.add(new Object());
        var index = new SectionPresenceIndex<Object, Object>();
        index.reset(sections);
        var a = new Object();
        var b = new Object();
        index.publish(sections.get(120), b, true);
        index.publish(sections.get(4), a, true);
        var snapshot = index.snapshot();
        assertEquals(2, snapshot.size());
        assertSame(a, snapshot.getFirst().compiled());
        assertSame(snapshot, index.snapshot());
        index.publish(sections.get(4), new Object(), false);
        assertEquals(1, index.snapshot().size());
        assertEquals(
                2, snapshot.size()); // Published snapshots are immutable for concurrent readers.
    }

    @Test
    void replacementResetAndWorldSwitchCannotAdoptRetiredOrExternalOwners() {
        var index = new SectionPresenceIndex<Object, Object>();
        var section = new Object();
        var external = new Object();
        var replacement = new Object();
        index.reset(List.of(section));
        var first = new Object();
        var second = new Object();
        index.publish(section, first, true);
        var old = index.snapshot();
        index.remove(section);
        assertTrue(index.snapshot().isEmpty());
        index.publish(section, second, true);
        assertNotSame(old.getFirst().compiled(), index.snapshot().getFirst().compiled());
        index.publish(external, new Object(), true);
        assertEquals(1, index.snapshot().size());
        index.reset(List.of(replacement));
        index.publish(section, first, true);
        assertTrue(index.snapshot().isEmpty());
        // Presence is independent of terrain-layer emptiness and animation; the same owner is
        // visited every frame.
        index.publish(replacement, second, true);
        assertEquals(1, index.snapshot().size());
    }
}
