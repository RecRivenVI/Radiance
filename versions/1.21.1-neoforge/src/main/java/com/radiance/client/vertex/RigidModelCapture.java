package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.texture.TextureTasks;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Bounded rigid baked-model capture. Renderer/model/tint callbacks still run every frame. Only
 * exact PBR consumers owned by the ordinary world entity capture can opt in.
 */
public final class RigidModelCapture {
    public static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("radiance.rigidModels", "true"));
    private static final int CAPACITY = 512;
    private static final long BYTE_CAPACITY = 32L * 1024 * 1024;
    private static final List<Entry> CACHE = new ArrayList<>();
    private static final IdentityHashMap<Object, Entry> RECENT = new IdentityHashMap<>();
    private static long nextId, frame;
    private static Object level;
    private static volatile long reloadEpoch;
    private static long seenReload;

    public static void invalidateResources() {
        ++reloadEpoch;
    }

    private RigidModelCapture() {}

    static long nextResourceId() {
        if (nextId >= Integer.MAX_VALUE)
            throw new IllegalStateException("Rigid model identity exhausted");
        return ++nextId;
    }

    public static void beginFrame(Object currentLevel) {
        if (!ENABLED) return;
        if (level != currentLevel || seenReload != reloadEpoch) {
            invalidate();
            level = currentLevel;
            seenReload = reloadEpoch;
        }
        ++frame;
        PartModelCapture.beginFrame(currentLevel, seenReload);
        PaintingModelCapture.beginFrame(frame);
        BakedModelTakeover.beginFrame(frame);
        ParticleQuadTakeover.beginFrame(frame);
        LocalMeshTakeover.beginFrame(frame);
        CACHE.removeIf(
                entry -> {
                    if (frame - entry.lastUsed <= 120) return false;
                    RECENT.remove(entry.model, entry);
                    entry.close();
                    return true;
                });
    }

    public static void invalidate() {
        for (Entry entry : CACHE) entry.close();
        CACHE.clear();
        RECENT.clear();
        level = null;
        PartModelCapture.invalidate();
        PaintingModelCapture.invalidate();
        BakedModelTakeover.invalidate();
        ParticleQuadTakeover.invalidate();
        LocalMeshTakeover.invalidate();
        com.radiance.client.render.StaticDrawStates.invalidatePrograms();
    }

    public static void render(
            Object model,
            PoseStack.Pose pose,
            VertexConsumer original,
            Consumer<VertexConsumer> render) {
        // This concrete vanilla data-model supplies quads without arbitrary model callbacks.
        // Custom/overridden model implementations keep their original consumer and execution.
        if (!ENABLED
                || model.getClass() != net.minecraft.client.resources.model.SimpleBakedModel.class
                || original.getClass() != PBRVertexConsumer.class
                || RasterPreviewScope.active()) {
            render.accept(original);
            return;
        }
        PBRVertexConsumer pbr = (PBRVertexConsumer) original;
        if (pbr.rigidOwner == null
                || !pbr.rigidOwner.rigidBakedEnabled()
                || !pbr.rigidEligible()
                || !uniformPose(pose)) {
            render.accept(original);
            return;
        }
        Entry recent = RECENT.get(model);
        if (recent != null && recent.layer != pbr.rigidLayer) recent = null;
        Recorder recorder = new Recorder(pbr, pose, recent == null ? null : recent.quads);
        render.accept(recorder);
        if (recorder.fallback) return;
        List<Quad> recipe = recorder.recipe();
        if (recipe.isEmpty()) return;
        TextureTasks.Owner texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) {
            recorder.flush();
            return;
        }
        // Nested render callbacks may evict the candidate. Its Java recipe remains valid,
        // but only a still-resident entry may supply a native vertex pointer.
        Entry found =
                recent != null
                                && RECENT.get(model) == recent
                                && recipe == recent.quads
                                && recent.texture.equals(texture)
                        ? recent
                        : null;
        for (int i = 0; found == null && i < CACHE.size(); i++) {
            Entry entry = CACHE.get(i);
            if (entry.model == model
                    && entry.layer == pbr.rigidLayer
                    && entry.texture.equals(texture)
                    && same(entry.quads, recipe)) {
                found = entry;
                break;
            }
        }
        if (found == null) {
            long required = (long) recipe.size() * 4 * 128;
            if (required > BYTE_CAPACITY) {
                recorder.flush();
                return;
            }
            long bytes = CACHE.stream().mapToLong(e -> (long) e.quads.size() * 4 * 128).sum();
            while (CACHE.size() >= CAPACITY || bytes + required > BYTE_CAPACITY) {
                Entry oldest =
                        CACHE.stream()
                                .filter(e -> e.lastUsed != frame)
                                .min(Comparator.comparingLong(e -> e.lastUsed))
                                .orElse(null);
                if (oldest == null) {
                    recorder.flush();
                    return;
                }
                CACHE.remove(oldest);
                RECENT.remove(oldest.model, oldest);
                oldest.close();
                bytes -= (long) oldest.quads.size() * 4 * 128;
            }
            found = new Entry(nextResourceId(), model, pbr.rigidLayer, texture, recipe);
            CACHE.add(found);
        }
        found.lastUsed = frame;
        RECENT.put(model, found);
        if (pbr.rigidOwner.rigidOrdinal >= 1024) {
            recorder.flush();
            return;
        }
        pbr.rigidOwner.rigidDraws.add(
                new Draw(
                                found,
                                new Matrix4f(recorder.pose.pose()),
                                pbr.rigidOwner.rigidInstance,
                                pbr.rigidOwner.rigidOrdinal++)
                        .capturedBy(pbr));
    }

    // MC's normal matrix and the shader's inverse transpose are equivalent for these poses,
    // including reflections. Reject skew/nonuniform scale rather than changing normals.
    static boolean uniformPose(PoseStack.Pose pose) {
        Matrix4f m = pose.pose();
        if (!m.isFinite() || !m.isAffine()) return false;
        float a = m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02();
        float b = m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12();
        float c = m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22();
        float tolerance = a * 2e-5f;
        if (a < 1e-10f || Math.abs(a - b) > tolerance || Math.abs(a - c) > tolerance) return false;
        if (Math.abs(m.m00() * m.m10() + m.m01() * m.m11() + m.m02() * m.m12()) > tolerance
                || Math.abs(m.m00() * m.m20() + m.m01() * m.m21() + m.m02() * m.m22()) > tolerance
                || Math.abs(m.m10() * m.m20() + m.m11() * m.m21() + m.m12() * m.m22()) > tolerance)
            return false;
        var expected = new org.joml.Matrix3f(m).scale((float) (1.0 / Math.sqrt(a)));
        return expected.equals(pose.normal(), 3e-5f);
    }

    static boolean same(List<Quad> a, List<Quad> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).same(b.get(i))) return false;
        return true;
    }

    // A removed draw shifts ordinals. Include the full rigid draw count so the remaining
    // identical models cannot inherit another part's transform/history after that change.
    static long historyIdentity(long owner, int count, int ordinal) {
        if (owner < 0
                || owner > 0xffff_ffffL
                || count < 1
                || count > 1024
                || ordinal < 0
                || ordinal >= count)
            throw new IllegalArgumentException("Invalid rigid draw history identity");
        return Long.MIN_VALUE | (owner << 21) | ((long) count << 10) | ordinal;
    }

    static final class Quad {
        final BakedQuad quad;
        final int[] vertices, lights;
        final float[] brightness, color;
        final int overlay;
        final boolean existing;

        Quad(
                BakedQuad quad,
                float[] brightness,
                float r,
                float g,
                float b,
                float alpha,
                int[] lights,
                int overlay,
                boolean existing,
                boolean copy) {
            this.vertices = quad.getVertices().clone();
            this.quad =
                    new BakedQuad(
                            vertices,
                            quad.getTintIndex(),
                            quad.getDirection(),
                            quad.getSprite(),
                            quad.isShade(),
                            quad.hasAmbientOcclusion());
            this.brightness = brightness.clone();
            this.color = new float[] {r, g, b, alpha};
            this.lights = lights.clone();
            this.overlay = overlay;
            this.existing = existing;
        }

        Quad snapshot() {
            return this;
        }

        boolean same(Quad other) {
            return quad.getDirection() == other.quad.getDirection()
                    && overlay == other.overlay
                    && existing == other.existing
                    && Arrays.equals(vertices, other.vertices)
                    && Arrays.equals(brightness, other.brightness)
                    && Arrays.equals(color, other.color)
                    && Arrays.equals(lights, other.lights);
        }

        boolean matches(
                BakedQuad other,
                float[] brightness,
                float r,
                float g,
                float b,
                float a,
                int[] lights,
                int overlay,
                boolean existing) {
            return quad.getDirection() == other.getDirection()
                    && this.overlay == overlay
                    && this.existing == existing
                    && Arrays.equals(vertices, other.getVertices())
                    && Arrays.equals(this.brightness, brightness)
                    && Float.floatToIntBits(color[0]) == Float.floatToIntBits(r)
                    && Float.floatToIntBits(color[1]) == Float.floatToIntBits(g)
                    && Float.floatToIntBits(color[2]) == Float.floatToIntBits(b)
                    && Float.floatToIntBits(color[3]) == Float.floatToIntBits(a)
                    && Arrays.equals(this.lights, lights);
        }

        void emit(VertexConsumer target, PoseStack.Pose pose) {
            target.putBulkData(
                    pose,
                    quad,
                    brightness,
                    color[0],
                    color[1],
                    color[2],
                    color[3],
                    lights,
                    overlay,
                    existing);
        }
    }

    static final class Recorder implements VertexConsumer {
        final VertexConsumer target;
        final PoseStack.Pose pose;
        final List<Quad> quads = new ArrayList<>();
        private List<Quad> candidate;
        private int matched;
        boolean fallback;

        Recorder(VertexConsumer target, PoseStack.Pose pose) {
            this(target, pose, null);
        }

        Recorder(VertexConsumer target, PoseStack.Pose pose, List<Quad> candidate) {
            this.target = target;
            this.pose = pose.copy();
            this.candidate = candidate;
        }

        // Compare the original compact recipe as callbacks issue it. A cache hit never expands
        // transformed vertices or clones a recipe; a divergence owns the prefix before continuing.
        private void materialize() {
            if (candidate == null) return;
            quads.addAll(candidate.subList(0, matched));
            candidate = null;
        }

        List<Quad> recipe() {
            if (candidate != null && matched == candidate.size()) return candidate;
            materialize();
            return quads;
        }

        void flush() {
            if (fallback) return;
            fallback = true;
            materialize();
            for (Quad quad : quads) quad.emit(target, pose);
            quads.clear();
        }

        @Override
        public void putBulkData(
                PoseStack.Pose p,
                BakedQuad q,
                float[] brightness,
                float r,
                float g,
                float b,
                float a,
                int[] lights,
                int overlay,
                boolean existing) {
            if (!fallback
                    && (q.getClass() != BakedQuad.class
                            || !pose.pose().equals(p.pose())
                            || !pose.normal().equals(p.normal())
                            || q.getVertices().length != 32
                            || brightness.length != 4
                            || lights.length != 4)) flush();
            if (fallback)
                target.putBulkData(p, q, brightness, r, g, b, a, lights, overlay, existing);
            else if (candidate != null
                    && matched < candidate.size()
                    && candidate
                            .get(matched)
                            .matches(q, brightness, r, g, b, a, lights, overlay, existing)) {
                ++matched;
            } else {
                materialize();
                quads.add(new Quad(q, brightness, r, g, b, a, lights, overlay, existing, false));
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            flush();
            target.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            flush();
            target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            flush();
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            flush();
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            flush();
            target.setNormal(x, y, z);
            return this;
        }
    }

    interface Geometry {
        long id();

        RenderType layer();

        TextureTasks.Owner texture();

        MeshData mesh();

        default List<Binding> bindings() {
            return List.of();
        }

        default List<TextureTasks.Owner> textures() {
            return List.of(texture());
        }
    }

    record Binding(
            Geometry geometry, int type, com.radiance.render.contract.DrawStateSnapshot state) {}

    static void collectTextures(
            Geometry geometry, List<Binding> bindings, Set<TextureTasks.Owner> owners) {
        if (bindings.isEmpty()) owners.addAll(geometry.textures());
        else for (var binding : bindings) owners.addAll(binding.geometry.textures());
    }

    static final class Entry implements AutoCloseable, Geometry {
        final long id;
        final Object model;
        final RenderType layer;
        final TextureTasks.Owner texture;
        final TextureTasks.Owner glintTexture;
        final List<Quad> quads;
        final ByteBufferBuilder allocator;
        final MeshData mesh;
        long lastUsed;

        Entry(
                long id,
                Object model,
                RenderType layer,
                TextureTasks.Owner texture,
                List<Quad> quads) {
            this(id, model, layer, texture, quads, null, null);
        }

        Entry(
                long id,
                Object model,
                RenderType layer,
                TextureTasks.Owner texture,
                List<Quad> quads,
                RenderType glint,
                TextureTasks.Owner glintTexture) {
            this.id = id;
            this.model = model;
            this.layer = layer;
            this.texture = texture;
            this.glintTexture = glintTexture;
            this.quads = quads.stream().map(Quad::snapshot).toList();
            allocator = new ByteBufferBuilder(quads.size() * 4 * 128);
            try {
                PBRVertexConsumer consumer = new PBRVertexConsumer(allocator, layer);
                VertexConsumer target =
                        glint == null ? consumer : new PBRVertexConsumer.GLint(consumer, glint);
                PoseStack identity = new PoseStack();
                for (Quad quad : quads) quad.emit(target, identity.last());
                mesh = consumer.endNullable();
            } catch (Throwable failure) {
                allocator.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            if (mesh != null) mesh.close();
            allocator.close();
        }

        public long id() {
            return id;
        }

        public RenderType layer() {
            return layer;
        }

        public TextureTasks.Owner texture() {
            return texture;
        }

        public List<TextureTasks.Owner> textures() {
            return glintTexture == null ? List.of(texture) : List.of(texture, glintTexture);
        }

        public MeshData mesh() {
            return mesh;
        }
    }

    public static final class Draw {
        private final Geometry entry;
        private final Matrix4f transform;
        private final long owner;
        private final int ordinal;
        private double x, y, z;
        private int mask;
        private int drawCount;
        private boolean reflect;
        private long explicitHistory;
        private VertexConsumer captureConsumer;
        private com.radiance.render.contract.DrawStateSnapshot drawState;
        private float[] particleColour;
        private double originX, originY, originZ;

        Draw particleColour(float r, float g, float b, float a) {
            particleColour = new float[] {r, g, b, a};
            return this;
        }

        Draw origin(double x, double y, double z) {
            originX = x;
            originY = y;
            originZ = z;
            return this;
        }

        private final com.radiance.render.contract.TakeoverSubmission.Resource resource;

        Draw capturedBy(VertexConsumer consumer) {
            captureConsumer = consumer;
            return this;
        }

        Draw withState(com.radiance.render.contract.DrawStateSnapshot state) {
            drawState = state;
            return this;
        }

        public void seal(StorageVertexConsumerProvider provider) {
            if (captureConsumer != null) drawState = provider.drawState(captureConsumer);
            if (particleColour != null) {
                var s = drawState;
                drawState =
                        new com.radiance.render.contract.DrawStateSnapshot(
                                s.faces(),
                                s.lineWidth(),
                                s.red() * particleColour[0],
                                s.green() * particleColour[1],
                                s.blue() * particleColour[2],
                                s.alpha() * particleColour[3],
                                s.uvTransform());
            }
            captureConsumer = null;
        }

        public RenderType layer() {
            return entry.layer();
        }

        public boolean acceptsGroup(String group) {
            return PBRVertexConsumer.normalizeTextLayerName(entry.layer().name).equals(group);
        }

        Draw(Geometry entry, Matrix4f transform, long owner, int ordinal) {
            this.entry = entry;
            this.transform = transform;
            this.owner = owner;
            this.ordinal = ordinal;
            resource =
                    new com.radiance.render.contract.TakeoverSubmission.Resource(
                            reloadEpoch, entry.id(), 1, 1, 1);
        }

        Draw(Geometry entry, Matrix4f transform, long history) {
            this(entry, transform, 0, 0);
            explicitHistory = history;
        }

        public void locate(double x, double y, double z, int mask, boolean reflect, int drawCount) {
            this.x = x + originX;
            this.y = y + originY;
            this.z = z + originZ;
            this.mask = mask;
            this.reflect = reflect;
            this.drawCount = drawCount;
        }

        public void submit(Batch batch) {
            var snapshot =
                    com.radiance.compatibility.vanilla.VanillaMaterialAdapter.sourceColour(
                            entry.layer(),
                            drawState == null
                                    ? MaterialFaces.captureSnapshot(entry.layer())
                                    : drawState);
            // The direct request owns this immutable resource and its actual state. Native
            // copies the mesh on first use and retains GPU versions independently of Java.
            var request =
                    new com.radiance.render.contract.TakeoverSubmission.Draw(
                            new com.radiance.render.contract.TakeoverSubmission.Instance(
                                    owner, ordinal, resource),
                            com.radiance.render.contract.TakeoverSubmission.Domain.WORLD,
                            com.radiance.render.contract.TakeoverSubmission.Executor.DIRECT,
                            com.radiance.client.render.StaticDrawStates.program(entry.layer()),
                            snapshot,
                            java.util.List.of());
            snapshot = request.state();
            int faces = snapshot.faces();
            int type =
                    MaterialFaces.encode(
                            Constants.GeometryTypes.getGeometryType(entry.layer(), reflect)
                                    .getValue(),
                            faces);
            type =
                    com.radiance.render.contract.SourceEmission.encode(
                            type,
                            com.radiance.compatibility.vanilla.VanillaEmissionAdapter.policy(
                                            entry.layer(),
                                            com.radiance.render.contract.SourceEmission.Role
                                                    .ORDINARY,
                                            false)
                                    .bits());
            long key =
                    (request.instance().resource().identity() << 32) | Integer.toUnsignedLong(type);
            var bindings = entry.bindings();
            collectTextures(entry, bindings, batch.textures);
            if (!bindings.isEmpty()) {
                batch.bindings.putIfAbsent(key, bindings);
                // Each declared component owns its source colour; do not multiply the base
                // draw colour into the complete coating stack for a second time.
                snapshot = com.radiance.render.contract.DrawStateSnapshot.IDENTITY;
            }
            long history =
                    explicitHistory != 0
                            ? explicitHistory
                            : historyIdentity(
                                    request.instance().owner(),
                                    drawCount,
                                    request.instance().part());
            batch.add(
                    key,
                    history,
                    type,
                    entry.texture(),
                    MemoryUtil.memAddress(entry.mesh().vertexBuffer()),
                    entry.mesh().drawState().vertexCount(),
                    x,
                    y,
                    z,
                    mask,
                    transform,
                    PBRVertexConsumer.normalizeTextLayerName(entry.layer().name),
                    snapshot);
        }
    }

    /** Submits every persistent draw of one entity submission with a single native call. */
    public static void submitAll(List<Draw> draws) {
        if (draws.isEmpty()) return;
        try (var request =
                        com.radiance.api.audit.DrawingAudit.begin(
                                "PERSISTENT", "RigidModelCapture", "DIRECT", "WORLD");
                Batch batch = new Batch(draws.size())) {
            for (Draw draw : draws) draw.submit(batch);
            batch.submit();
        }
    }

    /**
     * Native-order records matching {@code mcvr::rigid::SubmissionRecord} (152 bytes), plus a
     * per-batch group-name table. Texture owners are validated together immediately before use.
     */
    public static final class Batch implements AutoCloseable {
        static final int RECORD_BYTES = com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES;
        private final ByteBuffer records;
        private final Map<String, Integer> groupIndices = new HashMap<>();
        final List<String> groups = new ArrayList<>();
        private final Set<TextureTasks.Owner> textures = new LinkedHashSet<>();
        private final Map<Long, List<Binding>> bindings = new LinkedHashMap<>();
        private final int capacity;
        int count;

        Batch(int capacity) {
            this.capacity = capacity;
            records = MemoryUtil.memCalloc(capacity * RECORD_BYTES);
        }

        void add(
                long model,
                long history,
                int type,
                TextureTasks.Owner texture,
                long vertices,
                int vertexCount,
                double x,
                double y,
                double z,
                int mask,
                Matrix4f transform,
                String group) {
            add(
                    model,
                    history,
                    type,
                    texture,
                    vertices,
                    vertexCount,
                    x,
                    y,
                    z,
                    mask,
                    transform,
                    group,
                    com.radiance.render.contract.DrawStateSnapshot.IDENTITY);
        }

        void add(
                long model,
                long history,
                int type,
                TextureTasks.Owner texture,
                long vertices,
                int vertexCount,
                double x,
                double y,
                double z,
                int mask,
                Matrix4f transform,
                String group,
                com.radiance.render.contract.DrawStateSnapshot snapshot) {
            if (count == capacity)
                throw new IllegalStateException("Rigid model batch capacity exceeded");
            int base = count * RECORD_BYTES;
            records.putLong(base, model)
                    .putLong(base + 8, history)
                    .putLong(base + 16, vertices)
                    .putDouble(base + 24, x)
                    .putDouble(base + 32, y)
                    .putDouble(base + 40, z);
            transform.get(base + 48, records);
            records.putInt(base + 112, type)
                    .putInt(base + 116, texture.id())
                    .putInt(base + 120, vertexCount)
                    .putInt(base + 124, mask)
                    .putInt(
                            base + 128,
                            groupIndices.computeIfAbsent(
                                    group,
                                    name -> {
                                        groups.add(name);
                                        return groups.size() - 1;
                                    }))
                    .putInt(base + 132, 0);
            snapshot.writeColour(
                    records, base + com.radiance.render.contract.RenderAbi.RIGID_COLOUR_OFFSET);
            textures.add(texture);
            count++;
        }

        ByteBuffer records() {
            return records;
        }

        void submit() {
            int bindingCount = bindings.values().stream().mapToInt(List::size).sum();
            int bindingStride = com.radiance.render.contract.RenderAbi.RIGID_BINDING_BYTES;
            ByteBuffer bindingData =
                    MemoryUtil.memAlloc(Math.multiplyExact(bindingCount, bindingStride));
            try {
                int at = 0;
                for (var resource : bindings.entrySet()) {
                    int ordinal = 0;
                    for (var binding : resource.getValue()) {
                        var geometry = binding.geometry();
                        String group =
                                PBRVertexConsumer.normalizeTextLayerName(geometry.layer().name);
                        int groupIndex =
                                groupIndices.computeIfAbsent(
                                        group,
                                        name -> {
                                            groups.add(name);
                                            return groups.size() - 1;
                                        });
                        bindingData
                                .putLong(at, resource.getKey())
                                .putLong(
                                        at + 8,
                                        MemoryUtil.memAddress(geometry.mesh().vertexBuffer()))
                                .putInt(at + 16, geometry.mesh().drawState().vertexCount())
                                .putInt(at + 20, binding.type())
                                .putInt(at + 24, geometry.texture().id())
                                .putInt(at + 28, groupIndex);
                        binding.state().writeColour(bindingData, at + 32);
                        bindingData
                                .putInt(at + 48, ordinal++)
                                .putInt(at + 52, resource.getValue().size());
                        at += bindingStride;
                    }
                }
                PointerBuffer names = MemoryUtil.memAllocPointer(groups.size());
                List<ByteBuffer> encoded = new ArrayList<>(groups.size());
                try {
                    for (String group : groups) {
                        ByteBuffer utf8 = MemoryUtil.memUTF8(group);
                        encoded.add(utf8);
                        names.put(MemoryUtil.memAddress(utf8));
                    }
                    names.flip();
                    if (!TextureProxy.TASKS.useAll(
                            textures,
                            () -> {
                                if (bindingCount == 0)
                                    EntityProxy.queueRigidModelsV2(
                                            com.radiance.render.contract.RenderAbi
                                                    .RIGID_INPUT_VERSION,
                                            RECORD_BYTES,
                                            records.capacity(),
                                            MemoryUtil.memAddress(records),
                                            count,
                                            MemoryUtil.memAddress(names),
                                            groups.size());
                                else
                                    EntityProxy.queueRigidModelsV3(
                                            com.radiance.render.contract.RenderAbi
                                                    .RIGID_INPUT_VERSION,
                                            RECORD_BYTES,
                                            records.capacity(),
                                            MemoryUtil.memAddress(records),
                                            count,
                                            MemoryUtil.memAddress(names),
                                            groups.size(),
                                            com.radiance.render.contract.RenderAbi
                                                    .RIGID_BINDING_VERSION,
                                            bindingData.capacity(),
                                            MemoryUtil.memAddress(bindingData),
                                            bindingCount);
                            }))
                        throw new IllegalStateException(
                                "Rigid model texture was retired before submission");
                    com.radiance.api.audit.DrawingAudit.consumed("native.rigid.v2");
                } finally {
                    encoded.forEach(MemoryUtil::memFree);
                    MemoryUtil.memFree(names);
                }
            } finally {
                MemoryUtil.memFree(bindingData);
            }
        }

        @Override
        public void close() {
            MemoryUtil.memFree(records);
        }
    }
}
