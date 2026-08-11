package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Tracks the {@link ShaderInstance} whose {@code apply()} call owns the current raster draw.
 *
 * <p>Vanilla keeps this state implicitly in OpenGL's current program. Radiance has no OpenGL
 * context, so the state has to be represented explicitly until {@code clear()} is called.</p>
 */
public final class AppliedShaderState {

    private static final Binding<ShaderInstance> CURRENT = new Binding<>();

    private AppliedShaderState() {
    }

    public static void apply(ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
        CURRENT.apply(shader);
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        CURRENT.clear();
    }

    public static void clearIfCurrent(ShaderInstance shader) {
        CURRENT.clearIfCurrent(shader);
    }

    public static ShaderInstance requireCurrent() {
        RenderSystem.assertOnRenderThread();
        ShaderInstance shader = CURRENT.current();
        if (shader == null) {
            throw new IllegalStateException(
                "VertexBuffer.draw() requires a ShaderInstance.apply() on the render thread");
        }
        return shader;
    }

    static final class Binding<T> {

        private final ThreadLocal<T> current = new ThreadLocal<>();

        void apply(T value) {
            if (value == null) {
                throw new NullPointerException("Applied shader must not be null");
            }
            current.set(value);
        }

        T current() {
            return current.get();
        }

        void clear() {
            current.remove();
        }

        void clearIfCurrent(T value) {
            if (current.get() == value) {
                current.remove();
            }
        }
    }
}
