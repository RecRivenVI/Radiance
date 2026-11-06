package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SourceBindingsTest {
    @Test
    void primitiveHistoryKeepsDomainEqualityAndAllSlotBitsAcrossInvalidation() {
        var source = new SourceBindings();
        long low = source.history(new String("world"), 7);
        long high = source.history("world", (1L << 48) | 7);
        long other = source.history("gui", 7);
        assertNotEquals(low, high);
        assertNotEquals(low, other);
        assertEquals(low, source.history(new String("world"), 7));
        long noDomain = source.history(null, Long.MIN_VALUE);
        assertEquals(noDomain, source.history(null, Long.MIN_VALUE));
        source.clear();
        assertNotEquals(low, source.history("world", 7));
        long beforeReload = source.history("world", 7);
        SourceBindings.invalidateResources();
        assertNotEquals(beforeReload, source.history("world", 7));
    }

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
