package com.radiance.audit;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
class ProducerCensusTest {
    @Test void explicitPersistentDrawDoesNotConsumeLegacyAttribution() {
        var queue=new java.util.ArrayDeque<>(java.util.List.of("renderer-a","renderer-b"));
        assertEquals("rigid-unassigned",ProducerCensus.consumeFace("rigid-unassigned",queue,()->"missing"));
        assertEquals(2,queue.size());
        assertEquals("renderer-a",ProducerCensus.consumeFace(null,queue,()->"missing"));
        assertEquals("renderer-b",ProducerCensus.consumeFace(null,queue,()->"missing"));
        assertEquals("missing",ProducerCensus.consumeFace(null,queue,()->"missing"));
    }
    @Test void nestedModelTimeIsExcludedOnceFromRendererSelfEvenOnFailure() {
        var original=ProducerCensus.clock;
        AtomicLong now=new AtomicLong(); ProducerCensus.clock=now::get;
        try {
            var renderer=new ProducerCensus.Timer(); now.set(10);
            var model=new ProducerCensus.Timer(); now.set(15);
            var nested=new ProducerCensus.Timer(); now.set(20); nested.close();
            now.set(30); model.close(); now.set(50); renderer.close();
            assertEquals(50,renderer.elapsed); assertEquals(30,renderer.self);
            assertEquals(20,model.elapsed); assertEquals(15,model.self);
            var parent=new ProducerCensus.Timer(); now.set(60);
            try(var child=new ProducerCensus.Timer()) {now.set(70);throw new IllegalArgumentException();}
            catch(IllegalArgumentException expected) {}
            now.set(90);parent.close();assertEquals(30,parent.self);
        } finally {ProducerCensus.clock=original;}
    }
}
