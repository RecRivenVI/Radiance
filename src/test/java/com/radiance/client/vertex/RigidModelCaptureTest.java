package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;

class RigidModelCaptureTest {
    static BakedQuad quad() { return new BakedQuad(new int[32],-1,Direction.UP,null,true); }
    static void emit(VertexConsumer c,PoseStack.Pose p,BakedQuad q) {
        c.putBulkData(p,q,new float[]{1,1,1,1},.5f,.6f,.7f,.8f,new int[]{1,2,3,4},7,true);
    }
    static class Sink implements VertexConsumer {
        final List<String> calls=new ArrayList<>(); int captured;
        @Override public void putBulkData(PoseStack.Pose p,BakedQuad q,float[] b,float r,float g,float blue,float a,int[] l,int o,boolean existing) {
            captured=q.getVertices()[0]; calls.add("quad");
            assertEquals(.8f,a); assertEquals(7,o); assertArrayEquals(new int[]{1,2,3,4},l); assertTrue(existing);
        }
        public VertexConsumer addVertex(float x,float y,float z) {calls.add("vertex");return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    @Test void localRecipeSkipsExpansionAndOwnsMutableSource() {
        Sink sink=new Sink(); var pose=new PoseStack().last(); var recorder=new RigidModelCapture.Recorder(sink,pose);
        BakedQuad q=quad(); q.getVertices()[0]=42; emit(recorder,pose,q);
        assertTrue(sink.calls.isEmpty());
        q.getVertices()[0]=99;
        recorder.flush(); assertEquals(42,sink.captured);
        recorder.flush(); assertEquals(List.of("quad"),sink.calls);
    }
    @Test void unsupportedOperationsReplayBeforeForwardingInOriginalOrder() {
        Sink sink=new Sink(); var pose=new PoseStack().last(); var recorder=new RigidModelCapture.Recorder(sink,pose);
        emit(recorder,pose,quad()); recorder.addVertex(1,2,3); emit(recorder,pose,quad());
        assertEquals(List.of("quad","vertex","quad"),sink.calls); assertTrue(recorder.fallback);
    }
    @Test void changedPoseAndCustomQuadRetainFallback() {
        Sink sink=new Sink(); PoseStack pose=new PoseStack(); var recorder=new RigidModelCapture.Recorder(sink,pose.last());
        emit(recorder,pose.last(),quad()); pose.translate(1,0,0); emit(recorder,pose.last(),quad());
        assertTrue(recorder.fallback); assertEquals(2,sink.calls.size());
        recorder=new RigidModelCapture.Recorder(sink,pose.last());
        emit(recorder,pose.last(),new BakedQuad(new int[32],-1,Direction.UP,null,true) {});
        assertTrue(recorder.fallback);
    }
    @Test void emittedGeometryKeepsItsPoseWhenCallerChangesStackAfterLastQuad() {
        PoseStack source=new PoseStack();source.translate(2,3,4);
        var recorder=new RigidModelCapture.Recorder(new Sink(),source.last());
        emit(recorder,source.last(),quad());
        source.translate(5,6,7);
        assertEquals(2,recorder.pose.pose().m30());
        assertEquals(3,recorder.pose.pose().m31());
        assertEquals(4,recorder.pose.pose().m32());
        assertFalse(recorder.fallback);
    }
    @Test void appearanceAndSourceMutationInvalidateLocalRecipe() {
        var pose=new PoseStack().last(); BakedQuad q=quad();
        var a=new RigidModelCapture.Recorder(new Sink(),pose); emit(a,pose,q);
        var b=new RigidModelCapture.Recorder(new Sink(),pose); emit(b,pose,q);
        assertTrue(RigidModelCapture.same(a.quads,b.quads));
        q.getVertices()[3]=0x80ff8080;
        var c=new RigidModelCapture.Recorder(new Sink(),pose); emit(c,pose,q);
        assertFalse(RigidModelCapture.same(a.quads,c.quads));
        c.quads.getFirst().lights[0]=0xFFFF; assertFalse(RigidModelCapture.same(b.quads,c.quads));
    }
    @Test void rigidAndMirroredPosesAcceptedSkewAndNonuniformRejected() {
        PoseStack p=new PoseStack(); assertTrue(RigidModelCapture.uniformPose(p.last()));
        p.translate(3,4,5); p.scale(-.5f,.5f,.5f); assertTrue(RigidModelCapture.uniformPose(p.last()));
        p.scale(2,1,1); assertFalse(RigidModelCapture.uniformPose(p.last()));
        p=new PoseStack(); p.last().pose().m10(.1f); assertFalse(RigidModelCapture.uniformPose(p.last()));
        p=new PoseStack(); p.scale(0,0,0); assertFalse(RigidModelCapture.uniformPose(p.last()));
    }
    @Test void visibilityChangesCannotReuseShiftedPartHistory() {
        long first=RigidModelCapture.historyIdentity(23,2,0);
        long second=RigidModelCapture.historyIdentity(23,2,1);
        long remaining=RigidModelCapture.historyIdentity(23,1,0);
        assertNotEquals(first,second);assertNotEquals(first,remaining);assertNotEquals(second,remaining);
        assertEquals(first,RigidModelCapture.historyIdentity(23,2,0));
        assertNotEquals(first,RigidModelCapture.historyIdentity(24,2,0));
        assertNotEquals(RigidModelCapture.historyIdentity(0xffff_ffffL,1024,1023),
            RigidModelCapture.historyIdentity(0x7fff_ffffL,1024,1023));
        assertTrue(first<0);
        assertThrows(IllegalArgumentException.class,()->RigidModelCapture.historyIdentity(23,1,1));
    }
}
