package com.radiance.audit;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static com.radiance.audit.FrameTimings.Stage.*;

class FrameTimingsTest {
    @Test void worldSubstagesAndReentrantCallbacksStillPartitionTheFrame() {
        var now=new AtomicLong();var t=new FrameTimings(now::get);
        try(var root=t.enter(WORLD_RENDER_OTHER)) {
            now.set(10);
            try(var flywheel=t.enter(FLYWHEEL_DRAW)) {
                now.set(20);
                try(var geometry=t.enter(GEOMETRY_MARSHAL)) { now.set(40); }
                now.set(50);
            }
            try(var outer=t.enter(RENDER_STAGE_CALLBACKS)) {
                now.set(60);try(var inner=t.enter(RENDER_STAGE_CALLBACKS)) { now.set(80); }
                now.set(90);
            }
            now.set(100);
        }
        assertEquals(100,java.util.Arrays.stream(t.self).sum());
        assertEquals(20,t.self[WORLD_RENDER_OTHER.ordinal()]);
        assertEquals(20,t.self[FLYWHEEL_DRAW.ordinal()]);
        assertEquals(20,t.self[GEOMETRY_MARSHAL.ordinal()]);
        assertEquals(40,t.self[RENDER_STAGE_CALLBACKS.ordinal()]);
    }
    @Test void nestedSelfPartitionsFrameWithoutDoubleCounting() {
        var now=new AtomicLong();var t=new FrameTimings(now::get);
        try(var root=t.enter(FRAME_OTHER)) {
            now.set(10);
            try(var render=t.enter(ENTITIES)) {
                now.set(20);try(var jni=t.enter(GEOMETRY_MARSHAL)) { now.set(40); }
                now.set(70);
            }
            now.set(100);
        }
        assertEquals(100,java.util.Arrays.stream(t.self).sum());
        assertEquals(40,t.self[FRAME_OTHER.ordinal()]);
        assertEquals(40,t.self[ENTITIES.ordinal()]);
        assertEquals(20,t.self[GEOMETRY_MARSHAL.ordinal()]);
        assertEquals(60,t.inclusive[ENTITIES.ordinal()]);
    }
    @Test void exceptionAndDuplicateCloseKeepTheOriginalFailureAndBalance() {
        var now=new AtomicLong();var t=new FrameTimings(now::get);
        var root=t.enter(FRAME_OTHER);
        var original=new IllegalArgumentException("task");
        assertSame(original,assertThrows(IllegalArgumentException.class,()->{
            try(var child=t.enter(ENTITIES)) { now.set(10);throw original; }
        }));
        now.set(20);root.close();root.close();
        assertEquals(20,java.util.Arrays.stream(t.self).sum());
    }
    @Test void recursiveStageKeepsExclusiveTotals() {
        var now=new AtomicLong();var t=new FrameTimings(now::get);
        try(var a=t.enter(ENTITIES)) { now.set(10);try(var b=t.enter(ENTITIES)) {now.set(20);}now.set(30); }
        assertEquals(30,t.self[ENTITIES.ordinal()]);
        assertEquals(40,t.inclusive[ENTITIES.ordinal()]);
    }
}
