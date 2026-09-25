package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.texture.TextureTasks;
import com.radiance.client.constant.Constants;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Bounded rigid baked-model experiment. Renderer/model/tint callbacks still run every frame.
 * Only exact PBR consumers owned by the ordinary world entity capture can opt in. */
public final class RigidModelCapture {
    public static final boolean ENABLED = Boolean.getBoolean("radiance.rigidModels");
    private static final int CAPACITY = 512;
    private static final long BYTE_CAPACITY = 32L * 1024 * 1024;
    private static final List<Entry> CACHE = new ArrayList<>();
    private static long nextId, frame;
    private static Object level;
    private static volatile long reloadEpoch;
    private static long seenReload;
    public static void invalidateResources() { ++reloadEpoch; }
    private RigidModelCapture() {}

    static long nextResourceId() {
        if (nextId >= Integer.MAX_VALUE) throw new IllegalStateException("Rigid model identity exhausted");
        return ++nextId;
    }

    public static void beginFrame(Object currentLevel) {
        if (!ENABLED) return;
        if (level != currentLevel || seenReload != reloadEpoch) { invalidate(); level = currentLevel; seenReload=reloadEpoch; }
        ++frame;
        PartModelCapture.beginFrame(currentLevel, seenReload);
        CACHE.removeIf(entry -> {
            if (frame - entry.lastUsed <= 120) return false;
            entry.close(); return true;
        });
    }
    public static void invalidate() {
        for (Entry entry : CACHE) entry.close();
        CACHE.clear(); level = null;
        PartModelCapture.invalidate();
    }

    public static void render(Object model, PoseStack.Pose pose, VertexConsumer original,
                              Consumer<VertexConsumer> render) {
        // This concrete vanilla data-model supplies quads without arbitrary model callbacks.
        // Custom/overridden model implementations keep their original consumer and execution.
        if (!ENABLED || model.getClass() != net.minecraft.client.resources.model.SimpleBakedModel.class
            || original.getClass() != PBRVertexConsumer.class || RasterPreviewScope.active()) {
            render.accept(original); return;
        }
        PBRVertexConsumer pbr = (PBRVertexConsumer) original;
        if (pbr.rigidOwner == null || !pbr.rigidEligible() || !uniformPose(pose)) {
            render.accept(original); return;
        }
        Recorder recorder = new Recorder(pbr, pose);
        render.accept(recorder);
        if (recorder.fallback || recorder.quads.isEmpty()) return;
        TextureTasks.Owner texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) { recorder.flush(); return; }
        Entry found = null;
        for (Entry entry : CACHE) {
            if (entry.model == model && entry.layer == pbr.rigidLayer && entry.texture.equals(texture)
                && same(entry.quads, recorder.quads)) { found = entry; break; }
        }
        if (found == null) {
            long required=(long)recorder.quads.size()*4*128;
            if (required>BYTE_CAPACITY) { recorder.flush(); return; }
            long bytes=CACHE.stream().mapToLong(e -> (long)e.quads.size()*4*128).sum();
            while (CACHE.size() >= CAPACITY || bytes+required>BYTE_CAPACITY) {
                Entry oldest = CACHE.stream().filter(e -> e.lastUsed != frame)
                    .min(Comparator.comparingLong(e -> e.lastUsed)).orElse(null);
                if (oldest == null) { recorder.flush(); return; }
                CACHE.remove(oldest); oldest.close();
                bytes-=(long)oldest.quads.size()*4*128;
            }
            found = new Entry(nextResourceId(), model, pbr.rigidLayer, texture, recorder.quads);
            CACHE.add(found);
        }
        found.lastUsed = frame;
        if (pbr.rigidOwner.rigidOrdinal>=1024) { recorder.flush(); return; }
        pbr.rigidOwner.rigidDraws.add(new Draw(found, new Matrix4f(recorder.pose.pose()),
            pbr.rigidOwner.rigidInstance, pbr.rigidOwner.rigidOrdinal++));
    }

    // MC's normal matrix and the shader's inverse transpose are equivalent for these poses,
    // including reflections. Reject skew/nonuniform scale rather than changing normals.
    static boolean uniformPose(PoseStack.Pose pose) {
        Matrix4f m = pose.pose();
        if (!m.isFinite() || !m.isAffine()) return false;
        float a = m.m00()*m.m00()+m.m01()*m.m01()+m.m02()*m.m02();
        float b = m.m10()*m.m10()+m.m11()*m.m11()+m.m12()*m.m12();
        float c = m.m20()*m.m20()+m.m21()*m.m21()+m.m22()*m.m22();
        float tolerance = a * 2e-5f;
        if (a < 1e-10f || Math.abs(a-b)>tolerance || Math.abs(a-c)>tolerance) return false;
        if (Math.abs(m.m00()*m.m10()+m.m01()*m.m11()+m.m02()*m.m12())>tolerance
            || Math.abs(m.m00()*m.m20()+m.m01()*m.m21()+m.m02()*m.m22())>tolerance
            || Math.abs(m.m10()*m.m20()+m.m11()*m.m21()+m.m12()*m.m22())>tolerance) return false;
        var expected = new org.joml.Matrix3f(m).scale((float)(1.0/Math.sqrt(a)));
        return expected.equals(pose.normal(), 3e-5f);
    }

    static boolean same(List<Quad> a, List<Quad> b) {
        if (a.size()!=b.size()) return false;
        for (int i=0;i<a.size();i++) if (!a.get(i).same(b.get(i))) return false;
        return true;
    }

    // A removed draw shifts ordinals. Include the full rigid draw count so the remaining
    // identical models cannot inherit another part's transform/history after that change.
    static long historyIdentity(long owner, int count, int ordinal) {
        if (owner < 0 || owner > 0xffff_ffffL || count < 1 || count > 1024 || ordinal < 0 || ordinal >= count)
            throw new IllegalArgumentException("Invalid rigid draw history identity");
        return Long.MIN_VALUE | (owner << 21) | ((long) count << 10) | ordinal;
    }

    static final class Quad {
        final BakedQuad quad;
        final int[] vertices, lights;
        final float[] brightness, color;
        final int overlay;
        final boolean existing;
        Quad(BakedQuad quad,float[] brightness,float r,float g,float b,float alpha,int[] lights,int overlay,boolean existing,boolean copy) {
            this.vertices=quad.getVertices().clone();
            this.quad=new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());
            this.brightness=brightness.clone(); this.color=new float[]{r,g,b,alpha};
            this.lights=lights.clone(); this.overlay=overlay; this.existing=existing;
        }
        Quad snapshot() { return this; }
        boolean same(Quad other) {
            return quad.getDirection()==other.quad.getDirection() && overlay==other.overlay && existing==other.existing
                && Arrays.equals(vertices,other.vertices) && Arrays.equals(brightness,other.brightness)
                && Arrays.equals(color,other.color) && Arrays.equals(lights,other.lights);
        }
        void emit(VertexConsumer target,PoseStack.Pose pose) {
            target.putBulkData(pose,quad,brightness,color[0],color[1],color[2],color[3],lights,overlay,existing);
        }
    }

    static final class Recorder implements VertexConsumer {
        final VertexConsumer target;
        final PoseStack.Pose pose;
        final List<Quad> quads=new ArrayList<>();
        boolean fallback;
        Recorder(VertexConsumer target,PoseStack.Pose pose) { this.target=target; this.pose=pose.copy(); }
        void flush() {
            if (fallback) return;
            fallback=true;
            for (Quad quad:quads) quad.emit(target,pose);
            quads.clear();
        }
        @Override public void putBulkData(PoseStack.Pose p,BakedQuad q,float[] brightness,float r,float g,float b,float a,int[] lights,int overlay,boolean existing) {
            if (!fallback && (q.getClass()!=BakedQuad.class || !pose.pose().equals(p.pose()) || !pose.normal().equals(p.normal())
                || q.getVertices().length!=32 || brightness.length!=4 || lights.length!=4)) flush();
            if (fallback) target.putBulkData(p,q,brightness,r,g,b,a,lights,overlay,existing);
            else quads.add(new Quad(q,brightness,r,g,b,a,lights,overlay,existing,false));
        }
        @Override public VertexConsumer addVertex(float x,float y,float z) { flush(); target.addVertex(x,y,z); return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { flush(); target.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setUv(float u,float v) { flush(); target.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { flush(); target.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u,int v) { flush(); target.setUv2(u,v); return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { flush(); target.setNormal(x,y,z); return this; }
    }

    interface Geometry {
        long id();
        RenderType layer();
        TextureTasks.Owner texture();
        MeshData mesh();
    }

    private static final class Entry implements AutoCloseable, Geometry {
        final long id;
        final Object model;
        final RenderType layer;
        final TextureTasks.Owner texture;
        final List<Quad> quads;
        final ByteBufferBuilder allocator;
        final MeshData mesh;
        long lastUsed;
        Entry(long id,Object model,RenderType layer,TextureTasks.Owner texture,List<Quad> quads) {
            this.id=id; this.model=model; this.layer=layer; this.texture=texture;
            this.quads=quads.stream().map(Quad::snapshot).toList();
            allocator=new ByteBufferBuilder(quads.size()*4*128);
            try {
                PBRVertexConsumer consumer=new PBRVertexConsumer(allocator,layer);
                PoseStack identity=new PoseStack();
                for (Quad quad:quads) quad.emit(consumer,identity.last());
                mesh=consumer.endNullable();
            } catch (Throwable failure) { allocator.close(); throw failure; }
        }
        @Override public void close() { if (mesh!=null) mesh.close(); allocator.close(); }
        public long id() { return id; }
        public RenderType layer() { return layer; }
        public TextureTasks.Owner texture() { return texture; }
        public MeshData mesh() { return mesh; }
    }

    public static final class Draw {
        private final Geometry entry;
        private final Matrix4f transform;
        private final long owner;
        private final int ordinal;
        private double x,y,z;
        private int mask;
        private int drawCount;
        private boolean reflect;
        private PartModelCapture.MaterialBatch materialBatch;
        private long explicitHistory;
        Draw(Geometry entry,Matrix4f transform,long owner,int ordinal) {
            this.entry=entry; this.transform=transform; this.owner=owner; this.ordinal=ordinal;
        }
        Draw(Geometry entry, Matrix4f transform, long history, PartModelCapture.MaterialBatch batch) {
            this(entry, transform, 0, 0);
            explicitHistory = history; materialBatch = batch;
        }
        public void locate(double x,double y,double z,int mask,boolean reflect,int drawCount) {
            this.x=x; this.y=y; this.z=z; this.mask=mask; this.reflect=reflect;
            this.drawCount=drawCount;
        }
        public void submit() {
            int faces;
            if (materialBatch != null && materialBatch.captured) faces = materialBatch.faces;
            else {
                faces = MaterialFaces.capture(entry.layer());
                if (materialBatch != null) { materialBatch.faces = faces; materialBatch.captured = true; }
            }
            int type=MaterialFaces.encode(Constants.GeometryTypes.getGeometryType(entry.layer(),reflect).getValue(), faces);
            try (MemoryStack stack=MemoryStack.stackPush()) {
                var matrix=stack.mallocFloat(16); transform.get(matrix);
                var group=stack.UTF8(PBRVertexConsumer.normalizeTextLayerName(entry.layer().name));
                long key=(entry.id()<<32)|Integer.toUnsignedLong(type);
                long history=explicitHistory != 0 ? explicitHistory : historyIdentity(owner,drawCount,ordinal);
                if (!TextureProxy.TASKS.use(entry.texture(), () -> EntityProxy.queueRigidModel(key,history,
                    type,entry.texture().id(),MemoryUtil.memAddress(entry.mesh().vertexBuffer()),entry.mesh().drawState().vertexCount(),
                    x,y,z,mask,MemoryUtil.memAddress(matrix),MemoryUtil.memAddress(group))))
                    throw new IllegalStateException("Rigid model texture was retired before submission");
            }
        }
    }
}
