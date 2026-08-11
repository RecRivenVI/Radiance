package com.radiance.client.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiViewportTest {
    @Test void physicalContentRectangleMapsExactlyToOffscreenCorners() {
        var view = UiViewport.enclosing(-.25f, -.4f, .3f, .45f, 3840, 2054);
        assertTrue(view.width() < 3840);
        assertTrue(view.height() < 2054);
        var crop = view.cropProjection(new Matrix4f());
        var p = crop.transform(new Vector4f(view.leftNdc(), view.topNdc(), 0, 1));
        assertEquals(-1, p.x, 1e-5); assertEquals(1, p.y, 1e-5);
        p = crop.transform(new Vector4f(view.rightNdc(), view.bottomNdc(), 0, 1));
        assertEquals(1, p.x, 1e-5); assertEquals(-1, p.y, 1e-5);
    }
    @Test void resizePreservesNormalizedGeometryOutsideWindow() {
        for (int width : new int[] {854, 1920, 3840}) {
            var view = UiViewport.enclosing(-2, -.25f, .5f, 2, width, 1080);
            assertTrue(view.x() < 0); assertTrue(view.y() < 0);
            var crop = view.cropProjection(new Matrix4f());
            var p = crop.transform(new Vector4f(0, 0, 0, 1));
            float physicalX = view.x() + (p.x + 1) * view.width() / 2;
            float physicalY = view.y() + (1-p.y) * view.height() / 2;
            assertEquals(width/2f, physicalX, 1e-3); assertEquals(540, physicalY, 1e-3);
        }
    }
    @Test void transitionTranslationDoesNotCollapseOrResizeTheCamera() {
        var original = UiViewport.enclosing(-.25f, -.5f, .25f, .5f, 3840, 2054);
        for (float offset : new float[] {-.5f, 0, 1, 2, 3}) {
            var moved = UiViewport.enclosing(offset-.25f, -.5f, offset+.25f, .5f, 3840, 2054);
            assertEquals(original.width(), moved.width());
            assertEquals(original.height(), moved.height());
            var p = moved.cropProjection(new Matrix4f()).transform(new Vector4f(offset, 0, 0, 1));
            var expected = original.cropProjection(new Matrix4f()).transform(new Vector4f(0, 0, 0, 1));
            assertEquals(expected.x, p.x, 1e-5);
            assertEquals(expected.y, p.y, 1e-5);
        }
    }
}
