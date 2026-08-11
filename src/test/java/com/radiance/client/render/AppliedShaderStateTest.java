package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AppliedShaderStateTest {

    @Test
    void applyReplacesTheCurrentValueAndClearRemovesIt() {
        AppliedShaderState.Binding<Object> binding = new AppliedShaderState.Binding<>();
        Object first = new Object();
        Object second = new Object();

        binding.apply(first);
        assertSame(first, binding.current());
        binding.apply(second);
        assertSame(second, binding.current());
        binding.clearIfCurrent(first);
        assertSame(second, binding.current());
        binding.clearIfCurrent(second);
        assertNull(binding.current());
    }

    @Test
    void appliedStateIsBoundToTheCallingThread() throws Exception {
        AppliedShaderState.Binding<String> binding = new AppliedShaderState.Binding<>();
        AtomicReference<String> workerValue = new AtomicReference<>();
        binding.apply("render-thread");

        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                workerValue.set(binding.current());
                binding.apply("worker-thread");
                assertEquals("worker-thread", binding.current());
                binding.clear();
            }).get();
        }

        assertNull(workerValue.get());
        assertEquals("render-thread", binding.current());
        binding.clear();
    }
}
