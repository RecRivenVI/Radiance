package com.radiance.compatibility.create;

import com.radiance.client.render.WorldMeshSink;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Captures every layer emitted while Create renders a ValueBox and submits it as world geometry.
 */
public final class CreateValueBoxWorldGeometry {

    private static final ThreadLocal<Deque<Capture>> CAPTURES =
        ThreadLocal.withInitial(ArrayDeque::new);

    private CreateValueBoxWorldGeometry() {
    }

    public static CaptureToken begin(Object owner) {
        Capture capture = new Capture(owner, new StorageVertexConsumerProvider(16384));
        CAPTURES.get().push(capture);
        return new CaptureToken(capture, Thread.currentThread());
    }

    public static MultiBufferSource route(MultiBufferSource original) {
        Deque<Capture> captures = CAPTURES.get();
        if (captures.isEmpty()) {
            return original;
        }

        StorageVertexConsumerProvider worldGeometry = captures.peek().worldGeometry();
        return worldGeometry::getBuffer;
    }

    private static void finish(Capture expected, boolean commit) {
        Deque<Capture> captures = CAPTURES.get();
        if (captures.isEmpty()) {
            throw new IllegalStateException("Create ValueBox capture stack is empty");
        }

        Capture capture = captures.pop();
        if (captures.isEmpty()) {
            CAPTURES.remove();
        }
        if (capture != expected) {
            capture.worldGeometry().close();
            throw new IllegalStateException("Create ValueBox world-geometry capture stack is unbalanced");
        }
        if (!commit || capture.worldGeometry().getLayers().isEmpty()) {
            capture.worldGeometry().close();
            return;
        }
        WorldMeshSink.submitCaptured(capture.worldGeometry(), capture.owner(),
            "create/value_box");
    }

    private record Capture(Object owner, StorageVertexConsumerProvider worldGeometry) {
    }

    public static final class CaptureToken implements AutoCloseable {
        private final Capture capture;
        private final Thread owner;
        private boolean committed;
        private boolean closed;

        private CaptureToken(Capture capture, Thread owner) {
            this.capture = capture;
            this.owner = owner;
        }

        public void commit() {
            requireOwner();
            committed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            requireOwner();
            try {
                finish(capture, committed);
            } finally {
                closed = true;
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Create ValueBox capture closed from another thread");
            }
        }
    }
}
