package com.radiance.client.render;

import com.radiance.client.RadianceClient;
import com.radiance.render.contract.DrawTargetContract;
import com.radiance.render.contract.DrawTargetContract.BufferDrawDecision;
import com.radiance.render.contract.DrawTargetContract.Scope;
import com.radiance.render.contract.DrawTargetContract.ScopeKind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Explicit render-scope contract shared by the Java capture boundaries.
 *
 * <p>A vanilla {@code MeshData} draw has no Radiance world-geometry identity, material contract, or
 * TLAS submission handle. It may therefore be routed to the Vulkan UI only while an explicit UI
 * scope is active; world and dimension-extension scopes are rejected until a real world-capture
 * bridge supplies that missing contract.
 */
public final class RenderCaptureContract {

    private static final ThreadLocal<Deque<ScopeFrame>> SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> REPORTED_DISCARDS = new HashSet<>();

    private RenderCaptureContract() {}

    public static ScopeToken enter(ScopeKind kind, String label) {
        Scope scope = new Scope(kind, label);
        ScopeFrame frame = new ScopeFrame(scope);
        SCOPES.get().push(frame);
        return new ScopeToken(frame, Thread.currentThread());
    }

    public static Scope currentScope() {
        ScopeFrame frame = SCOPES.get().peek();
        return frame == null ? new Scope(ScopeKind.OUTSIDE, "outside") : frame.scope;
    }

    public static BufferDrawDecision classifyBufferDraw() {
        Scope scope = currentScope();
        int framebuffer = 0;
        if (scope.kind() == ScopeKind.WORLD_STAGE || scope.kind() == ScopeKind.DIMENSION_EFFECT) {
            try {
                framebuffer =
                        com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(
                                com.radiance.client.proxy.vulkan.FramebufferProxy.DRAW_FRAMEBUFFER);
            } catch (UnsatisfiedLinkError unavailableDuringContractTest) {
                framebuffer = 0;
            }
        }
        return DrawTargetContract.classify(scope, framebuffer);
    }

    public static UnsupportedOperationException reject(
            BufferDrawDecision decision, String operation) {
        return new UnsupportedOperationException(
                "Radiance rejected "
                        + operation
                        + " in "
                        + decision.scope().kind()
                        + "["
                        + decision.scope().label()
                        + "]: "
                        + decision.reason());
    }

    public static UnsupportedOperationException rejectOpenGl(String operation) {
        Scope scope = currentScope();
        return new UnsupportedOperationException(
                "Radiance rejected direct OpenGL/FBO operation "
                        + operation
                        + " in "
                        + scope.kind()
                        + "["
                        + scope.label()
                        + "]: GLFW_NO_API has no OpenGL context");
    }

    /**
     * Records an unsupported draw once per operation and render scope. The caller must still
     * close/discard the source resource; this method never pretends that the operation reached
     * Vulkan or the TLAS.
     */
    public static void reportDiscard(String operation, String reason) {
        Scope scope = currentScope();
        if (claimDiscardReport(operation, scope)) {
            RadianceClient.LOGGER.warn(
                    "Radiance discarded unsupported render operation {} in {}[{}]: {}",
                    bounded(operation, 128),
                    scope.kind(),
                    bounded(scope.label(), 256),
                    bounded(reason, 512));
        }
    }

    static synchronized boolean claimDiscardReport(String operation, Scope scope) {
        if (REPORTED_DISCARDS.size() >= 128) return false;
        return REPORTED_DISCARDS.add(
                bounded(operation, 128) + '|' + scope.kind() + '|' + bounded(scope.label(), 256));
    }

    private static String bounded(String value, int limit) {
        String text = String.valueOf(value);
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private static final class ScopeFrame {
        private final Scope scope;

        private ScopeFrame(Scope scope) {
            this.scope = scope;
        }
    }

    public static final class ScopeToken implements AutoCloseable {
        private final ScopeFrame frame;
        private final Thread owner;
        private boolean closed;

        private ScopeToken(ScopeFrame frame, Thread owner) {
            this.frame = frame;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Render scope closed from another thread");
            }
            Deque<ScopeFrame> scopes = SCOPES.get();
            if (scopes.peek() != frame) {
                throw new IllegalStateException(
                        "Render scopes must close in LIFO order; active=" + currentScope());
            }
            scopes.pop();
            closed = true;
        }
    }
}
