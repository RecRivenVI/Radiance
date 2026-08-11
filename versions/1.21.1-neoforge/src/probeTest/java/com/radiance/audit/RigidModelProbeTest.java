package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RigidModelProbeTest {
    static class Parent {
        private final Object queue = new Object();
    }

    static final class Child extends Parent {}

    @Test
    void actualInheritedStateIsFoundAndMissingFieldsRemainFailures() throws Exception {
        var owner = new Child();
        assertSame(((Parent) owner).queue, RigidModelProbe.field(owner, "queue"));
        assertThrows(NoSuchFieldException.class, () -> RigidModelProbe.field(owner, "absent"));
    }
}
