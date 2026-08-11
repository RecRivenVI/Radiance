package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.render.contract.DrawTargetContract.GuiRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuiDrawScopeTest {
    @Test
    void batchesAndNestedRolesAreRecordedInTheirActualScope() {
        var state = new AtomicInteger();
        List<Integer> flushes = new ArrayList<>();
        GuiDrawScope.run(
                GuiRole.BACKGROUND_EFFECT.nativeId(),
                () -> flushes.add(state.get()),
                state::getAndSet,
                () -> {
                    assertEquals(1, state.get());
                    GuiDrawScope.run(
                            GuiRole.WORLD_REPLACEMENT.nativeId(),
                            () -> flushes.add(state.get()),
                            state::getAndSet,
                            () -> assertEquals(2, state.get()));
                    assertEquals(1, state.get());
                });
        assertEquals(List.of(0, 1, 2, 1), flushes);
        assertEquals(0, state.get());
    }

    @Test
    void drawFailureSurvivesFlushAndRestoreFailures() {
        var original = new IllegalArgumentException("draw");
        var flushFailure = new IllegalStateException("flush");
        var restoreFailure = new IllegalStateException("restore");
        var calls = new AtomicInteger();
        var state = new AtomicInteger();
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                GuiDrawScope.run(
                                        1,
                                        () -> {
                                            if (calls.incrementAndGet() == 2) throw flushFailure;
                                        },
                                        role -> {
                                            int previous = state.getAndSet(role);
                                            if (role == 0) throw restoreFailure;
                                            return previous;
                                        },
                                        () -> {
                                            throw original;
                                        }));
        assertSame(original, thrown);
        assertArrayEquals(new Throwable[] {flushFailure, restoreFailure}, thrown.getSuppressed());
        assertEquals(0, state.get());
    }

    @Test
    void unsuccessfulInitialFlushDoesNotEnterTheBackgroundRole() {
        var state = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        GuiDrawScope.run(
                                1,
                                () -> {
                                    throw new IllegalStateException("before draw");
                                },
                                state::getAndSet,
                                () -> fail("must not draw")));
        assertEquals(0, state.get());
    }

    @Test
    void cleanupFailureIsPropagatedAfterRestoringTheRole() {
        var calls = new AtomicInteger();
        var state = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        GuiDrawScope.run(
                                1,
                                () -> {
                                    if (calls.incrementAndGet() == 2)
                                        throw new IllegalStateException("flush");
                                },
                                state::getAndSet,
                                () -> {}));
        assertEquals(0, state.get());
    }
}
