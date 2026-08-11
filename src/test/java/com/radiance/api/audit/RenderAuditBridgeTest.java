package com.radiance.api.audit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RenderAuditBridgeTest {
    static class Sink implements RenderAuditSink {
        public boolean accepts(String c) { return true; }
        public long beginIntent(String c, String s, String d) { return 9; }
        public long currentIntentId() { return 9; }
        public void transition(long id, String s, String d, String detail, boolean terminal) {}
        public void frameBoundary(long token, String s, String d) {}
    }
    @Test void observerFailureDetachesWithoutReplacingRendererError() {
        Sink broken = new Sink() {
            @Override public boolean accepts(String c) { throw new AssertionError("diagnostic failed"); }
        };
        RenderAuditBridge.register(broken);
        assertEquals(0, RenderAuditBridge.beginIntent("x", "x", ""));
        assertFalse(RenderAuditBridge.enabled());
        assertDoesNotThrow(() -> RenderAuditBridge.frameBoundary(1, "close", ""));
        Sink valid = new Sink();
        try {
            RenderAuditBridge.register(valid);
            RenderAuditBridge.unregister(broken);
            assertEquals(9, RenderAuditBridge.beginIntent("x", "x", ""));
        } finally { RenderAuditBridge.unregister(valid); }
    }
    @Test void incompatibleObserverIsRejectedBeforePublication() {
        Sink wrong = new Sink() { @Override public int apiVersion() { return -1; } };
        assertThrows(IllegalArgumentException.class, () -> RenderAuditBridge.register(wrong));
        assertFalse(RenderAuditBridge.enabled());
    }
}
