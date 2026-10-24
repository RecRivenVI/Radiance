package com.radiance.client.proxy.world;

import java.util.Objects;
import java.util.function.Supplier;

/** Render-thread batch scope; invalidation never mutates snapshots already handed to workers. */
final class SectionPreparationCache<T> {
    private final Supplier<T> factory;
    private long revision;
    private T value;

    SectionPreparationCache(Supplier<T> factory) {
        this.factory = factory;
    }

    T get(long revision) {
        if (value == null || this.revision != revision) {
            T next = Objects.requireNonNull(factory.get());
            value = next;
            this.revision = revision;
        }
        return value;
    }
}
