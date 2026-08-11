package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SourceBindingsTest {
    @Test
    void definitionsAndDrawIdentityFollowTheirActualOwnerAndDomain() {
        var source = new SourceBindings();
        var first = source.definition("stable", Object::new);
        assertSame(first, source.definition("stable", Object::new));
        long body = source.history("world", 7), hand = source.history("hand", 7);
        assertNotEquals(body, hand);
        assertEquals(body, source.history("world", 7));
        assertNotEquals(body, new SourceBindings().history("world", 7));
        source.retire();
        assertEquals(body, source.history("world", 7));
        source.resume();
        assertNotEquals(body, source.history("world", 7));
        SourceBindings.invalidateResources();
        assertNotSame(first, source.definition("stable", Object::new));
    }
}
