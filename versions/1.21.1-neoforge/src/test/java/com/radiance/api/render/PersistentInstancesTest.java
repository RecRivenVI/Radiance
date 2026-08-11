package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.*;
import org.junit.jupiter.api.*;

class PersistentInstancesTest {
    @Test
    void acceptedSnapshotDoesNotAliasReusedStagingAndOwnerReplacementRetiresIndependently() {
        var data =
                ByteBuffer.allocateDirect(com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES)
                        .order(ByteOrder.nativeOrder());
        Object first = new Object(), second = new Object();
        data.putDouble(24, 1);
        PersistentInstances.accepted(73, first, data, 0, "base");
        data.putDouble(24, 2);
        assertTrue(PersistentInstances.changed(73, data, 0, "base"));
        data.putDouble(24, 1);
        assertFalse(PersistentInstances.changed(73, data, 0, "base"));
        data.putDouble(24, 2);
        PersistentInstances.accepted(73, second, data, 0, "base");
        data.putDouble(24, 9);
        assertTrue(PersistentInstances.changed(73, data, 0, "base"));
        data.putDouble(24, 2);
        assertFalse(PersistentInstances.changed(73, data, 0, "base"));
        PersistentInstances.removeOwner(first);
        assertTrue(PersistentInstances.known(73));
        PersistentInstances.removeOwner(second);
        assertFalse(PersistentInstances.known(73));
    }

    @AfterEach
    void clear() {
        PersistentInstances.invalidate();
        RenderRoutes.stop();
    }

    @Test
    void unchangedParametersSurviveAnEmptyViewAndOnlyRealChangesResubmit() {
        Object owner = new Object();
        long id = Long.MIN_VALUE | 71;
        ByteBuffer packet =
                ByteBuffer.allocateDirect(com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES)
                        .order(ByteOrder.nativeOrder());
        packet.putLong(0, 77).putLong(8, id).putLong(16, 1234).putDouble(24, 2).putFloat(136, 1);
        assertTrue(PersistentInstances.changed(id, packet, 0, "base"));
        PersistentInstances.accepted(id, owner, packet, 0, "base");
        assertFalse(PersistentInstances.changed(id, packet, 0, "base"));
        // JNI group-table offsets and a source buffer address are not instance state.
        packet.putLong(16, 8888).putInt(128, 9);
        assertFalse(PersistentInstances.changed(id, packet, 0, "base"));
        PersistentInstances.selected(0);
        assertEquals(1, PersistentInstances.counters()[0]);
        packet.putDouble(24, 3);
        assertTrue(PersistentInstances.changed(id, packet, 0, "base"));
        packet.putDouble(24, 2).putFloat(136, .5f);
        assertTrue(PersistentInstances.changed(id, packet, 0, "base"));
        packet.putFloat(136, 1);
        assertTrue(PersistentInstances.changed(id, packet, 0, "coating"));
        PersistentInstances.removeOwner(owner);
        assertEquals(0, PersistentInstances.counters()[0]);
        assertArrayEquals(new long[] {id}, PersistentInstances.removals());
    }

    @Test
    void acceptedSourceStateIsComparedBeforePackingAndSpacePoseIsNotInstanceContent() {
        Object owner = new Object();
        long id = Long.MIN_VALUE | 92;
        var pose = new org.joml.Matrix4f().translation(1, 2, 3).rotateY(.4f);
        var colour = com.radiance.render.contract.DrawStateSnapshot.IDENTITY;
        var packet =
                ByteBuffer.allocateDirect(com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES)
                        .order(ByteOrder.nativeOrder());
        packet.putLong(0, 7).putLong(8, id).putDouble(24, 3).putDouble(32, 4).putDouble(40, 5);
        pose.get(48, packet);
        packet.putInt(112, 2).putInt(116, 3).putInt(120, 4).putInt(124, 8).putLong(152, 11);
        colour.writeColour(packet, 136);
        LocalSurface.NONE.write(
                packet, com.radiance.render.contract.RenderAbi.RIGID_SURFACE_OFFSET);
        PersistentInstances.accepted(id, owner, packet, 0, "surface");
        assertTrue(
                PersistentInstances.matches(
                        id,
                        owner,
                        7,
                        2,
                        3,
                        4,
                        3,
                        4,
                        5,
                        8,
                        pose,
                        "surface",
                        colour,
                        11,
                        LocalSurface.NONE,
                        0));
        assertFalse(
                PersistentInstances.matches(
                        id,
                        new Object(),
                        7,
                        2,
                        3,
                        4,
                        3,
                        4,
                        5,
                        8,
                        pose,
                        "surface",
                        colour,
                        11,
                        LocalSurface.NONE,
                        0));
        assertFalse(
                PersistentInstances.matches(
                        id,
                        owner,
                        7,
                        2,
                        3,
                        4,
                        3,
                        4,
                        5,
                        8,
                        pose,
                        "surface",
                        colour,
                        12,
                        LocalSurface.NONE,
                        0));
        pose.rotateY(.1f);
        assertFalse(
                PersistentInstances.matches(
                        id,
                        owner,
                        7,
                        2,
                        3,
                        4,
                        3,
                        4,
                        5,
                        8,
                        pose,
                        "surface",
                        colour,
                        11,
                        LocalSurface.NONE,
                        0));
        PersistentInstances.refused(id);
        assertFalse(
                PersistentInstances.matches(
                        id,
                        owner,
                        7,
                        2,
                        3,
                        4,
                        3,
                        4,
                        5,
                        8,
                        pose,
                        "surface",
                        colour,
                        11,
                        LocalSurface.NONE,
                        0));
    }

    @Test
    void aBudgetRefusalDoesNotPretendTheBackendRetainedTheInstance() {
        ByteBuffer packet =
                ByteBuffer.allocateDirect(
                        com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES);
        Object owner = new Object();
        PersistentInstances.accepted(1, owner, packet, 0, "base");
        PersistentInstances.refused(1);
        assertTrue(PersistentInstances.changed(1, packet, 0, "base"));
        assertEquals(0, PersistentInstances.removals().length);
    }

    @Test
    void nativeVersionMismatchIsRejectedBeforeAnyGpuUse() {
        assertThrows(
                IllegalStateException.class,
                () -> RenderPlatform.initializeNative(RenderPlatform.API_VERSION + 1));
    }

    @Test
    void onDemandCountersKeepRejectedAndUnseenDrawsDistinct() {
        assertNull(RenderRoutes.request("ENTITY", "source", "DIRECT", "WORLD"));
        RenderRoutes.start();
        try (var scope =
                com.radiance.api.audit.DrawingAudit.begin("ENTITY", "source", "DIRECT", "WORLD")) {
            com.radiance.api.audit.DrawingAudit.consumed("native.scene");
        }
        var refused = RenderRoutes.request("GUI", "unknown", "CAPTURE", "UI");
        refused.rejected("Unsupported program");
        refused.close();
        var rows = RenderRoutes.snapshot();
        assertEquals(2, rows.size());
        assertEquals(1, rows.getFirst().submitted());
        assertEquals(0, rows.getFirst().rejected());
        assertEquals(1, rows.getLast().rejected());
        assertEquals("Unsupported program", rows.getLast().reason());
    }

    @Test
    void delayedSectionCompletionCannotEnterANewStatusWindow() {
        RenderRoutes.start();
        var old = RenderRoutes.request("SECTION", "worker", "DIRECT", "WORLD");
        RenderRoutes.stop();
        RenderRoutes.start();
        old.submitted("native.section.enqueue");
        old.close();
        assertTrue(RenderRoutes.snapshot().isEmpty());
    }

    @Test
    void viewInputsCannotBeMutatedAfterSampling() {
        var matrix = new org.joml.Matrix4f().translation(1, 2, 3);
        float[] fog = {1, 2, 3, 4};
        var view =
                new WorldView(
                        matrix, matrix, matrix, matrix, 0, 1, true, 0, 1, fog, 0, 0, 1, 2, 3, 4);
        matrix.identity();
        fog[0] = 99;
        assertEquals(1, view.viewMatrix().m30());
        assertEquals(1, view.atmosphereColor()[0]);
        view.viewMatrix().identity();
        assertEquals(1, view.viewMatrix().m30());
    }
}
