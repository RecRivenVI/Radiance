package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import org.joml.*;

/** Explicit, bounded dual-path check, never enabled for performance samples. */
public final class RigidModelProbe {
    private static final boolean ENABLED=Boolean.getBoolean("radiance.audit.rigidVerify");
    private static final Set<Long> MODELS=new HashSet<>();
    private static long calls, checked, vertices;
    private static double maxPosition,maxNormal;
    private static final Path OUTPUT=Path.of("radiance-audit","rigid-model-check.txt");
    private RigidModelProbe() {}
    public static void checkpoint(String stage) throws java.io.IOException {
        if (!ENABLED || checked==0) throw new IllegalStateException("No local geometry verification for "+stage);
        write("PASS");
        Files.copy(OUTPUT,OUTPUT.resolveSibling("rigid-model-"+stage+".txt"));
        calls=checked=vertices=0;maxPosition=maxNormal=0;MODELS.clear();
    }
    static Object field(Object owner,String name) throws ReflectiveOperationException {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    public static void check(Object draw) {
        if (!ENABLED || ++calls%97!=0 || checked>=4096) return;
        try {
            Object entry=field(draw,"entry");
            Matrix4f transform=(Matrix4f)field(draw,"transform");
            MeshData local=(MeshData)field(entry,"mesh");
            RenderType layer=(RenderType)field(entry,"layer");
            PoseStack pose=new PoseStack(); pose.last().pose().set(transform);
            float scale=new Vector3f(transform.m00(),transform.m01(),transform.m02()).length();
            pose.last().normal().set(transform).scale(1/scale);
            try (ByteBufferBuilder allocation=new ByteBufferBuilder(local.drawState().vertexCount()*128)) {
                PBRVertexConsumer reference=new PBRVertexConsumer(allocation,layer);
                if (entry.getClass().getName().contains("PartModelCapture")) {
                    Object snapshot=field(entry,"snapshot");
                    Method emit=snapshot.getClass().getDeclaredMethod("emit",VertexConsumer.class,PoseStack.Pose.class,int.class,int.class,int.class);
                    emit.setAccessible(true);emit.invoke(snapshot,reference,pose.last(),field(entry,"light"),field(entry,"overlay"),field(entry,"color"));
                } else for (Object quad:(List<?>)field(entry,"quads")) {
                        Method emit=quad.getClass().getDeclaredMethod("emit",VertexConsumer.class,PoseStack.Pose.class);
                        emit.setAccessible(true); emit.invoke(quad,reference,pose.last());
                    }
                try(MeshData expected=reference.end()) {
                    if(expected.drawState().vertexCount()!=local.drawState().vertexCount()) throw new AssertionError("vertex count");
                    ByteBuffer a=local.vertexBuffer().order(ByteOrder.nativeOrder());
                    ByteBuffer b=expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    for(int vertex=0;vertex<local.drawState().vertexCount();vertex++) {
                        int offset=vertex*128;
                        Vector3f p=new Vector3f(a.getFloat(offset),a.getFloat(offset+4),a.getFloat(offset+8));
                        transform.transformPosition(p);
                        double error=p.distance(b.getFloat(offset),b.getFloat(offset+4),b.getFloat(offset+8));
                        maxPosition=java.lang.Math.max(maxPosition,error);
                        if(error>3e-5) throw new AssertionError("position error="+error);
                        Vector3f n=new Vector3f(a.getFloat(offset+16),a.getFloat(offset+20),a.getFloat(offset+24));
                        pose.last().normal().transform(n);
                        error=n.distance(b.getFloat(offset+16),b.getFloat(offset+20),b.getFloat(offset+24));
                        maxNormal=java.lang.Math.max(maxNormal,error);
                        if(error>5e-5) throw new AssertionError("normal error="+error);
                        for(int i=12;i<128;i++) {
                            if(i>=16 && i<28) continue;
                            if(a.get(offset+i)!=b.get(offset+i)) throw new AssertionError("material byte="+i);
                        }
                    }
                    vertices+=local.drawState().vertexCount(); checked++; MODELS.add((Long)field(entry,"id"));
                }
            }
            if(checked%32==0) write("PASS");
        } catch(Throwable failure) {
            write("FAIL "+failure);
            throw new IllegalStateException("Rigid model dual-path verification failed",failure);
        }
    }
    private static void write(String status) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT,status+"\nsampledDraws="+checked+"\nvertices="+vertices+"\nmodels="+MODELS.size()
                +"\nmaxPosition="+maxPosition+"\nmaxNormal="+maxNormal+"\nNot a GPU hit or visual proof.\n");
        } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
