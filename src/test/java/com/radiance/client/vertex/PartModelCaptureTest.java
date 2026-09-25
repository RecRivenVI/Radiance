package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class PartModelCaptureTest {
    @Test void ordinaryProvidersAllocateNoPartMapsAndCloseDropsExperimentState() {
        var ordinary = new StorageVertexConsumerProvider(0);
        try { assertNull(ordinary.partState); }
        finally { ordinary.close(); }
        assertNull(ordinary.partState);

        var experimental = new StorageVertexConsumerProvider(0);
        try {
            var state = experimental.partState();
            state.occurrences.put(new Object(), 1);
            assertSame(state, experimental.partState());
            assertEquals(1, experimental.partState().occurrences.size());
        } finally { experimental.close(); }
        assertNull(experimental.partState);
    }
    @Test void liveLayerIdentitySurvivesOtherChangesAndExpiredLayersCannotGrowWithoutBound() {
        var source = new PartModelCapture.Source(PartModelCapture.Snapshot.read(List.of(cube(false))));
        Object first = new Object();
        var identity = source.identity(first, 1);
        for (int i=0;i<63;i++) assertNotNull(source.identity(new Object(), 1));
        assertNull(source.identity(new Object(), 2));
        assertSame(identity, source.identity(first, 100));
        source.snapshot = PartModelCapture.Snapshot.read(List.of(cube(true)));
        assertSame(identity, source.identity(first, 101));
        assertNotNull(source.identity(new Object(), 122));
        assertEquals(2, source.layers.size());
        assertSame(identity, source.identity(first, 122));
        source.identity(new Object(), 300);
        assertNotEquals(identity.id, source.identity(first, 300).id);
    }
    static ModelPart.Cube cube(boolean mirror) {
        return new ModelPart.Cube(8, 16, -2, -3, -4, 4, 6, 8, .1f, .2f, .3f,
            mirror, 64, 64, EnumSet.allOf(Direction.class));
    }
    static class Sink implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        final List<int[]> appearance = new ArrayList<>();
        public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            vertices.add(new float[]{x,y,z,u,v,nx,ny,nz}); appearance.add(new int[]{color,overlay,light});
        }
        public VertexConsumer addVertex(float x,float y,float z){throw new AssertionError("Unexpected separate vertex");}
        public VertexConsumer setColor(int r,int g,int b,int a){throw new AssertionError();}
        public VertexConsumer setUv(float u,float v){throw new AssertionError();}
        public VertexConsumer setUv1(int u,int v){throw new AssertionError();}
        public VertexConsumer setUv2(int u,int v){throw new AssertionError();}
        public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError();}
    }
    @Test void localSnapshotMatchesActualVanillaCubeForMovingAndMirroredParts() {
        for(boolean mirror:List.of(false,true)) for(int tick=0;tick<25;tick++) {
            var source=List.of(cube(mirror),cube(!mirror));
            var snapshot=PartModelCapture.Snapshot.read(source);
            PoseStack pose=new PoseStack(); pose.translate(3.2,-5.7,1.1);
            pose.mulPose(new Quaternionf().rotationXYZ(tick*.035f,tick*-.067f,.5f));
            pose.scale(mirror?-.7f:.7f,.7f,.7f);
            Sink original=new Sink(), cached=new Sink();
            for(var cube:source)cube.compile(pose.last(),original,0x12ff34,0x450067,0x80aabbcc);
            snapshot.emit(cached,pose.last(),0x12ff34,0x450067,0x80aabbcc);
            assertEquals(original.vertices.size(),cached.vertices.size());
            for(int i=0;i<original.vertices.size();i++) {
                assertArrayEquals(original.vertices.get(i),cached.vertices.get(i),1e-6f);
                assertArrayEquals(original.appearance.get(i),cached.appearance.get(i));
            }
        }
    }
    @Test void mutableGeometryInvalidatesWithoutChangingQueuedSnapshot() {
        var cube=cube(false); var list=new ArrayList<>(List.of(cube));
        var snapshot=PartModelCapture.Snapshot.read(list);
        Sink before=new Sink();snapshot.emit(before,new PoseStack().last(),1,2,3);
        assertTrue(snapshot.matches(list));
        cube.polygons[0].vertices[0].pos.x+=.25f;
        assertFalse(snapshot.matches(list));
        Sink after=new Sink();snapshot.emit(after,new PoseStack().last(),1,2,3);
        for(int i=0;i<before.vertices.size();i++)assertArrayEquals(before.vertices.get(i),after.vertices.get(i));
        var changed=PartModelCapture.Snapshot.read(list);
        assertTrue(changed.matches(list));
        list.add(cube(true));assertFalse(changed.matches(list));
        list.clear();assertFalse(changed.matches(list));
    }
    @Test void sourceNormalAndUvChangesAreNotAssumedStatic() {
        var cube=cube(false);var source=List.of(cube);var snapshot=PartModelCapture.Snapshot.read(source);
        cube.polygons[0].normal.x+=.5f;assertFalse(snapshot.matches(source));
        snapshot=PartModelCapture.Snapshot.read(source);
        var v=cube.polygons[0].vertices[0];cube.polygons[0].vertices[0]=v.remap(.7f,.3f);
        assertFalse(snapshot.matches(source));
        cube.polygons[0].normal.x=Float.NaN;assertNull(PartModelCapture.Snapshot.read(source));
    }
    @Test void historyDoesNotDependOnOtherPartsVisibilityOrAppearance() {
        long a=PartModelCapture.historyIdentity(24,7,0);
        assertEquals(a,PartModelCapture.historyIdentity(24,7,0));
        assertNotEquals(a,PartModelCapture.historyIdentity(24,8,0));
        assertNotEquals(a,PartModelCapture.historyIdentity(24,7,1));
        assertNotEquals(a,PartModelCapture.historyIdentity(25,7,0));
        assertNotEquals(a,RigidModelCapture.historyIdentity(24,7,0));
        assertNotEquals(PartModelCapture.historyIdentity(0xffff_ffffL,0xff_ffff,31),
            PartModelCapture.historyIdentity(0x7fff_ffffL,0xff_ffff,31));
        assertThrows(IllegalArgumentException.class,()->PartModelCapture.historyIdentity(1,2,32));
    }
}
