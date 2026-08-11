package com.radiance.client.render;

/** Only the named F3+G renderer opts into active emission among general debug renderers. */
public final class DebugEmissionScope {
    private static final ThreadLocal<Boolean> CHUNK_BORDERS = new ThreadLocal<>();

    private DebugEmissionScope() {}

    public static boolean isChunkBorder() { return Boolean.TRUE.equals(CHUNK_BORDERS.get()); }

    public static void withChunkBorders(Runnable render) {
        Boolean previous = CHUNK_BORDERS.get();
        CHUNK_BORDERS.set(true);
        try {
            render.run();
        } finally {
            if (previous == null) CHUNK_BORDERS.remove();
            else CHUNK_BORDERS.set(previous);
        }
    }
}
