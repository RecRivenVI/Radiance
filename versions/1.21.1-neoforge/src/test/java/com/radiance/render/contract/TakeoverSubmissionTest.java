package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TakeoverSubmissionTest {
    @Test
    void sourceLayersKeepOrderAndOwnerCompositionWithoutGameObjects() {
        var input =
                new ArrayList<>(
                        List.of(
                                new TakeoverSubmission.Layer(
                                        0,
                                        TakeoverSubmission.LayerKind.SURFACE_COATING,
                                        TakeoverSubmission.Composition.SURFACE_OWNER),
                                new TakeoverSubmission.Layer(
                                        1,
                                        TakeoverSubmission.LayerKind.EYES,
                                        TakeoverSubmission.Composition.EYE_OWNER),
                                new TakeoverSubmission.Layer(
                                        2,
                                        TakeoverSubmission.LayerKind.DECORATION,
                                        TakeoverSubmission.Composition.SOURCE_ORDER)));
        var plan = new TakeoverSubmission.LayerPlan(input);
        input.clear();
        assertEquals(3, plan.layers().size());
        assertTrue(plan.eyeOwner());
        assertFalse(plan.surfaceOwner());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TakeoverSubmission.LayerPlan(
                                List.of(plan.layers().get(1), plan.layers().get(0))));
        assertThrows(IllegalArgumentException.class, () -> new TakeoverSubmission.Program(0, 1));
    }

    @Test
    void existingTargetRoutesExpressWorldGuiAndScreenWithoutChangingExecution() {
        assertEquals(
                TakeoverSubmission.Domain.GUI,
                DrawTargetContract.classify(
                                new DrawTargetContract.Scope(
                                        DrawTargetContract.ScopeKind.GUI, "preview"),
                                7)
                        .domain()
                        .orElseThrow());
        assertEquals(
                TakeoverSubmission.Domain.SCREEN,
                DrawTargetContract.classify(
                                new DrawTargetContract.Scope(
                                        DrawTargetContract.ScopeKind.CAMERA_OVERLAY, "water"),
                                0)
                        .domain()
                        .orElseThrow());
        assertEquals(
                TakeoverSubmission.Domain.WORLD,
                DrawTargetContract.classify(
                                new DrawTargetContract.Scope(
                                        DrawTargetContract.ScopeKind.WORLD_STAGE, "terrain"),
                                0)
                        .domain()
                        .orElseThrow());
        assertTrue(DrawTargetContract.sourceDomain(DrawTargetContract.ScopeKind.OUTSIDE).isEmpty());
    }

    @Test
    void oldCompletionKeepsPublishedGeometryAndReloadCannotCrossAnUpdate() {
        var old = new TakeoverSubmission.Resource(1, 7, 1, 1, 1);
        var desired = new TakeoverSubmission.Resource(1, 7, 2, 1, 1);
        var pending = new TakeoverSubmission.Publication(desired, old, old);
        assertFalse(pending.mayPublish());
        assertEquals(old, pending.visible());
        assertEquals(desired, new TakeoverSubmission.Publication(desired, desired, old).visible());
        assertEquals(old, new TakeoverSubmission.Publication(null, desired, old).visible());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TakeoverSubmission.Update(
                                old,
                                new TakeoverSubmission.Resource(2, 7, 1, 1, 1),
                                TakeoverSubmission.Change.TOPOLOGY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TakeoverSubmission.Update(
                                old, desired, TakeoverSubmission.Change.TRANSFORM));
    }

    @Test
    void recursiveRequestsRestoreOwnershipAndCannotDoubleSubmit() {
        try (var outer = TakeoverSubmission.Ownership.open()) {
            outer.claim(TakeoverSubmission.Executor.DIRECT);
            try (var inner = TakeoverSubmission.Ownership.open()) {
                assertThrows(
                        IllegalStateException.class,
                        () -> outer.claim(TakeoverSubmission.Executor.CAPTURE));
                inner.claim(TakeoverSubmission.Executor.CAPTURE);
            }
            assertEquals(TakeoverSubmission.Executor.DIRECT, outer.executor());
            assertThrows(
                    IllegalStateException.class,
                    () -> outer.claim(TakeoverSubmission.Executor.CAPTURE));
        }
        try (var afterException = TakeoverSubmission.Ownership.open()) {
            afterException.claim(TakeoverSubmission.Executor.CAPTURE);
        }
    }

    @Test
    void instancesAndAttachmentVersionsAreIndependentSnapshots() {
        var resource = new TakeoverSubmission.Resource(2, 8, 1, 1, 1);
        var dependencies = new ArrayList<>(List.of(new TakeoverSubmission.Dependency(1, 4, 5)));
        var a =
                new TakeoverSubmission.Draw(
                        new TakeoverSubmission.Instance(1, 0, resource),
                        TakeoverSubmission.Domain.GUI,
                        TakeoverSubmission.Executor.DIRECT,
                        new TakeoverSubmission.Program(1, 1),
                        new DrawStateSnapshot(0, 1, 1, 0, 0, .5f),
                        dependencies);
        var b =
                new TakeoverSubmission.Draw(
                        new TakeoverSubmission.Instance(2, 0, resource),
                        TakeoverSubmission.Domain.WORLD,
                        TakeoverSubmission.Executor.DIRECT,
                        new TakeoverSubmission.Program(2, 1),
                        DrawStateSnapshot.IDENTITY,
                        List.of());
        dependencies.clear();
        assertEquals(1, a.dependencies().size());
        assertNotEquals(a.state(), b.state());
        assertEquals(a.instance().resource(), b.instance().resource());
    }
}
