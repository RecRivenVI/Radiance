package com.radiance.client.proxy.world;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.lwjgl.system.MemoryUtil;

/** UTF-8 ownership for one synchronous geometry submission; never retained across frames/reloads. */
final class SubmissionStrings implements AutoCloseable {
    private final Map<String, ByteBuffer> strings = new HashMap<>();
    private final Function<String, ByteBuffer> allocate;
    private final Consumer<ByteBuffer> release;
    private boolean closed;

    SubmissionStrings() {
        this(value -> MemoryUtil.memUTF8(value, true), MemoryUtil::memFree);
    }

    SubmissionStrings(Function<String, ByteBuffer> allocate, Consumer<ByteBuffer> release) {
        this.allocate = allocate;
        this.release = release;
    }

    long address(String value) {
        if (closed) throw new IllegalStateException("Geometry submission strings are closed");
        ByteBuffer buffer = strings.get(value);
        if (buffer == null) {
            buffer = allocate.apply(value);
            // HashMap growth can fail after native allocation has succeeded.
            try {
                strings.put(value, buffer);
            } catch (RuntimeException | Error failure) {
                release.accept(buffer);
                throw failure;
            }
        }
        return MemoryUtil.memAddress(buffer);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (ByteBuffer buffer : strings.values()) release.accept(buffer);
        strings.clear();
    }
}
