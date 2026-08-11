package com.radiance.compatibility.veil;

/** Explicit replacement for OpenGL's GL_PATCH_VERTICES state. */
public final class VeilPatchState {

    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> 0);

    private VeilPatchState() {
    }

    public static void set(int vertices) {
        if (vertices <= 0) throw new IllegalArgumentException("Patch size must be positive");
        CURRENT.set(vertices);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static int current() {
        return CURRENT.get();
    }
}
