package com.radiance.client.render;

import com.radiance.client.RadianceClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Explicit render-scope contract shared by the Java capture boundaries.
 *
 * <p>A vanilla {@code MeshData} draw has no Radiance world-geometry identity,
 * material contract, or TLAS submission handle.  It may therefore be routed
 * to the Vulkan UI only while an explicit UI scope is active; world and
 * dimension-extension scopes are rejected until a real world-capture bridge
 * supplies that missing contract.</p>
 */
public final class RenderCaptureContract {

    public enum ScopeKind {
        OUTSIDE,
        WORLD_STAGE,
        WORLD_RASTER,
        GUI,
        CAMERA_OVERLAY,
        DIMENSION_EFFECT,
        UNSUPPORTED_OPENGL
    }

    public enum BufferDrawRoute {
        VULKAN_UI,
        VULKAN_CUSTOM_TARGET,
        VULKAN_WORLD_RASTER,
        REJECT_WORLD_MESH,
        REJECT_UNSCOPED
    }

    public record Scope(ScopeKind kind, String label) {
        public Scope {
            kind = kind == null ? ScopeKind.OUTSIDE : kind;
            label = label == null || label.isBlank() ? kind.name() : label;
        }
    }

    public record BufferDrawDecision(BufferDrawRoute route, Scope scope, String reason) {
    }

    private static final ThreadLocal<Deque<ScopeFrame>> SCOPES =
        ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> REPORTED_DISCARDS = ConcurrentHashMap.newKeySet();

    private RenderCaptureContract() {
    }

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
        return switch (scope.kind()) {
            case WORLD_RASTER -> new BufferDrawDecision(
                BufferDrawRoute.VULKAN_WORLD_RASTER, scope,
                "explicit world effect after native color and depth resolve");
            case GUI, CAMERA_OVERLAY -> new BufferDrawDecision(
                BufferDrawRoute.VULKAN_UI,
                scope,
                "explicit UI scope");
            case WORLD_STAGE, DIMENSION_EFFECT -> {
                int framebuffer;
                try {
                    framebuffer = com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(
                        com.radiance.client.proxy.vulkan.FramebufferProxy.DRAW_FRAMEBUFFER);
                } catch (UnsatisfiedLinkError unavailableDuringContractTest) {
                    framebuffer = 0;
                }
                if (framebuffer != 0) {
                    yield new BufferDrawDecision(BufferDrawRoute.VULKAN_CUSTOM_TARGET, scope,
                        "explicit Vulkan-backed framebuffer " + framebuffer);
                }
                yield new BufferDrawDecision(BufferDrawRoute.REJECT_WORLD_MESH, scope,
                    "raw BufferUploader draw has no RenderType/world material metadata");
            }
            case UNSUPPORTED_OPENGL -> new BufferDrawDecision(
                BufferDrawRoute.REJECT_UNSCOPED,
                scope,
                "OpenGL scope is unsupported under GLFW_NO_API");
            case OUTSIDE -> new BufferDrawDecision(
                BufferDrawRoute.REJECT_UNSCOPED,
                scope,
                "draw is outside an explicit GUI, camera, or world stage scope");
        };
    }

    public static UnsupportedOperationException reject(BufferDrawDecision decision,
        String operation) {
        return new UnsupportedOperationException(
            "Radiance rejected " + operation + " in " + decision.scope().kind()
                + "[" + decision.scope().label() + "]: " + decision.reason());
    }

    public static UnsupportedOperationException rejectOpenGl(String operation) {
        Scope scope = currentScope();
        return new UnsupportedOperationException(
            "Radiance rejected direct OpenGL/FBO operation " + operation
                + " in " + scope.kind() + "[" + scope.label()
                + "]: GLFW_NO_API has no OpenGL context");
    }

    /**
     * Records an unsupported draw once per operation and render scope.  The
     * caller must still close/discard the source resource; this method never
     * pretends that the operation reached Vulkan or the TLAS.
     */
    public static void reportDiscard(String operation, String reason) {
        Scope scope = currentScope();
        String key = operation + '|' + scope.kind() + '|' + scope.label();
        if (REPORTED_DISCARDS.add(key)) {
            RadianceClient.LOGGER.warn(
                "Radiance discarded unsupported render operation {} in {}[{}]: {}",
                operation, scope.kind(), scope.label(), reason);
        }
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
