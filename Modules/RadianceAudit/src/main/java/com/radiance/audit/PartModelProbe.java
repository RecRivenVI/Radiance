package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/** Optional real Cube.compile comparison, never enabled in formal performance samples. */
public final class PartModelProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.partVerify");
    private static long calls, checked, vertices;
    private static double maxPosition, maxNormal;
    private static final Set<Long> models = new HashSet<>();
    private static long targetOwner = -1;
    private PartModelProbe() {}
    public static void compile(List<ModelPart.Cube> cubes, PoseStack.Pose pose, VertexConsumer consumer,
                               int light, int overlay, int color, Runnable original) {
        if (!ENABLED || consumer.getClass() != PBRVertexConsumer.class || ++calls % 89 != 0 || checked >= 4096) {
            original.run(); return;
        }
        try {
            Object owner = RigidModelProbe.field(consumer,"rigidOwner");
            if (owner == null) { original.run(); return; }
            if (targetOwner >= 0 && (long) RigidModelProbe.field(owner,"rigidInstance") != targetOwner) {
                original.run(); return;
            }
            List<?> draws = (List<?>) RigidModelProbe.field(owner,"rigidDraws");
            int before = draws.size(); original.run();
            if (draws.size() == before) return; // Explicit fallback, not a cache success.
            if (draws.size() != before + 1) throw new AssertionError("Part draw count");
            Object entry = RigidModelProbe.field(draws.getLast(),"entry");
            MeshData mesh = (MeshData) RigidModelProbe.field(entry,"mesh");
            RenderType layer = (RenderType) RigidModelProbe.field(entry,"layer");
            try (ByteBufferBuilder allocation = new ByteBufferBuilder(mesh.drawState().vertexCount()*128)) {
                PBRVertexConsumer reference = new PBRVertexConsumer(allocation,layer);
                for (ModelPart.Cube cube:cubes) cube.compile(pose,reference,light,overlay,color);
                try (MeshData expected = reference.end()) {
                    if (expected.drawState().vertexCount()!=mesh.drawState().vertexCount()) throw new AssertionError("Part vertices");
                    ByteBuffer a=mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                    ByteBuffer b=expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    for(int i=0;i<mesh.drawState().vertexCount();i++) {
                        int at=i*128;
                        Vector3f p=new Vector3f(a.getFloat(at),a.getFloat(at+4),a.getFloat(at+8));
                        pose.pose().transformPosition(p);
                        double error=p.distance(b.getFloat(at),b.getFloat(at+4),b.getFloat(at+8));
                        maxPosition=Math.max(maxPosition,error);
                        if(error>3e-5)throw new AssertionError("Part position="+error);
                        Vector3f n=new Vector3f(a.getFloat(at+16),a.getFloat(at+20),a.getFloat(at+24));
                        pose.transformNormal(n,n);
                        error=n.distance(b.getFloat(at+16),b.getFloat(at+20),b.getFloat(at+24));
                        maxNormal=Math.max(maxNormal,error);
                        if(error>5e-5)throw new AssertionError("Part normal="+error);
                        for(int j=12;j<128;j++)if((j<16||j>=28)&&a.get(at+j)!=b.get(at+j))
                            throw new AssertionError("Part material byte="+j);
                    }
                    checked++;vertices+=mesh.drawState().vertexCount();models.add((Long)RigidModelProbe.field(entry,"id"));
                }
            }
            if(checked%32==0)write("PASS");
        } catch(Throwable failure) {write("FAIL "+failure);throw new IllegalStateException("Part geometry comparison",failure);}
    }
    public static long checked() { return checked; }
    public static void target(int entity) {
        targetOwner = Integer.toUnsignedLong(entity);
        checked=0;vertices=0;models.clear();maxPosition=maxNormal=0;
    }
    public static void checkpoint(String stage) {
        if(checked==0)throw new IllegalStateException("No part draw compared at "+stage);
        write("PASS stage="+stage);
        try {Files.copy(Path.of("radiance-audit/part-model-check.txt"),Path.of("radiance-audit/part-model-"+stage+".txt"));}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        checked=0;vertices=0;models.clear();maxPosition=maxNormal=0;
    }
    private static void write(String status) {
        try {Files.createDirectories(Path.of("radiance-audit"));
            Files.writeString(Path.of("radiance-audit/part-model-check.txt"),status+"\nchecked="+checked
                +"\nvertices="+vertices+"\nmodels="+models.size()+"\nmaxPosition="+maxPosition+"\nmaxNormal="+maxNormal
                +"\nReference: actual Cube.compile; not GPU hit or user visual parity.\n");
        } catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
