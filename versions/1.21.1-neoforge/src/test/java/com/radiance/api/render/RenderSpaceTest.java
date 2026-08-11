package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.*;
import org.junit.jupiter.api.Test;

class RenderSpaceTest {
    @Test
    void explicitSourceEndpointsClampAndFinalPoseEndsInterpolation() {
        var source = new RenderSpace();
        var independent = new RenderSpace();
        long unrelated = independent.version();
        var position = new Vector3d(30000000.125, 2, -30000000.375);
        var before = new PoseEndpoint(position, new Quaterniond(), new Vector3d(-2, 3, 4));
        position.x = 0;
        var after =
                new PoseEndpoint(
                        new Vector3d(30000002.125, 6, -30000004.375),
                        new Quaterniond().rotateY(.8),
                        new Vector3d(-4, 5, 6));
        source.motion(before, after, 10, 12, 11);
        var middle = source.transform(new Matrix4d());
        assertEquals(30000001.125, middle.m30());
        assertEquals(before.interpolate(after, 10, 12, 11), middle);
        assertEquals(unrelated, independent.version());
        source.sample(20);
        assertEquals(before.interpolate(after, 10, 12, 12), source.transform(new Matrix4d()));
        source.pose(new Matrix4d(), new Matrix3f());
        assertThrows(IllegalStateException.class, () -> source.sample(11));
        var mirrored = new PoseEndpoint(new Vector3d(), new Quaterniond(), new Vector3d(1, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> source.motion(before, mirrored, 10, 12, 11));
        assertEquals(new Matrix4d(), source.transform(new Matrix4d()));
        source.close();
        independent.close();
    }

    @Test
    void parentMotionPreservesLocalMembersAndExactRevisions() {
        var parent = new RenderSpace();
        var child = new RenderSpace(parent);
        var pose = new Matrix4d().translation(30000000.125, 10, -30000000.375);
        parent.pose(pose, new Matrix3f());
        child.pose(new Matrix4d().translation(.0625, 2, .125), new Matrix3f());
        var first = child.transform(new Matrix4d());
        assertEquals(30000000.1875, first.m30());
        long version = child.version();
        parent.pose(pose, new Matrix3f());
        assertEquals(version, child.version());
        parent.pose(pose.rotateY(.6), new Matrix3f().rotationY(.6f));
        assertNotEquals(version, child.version());
        assertNotEquals(first, child.transform(new Matrix4d()));
        assertThrows(IllegalStateException.class, parent::close);
        child.close();
        parent.close();
    }

    @Test
    void worldCloseInvalidatesSpacesWithoutNativeCalls() {
        var parent = new RenderSpace();
        var child = new RenderSpace(parent);
        RenderSpace.worldClosed();
        assertThrows(IllegalStateException.class, () -> child.transform(new Matrix4d()));
        assertThrows(IllegalStateException.class, () -> parent.normal(new Matrix3f()));
    }
}
