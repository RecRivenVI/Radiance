package com.radiance.client.render;

/** Raster previews retain their producer's baked light/shade; the PT world is unchanged. */
public final class RasterPreviewScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private final Thread owner = Thread.currentThread();
    private boolean closed;
    private RasterPreviewScope() { DEPTH.set(DEPTH.get() + 1); }
    public static RasterPreviewScope enter() { return new RasterPreviewScope(); }
    public static boolean active() {
        // Decide while vertices are produced, not when their buffers eventually draw.
        // Screen/Gui capture also covers previews outside Catnip (inventory, tooltips,
        // third-party screens). An explicit nested world capture remains a PT producer.
        return switch (RenderCaptureContract.currentScope().kind()) {
            case GUI, CAMERA_OVERLAY, WORLD_RASTER -> true;
            case WORLD_STAGE, DIMENSION_EFFECT -> false;
            default -> DEPTH.get() != 0;
        };
    }
    public static boolean useAmbientOcclusion(java.util.function.BooleanSupplier original) {
        // PT derives occlusion from ray visibility. Raster previews still need the user's
        // original smooth-lighting choice; removing it changes translucent/cutout vertex colors.
        return active() && original.getAsBoolean();
    }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner) throw new IllegalStateException("Preview scope thread changed");
        int depth = DEPTH.get() - 1;
        if (depth == 0) DEPTH.remove(); else DEPTH.set(depth);
        closed = true;
    }
}
