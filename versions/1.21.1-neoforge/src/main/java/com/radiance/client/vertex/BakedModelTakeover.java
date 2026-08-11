package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.render.contract.TakeoverSubmission;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Source adapter for the fixed quad lists owned by a vanilla baked-model resource epoch. */
public final class BakedModelTakeover {
    public interface OverrideSource {
        BakedModel radiance$overrideModel();
    }

    private static final IdentityHashMap<BakedModel, Source> SOURCES = new IdentityHashMap<>();
    private static volatile Set<BakedModel> publishedModels =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean resourcesChanged;
    private static final Map<BakedModel, Boolean> DECLARED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<Version> VERSIONS = new ArrayList<>();
    private static final int CAPACITY = 512;
    private static final long BYTE_CAPACITY = 32L * 1024 * 1024;
    private static long frame, bytes, sourceBytes, registrations, builds, draws, declines;

    private BakedModelTakeover() {}

    static void beginFrame(long value) {
        if (resourcesChanged) {
            invalidate();
            resourcesChanged = false;
        }
        frame = value;
        VERSIONS.removeIf(
                v -> {
                    if (frame - v.used <= 120) return false;
                    bytes -= v.bytes;
                    v.geometry.close();
                    return true;
                });
        SOURCES.values()
                .removeIf(
                        s -> {
                            if (frame - s.used <= 120) return false;
                            sourceBytes -= s.bytes;
                            return true;
                        });
    }

    static void invalidate() {
        VERSIONS.forEach(v -> v.geometry.close());
        VERSIONS.clear();
        SOURCES.clear();
        bytes = 0;
        sourceBytes = 0;
    }

    public static boolean sourceCompatible() {
        return com.radiance.mixin_related.SourceCompatibility.compatible(
                com.radiance.mixin_related.SourceCompatibility.Source.BAKED);
    }

    public static long[] counters() {
        return new long[] {registrations, builds, draws, declines, bytes, sourceBytes};
    }

    /** ModelManager calls this after the actual NeoForge bake-result callbacks have completed. */
    public static void publishModels(Collection<BakedModel> models) {
        var next = Collections.newSetFromMap(new IdentityHashMap<BakedModel, Boolean>());
        var pending = new ArrayDeque<>(models);
        while (!pending.isEmpty()) {
            var model = pending.removeFirst();
            if (!DECLARED.containsKey(model) || !next.add(model)) continue;
            for (var override : model.getOverrides().getOverrides()) {
                var child = ((OverrideSource) override).radiance$overrideModel();
                if (child != null) pending.addLast(child);
            }
        }
        publishedModels = next;
        // Pending draws may still own Java vertex pointers. Retire local storage only at
        // the next frame boundary, after their synchronous JNI copy has finished.
        resourcesChanged = true;
    }

    /** Producer contract: the standard JSON baker owns this model until the next publication. */
    public static void declareBakedResource(BakedModel model) {
        if (model != null && model.getClass() == SimpleBakedModel.class)
            DECLARED.put(model, Boolean.TRUE);
    }

    static boolean owns(BakedModel model) {
        return publishedModels.contains(model);
    }

    public static boolean declared(BakedModel model) {
        return owns(model) && sourceCompatible();
    }

    public static void request(
            BakedModel model,
            ItemStack item,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer) {
        if (submit(
                model,
                pose,
                consumer,
                light,
                overlay,
                true,
                quad ->
                        !item.isEmpty() && quad.isTinted()
                                ? Minecraft.getInstance()
                                        .getItemColors()
                                        .getColor(item, quad.getTintIndex())
                                : -1,
                1,
                1,
                1,
                true)) return;
        // Unsupported affine/glint consumers still use the immutable published quad definition;
        // no renderer or model-output callback is invoked by this execution path.
        Source source = SOURCES.get(model);
        if (source == null) {
            source = new Source(model, BYTE_CAPACITY);
            if (SOURCES.size() < CAPACITY && sourceBytes + source.bytes <= BYTE_CAPACITY) {
                SOURCES.put(model, source);
                sourceBytes += source.bytes;
                ++registrations;
            }
        }
        source.used = frame;
        var colors = new ArrayList<Integer>(source.quads.size());
        for (var quad : source.quads)
            colors.add(
                    !item.isEmpty() && quad.isTinted()
                            ? Minecraft.getInstance()
                                    .getItemColors()
                                    .getColor(item, quad.getTintIndex())
                            : -1);
        emit(
                source,
                new Appearance(light, overlay, true, 1, 1, 1, List.copyOf(colors)),
                consumer,
                pose);
    }

    public static boolean item(
            BakedModel model,
            ItemStack item,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer) {
        return submit(
                model,
                pose,
                consumer,
                light,
                overlay,
                true,
                quad ->
                        !item.isEmpty() && quad.isTinted()
                                ? Minecraft.getInstance()
                                        .getItemColors()
                                        .getColor(item, quad.getTintIndex())
                                : -1,
                1,
                1,
                1,
                false);
    }

    public static boolean block(
            BakedModel model,
            float red,
            float green,
            float blue,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer) {
        return submit(
                model,
                pose,
                consumer,
                light,
                overlay,
                false,
                quad -> -1,
                Mth.clamp(red, 0, 1),
                Mth.clamp(green, 0, 1),
                Mth.clamp(blue, 0, 1),
                false);
    }

    public static void requestBlock(
            BakedModel model,
            float red,
            float green,
            float blue,
            int light,
            int overlay,
            PoseStack.Pose pose,
            VertexConsumer consumer) {
        red = Mth.clamp(red, 0, 1);
        green = Mth.clamp(green, 0, 1);
        blue = Mth.clamp(blue, 0, 1);
        if (submit(
                model, pose, consumer, light, overlay, false, quad -> -1, red, green, blue, true))
            return;
        Source source = SOURCES.get(model);
        if (source == null) {
            source = new Source(model, BYTE_CAPACITY);
            if (SOURCES.size() < CAPACITY && sourceBytes + source.bytes <= BYTE_CAPACITY) {
                SOURCES.put(model, source);
                sourceBytes += source.bytes;
                ++registrations;
            }
        }
        source.used = frame;
        emit(
                source,
                new Appearance(
                        light,
                        overlay,
                        false,
                        red,
                        green,
                        blue,
                        java.util.Collections.nCopies(source.quads.size(), -1)),
                consumer,
                pose);
    }

    @FunctionalInterface
    interface Tint {
        int colour(BakedQuad quad);
    }

    static final class Source {
        final List<BakedQuad> quads;
        final long bytes;
        long used;

        Source(BakedModel model, long remainingBytes) {
            var result = new ArrayList<BakedQuad>();
            var random = RandomSource.create(42);
            for (Direction direction : Direction.values()) {
                random.setSeed(42);
                append(result, model.getQuads(null, direction, random), remainingBytes);
            }
            random.setSeed(42);
            append(result, model.getQuads(null, null, random), remainingBytes);
            if (result.stream()
                    .anyMatch(q -> q.getClass() != BakedQuad.class || q.getVertices().length != 32))
                throw new UnsupportedSource();
            // A model resource is published as an immutable snapshot. This source has no
            // per-frame geometry callback; model reload publishes a new epoch instead.
            quads =
                    result.stream()
                            .map(
                                    q ->
                                            new BakedQuad(
                                                    q.getVertices().clone(),
                                                    q.getTintIndex(),
                                                    q.getDirection(),
                                                    q.getSprite(),
                                                    q.isShade(),
                                                    q.hasAmbientOcclusion()))
                            .toList();
            bytes = (long) quads.size() * 256;
        }

        private static void append(List<BakedQuad> target, List<BakedQuad> quads, long remaining) {
            long count = (long) target.size() + quads.size();
            if (count > BYTE_CAPACITY / 512 || count * 256 > remaining)
                throw new UnsupportedSource();
            target.addAll(quads);
        }
    }

    record Appearance(
            int light,
            int overlay,
            boolean existing,
            float red,
            float green,
            float blue,
            List<Integer> tints) {}

    private static final class Version {
        final Source source;
        final Appearance appearance;
        final RigidModelCapture.Entry geometry;
        final long bytes;
        final RenderType glint;
        final com.radiance.client.texture.TextureTasks.Owner glintTexture;
        final PBRMaterialContext.State material;
        long used;

        Version(
                Source source,
                Appearance appearance,
                RigidModelCapture.Entry geometry,
                RenderType glint,
                com.radiance.client.texture.TextureTasks.Owner glintTexture) {
            this.source = source;
            this.appearance = appearance;
            this.geometry = geometry;
            this.glint = glint;
            this.glintTexture = glintTexture;
            material = PBRMaterialContext.snapshot();
            bytes = (long) source.quads.size() * 4 * 128;
        }
    }

    private static boolean submit(
            BakedModel model,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean existing,
            Tint tint,
            float red,
            float green,
            float blue,
            boolean declaredRequest) {
        light = com.radiance.adapter.vanilla.VanillaMaterialAdapter.lightmap(light);
        VertexConsumer output = consumer;
        RenderType glint = null;
        com.radiance.client.texture.TextureTasks.Owner glintTexture = null;
        if (consumer instanceof PBRVertexConsumer.GLint decorated) {
            glint = decorated.sourceLayer();
            glintTexture = decorated.textureOwner();
            consumer = decorated.delegate();
        }
        if (glint != null && glintTexture == null) return false;
        if (!RigidModelCapture.ENABLED
                || !sourceCompatible()
                || !owns(model)
                || consumer.getClass() != PBRVertexConsumer.class
                || RasterPreviewScope.active()
                || !RigidModelCapture.compatiblePose(pose)) return false;
        var pbr = (PBRVertexConsumer) consumer;
        var owner = pbr.rigidOwner;
        if (owner == null
                || !(owner.takeoverItemFrame
                        || owner.declaredPartsEnabled
                        || declaredRequest && owner.rigidBakedEnabled())
                || !(declaredRequest ? pbr.declaredModelEligible() : pbr.rigidEligible())
                || owner.rigidOrdinal >= 1024) return false;
        var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) return false;
        Source source = SOURCES.get(model);
        if (source == null) {
            if (SOURCES.size() >= CAPACITY) {
                ++declines;
                return false;
            }
            try {
                source = new Source(model, BYTE_CAPACITY - sourceBytes);
            } catch (UnsupportedSource unsupported) {
                ++declines;
                return false;
            }
            SOURCES.put(model, source);
            sourceBytes += source.bytes;
            ++registrations;
        }
        source.used = frame;
        if (source.quads.isEmpty()) return true;
        List<Integer> tints = new ArrayList<>(source.quads.size());
        // Keep the original number and order of item-colour callbacks. Only appearance
        // scalars are inspected here; vertex arrays and recipe hashes are cold-path work.
        for (var quad : source.quads) tints.add(tint.colour(quad));
        var appearance =
                new Appearance(light, overlay, existing, red, green, blue, List.copyOf(tints));
        Version found = null;
        var material = PBRMaterialContext.snapshot();
        for (var version : VERSIONS) {
            if (version.source == source
                    && version.geometry.layer == pbr.rigidLayer
                    && version.geometry.texture.equals(texture)
                    && version.glint == glint
                    && java.util.Objects.equals(version.glintTexture, glintTexture)
                    && version.material.equals(material)
                    && version.appearance.equals(appearance)) {
                found = version;
                break;
            }
        }
        if (found == null) {
            long required = (long) source.quads.size() * 4 * 128;
            if (required > BYTE_CAPACITY) {
                emit(source, appearance, consumer, pose);
                ++declines;
                return true;
            }
            while (VERSIONS.size() >= CAPACITY || bytes + required > BYTE_CAPACITY) {
                var oldest =
                        VERSIONS.stream()
                                .filter(v -> v.used != frame)
                                .min(Comparator.comparingLong(v -> v.used))
                                .orElse(null);
                if (oldest == null) {
                    emit(source, appearance, output, pose);
                    ++declines;
                    return true;
                }
                VERSIONS.remove(oldest);
                bytes -= oldest.bytes;
                oldest.geometry.close();
            }
            var recipe = recipe(source.quads, appearance);
            var geometry =
                    new RigidModelCapture.Entry(
                            RigidModelCapture.nextResourceId(),
                            model,
                            pbr.rigidLayer,
                            texture,
                            recipe,
                            glint,
                            glintTexture);
            found = new Version(source, appearance, geometry, glint, glintTexture);
            VERSIONS.add(found);
            bytes += required;
            ++builds;
        }
        found.used = frame;
        try (var ownership = TakeoverSubmission.Ownership.open()) {
            ownership.claim(TakeoverSubmission.Executor.DIRECT);
            owner.rigidDraws.add(
                    new RigidModelCapture.Draw(
                                    found.geometry,
                                    new Matrix4f(pose.pose()),
                                    owner.rigidInstance,
                                    owner.rigidOrdinal++)
                            .capturedBy(pbr));
        }
        ++draws;
        return true;
    }

    static List<RigidModelCapture.Quad> recipe(List<BakedQuad> quads, Appearance a) {
        var result = new ArrayList<RigidModelCapture.Quad>(quads.size());
        int i = 0;
        for (var q : quads) {
            int colour = a.tints.get(i++);
            float r = a.existing ? ((colour >> 16) & 255) / 255f : q.isTinted() ? a.red : 1;
            float g = a.existing ? ((colour >> 8) & 255) / 255f : q.isTinted() ? a.green : 1;
            float b = a.existing ? (colour & 255) / 255f : q.isTinted() ? a.blue : 1;
            float alpha = a.existing ? (colour >>> 24) / 255f : 1;
            result.add(
                    new RigidModelCapture.Quad(
                            q,
                            new float[] {1, 1, 1, 1},
                            r,
                            g,
                            b,
                            alpha,
                            new int[] {a.light, a.light, a.light, a.light},
                            a.overlay,
                            a.existing,
                            false));
        }
        return List.copyOf(result);
    }

    private static void emit(
            Source source, Appearance appearance, VertexConsumer target, PoseStack.Pose pose) {
        for (var quad : recipe(source.quads, appearance)) quad.emit(target, pose);
    }

    private static final class UnsupportedSource extends IllegalArgumentException {}
}
