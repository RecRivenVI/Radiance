package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.adapter.vanilla.VanillaEmissionAdapter;
import com.radiance.adapter.vanilla.VanillaMaterialAdapter;
import com.radiance.adapter.vanilla.VanillaModelPose;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.render.contract.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;

/** Bind same-part layers before world-space vertices exist. Native composes each resource once. */
public final class BoundPartCapture {
    private record PartPose(ModelPart part, Matrix4f pose) {}

    private record Input(
            VanillaModelPose.Part part,
            RenderType layer,
            VertexConsumer consumer,
            int light,
            int overlay,
            PBRMaterialContext.State context,
            DrawStateSnapshot state,
            LayerInput binding) {}

    private record LayerInput(
            RenderType layer,
            com.radiance.client.texture.TextureTasks.Owner texture,
            TextureAtlasSprite sprite,
            SurfaceMaterial.MaterialPolicy policy,
            RenderType glint,
            com.radiance.client.texture.TextureTasks.Owner glintTexture,
            PBRMaterialContext.State context,
            int type,
            DrawStateSnapshot ordinary,
            DrawStateSnapshot uniform) {}

    private record Key(List<RigidModelCapture.Binding> bindings) {}

    private static final Map<Key, Resource> RESOURCES = new HashMap<>();
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;
    private static long frame, bytes;

    static void invalidate() {
        SourceBindings.invalidateResources();
        RESOURCES.clear();
        bytes = 0;
    }

    static void beginFrame(long value) {
        frame = value;
        RESOURCES
                .values()
                .removeIf(
                        resource -> {
                            if (frame - resource.used <= 120) return false;
                            bytes -= resource.bytes;
                            return true;
                        });
    }

    private static final class Resource implements RigidModelCapture.Geometry {
        final long id = RigidModelCapture.nextResourceId();
        final Key key;
        final RigidModelCapture.Geometry base;
        final long bytes;
        long used;

        Resource(Key key) {
            this.key = key;
            base = key.bindings.getFirst().geometry();
            bytes = key.bindings.size() * 128L;
        }

        public long id() {
            return id;
        }

        public RenderType layer() {
            return base.layer();
        }

        public com.radiance.client.texture.TextureTasks.Owner texture() {
            return base.texture();
        }

        public MeshData mesh() {
            return base.mesh();
        }

        public List<RigidModelCapture.Binding> bindings() {
            return key.bindings;
        }
    }

    private final StorageVertexConsumerProvider owner;
    private final LinkedHashMap<PartPose, List<Input>> groups = new LinkedHashMap<>();

    public BoundPartCapture(StorageVertexConsumerProvider owner) {
        this.owner = owner;
    }

    public StorageVertexConsumerProvider owner() {
        return owner;
    }

    public void add(
            List<VanillaModelPose.Part> parts,
            RenderType layer,
            VertexConsumer consumer,
            int light,
            int overlay) {
        light = VanillaMaterialAdapter.lightmap(light);
        if (consumer
                instanceof
                StorageOutlineVertexConsumerProvider.WorldOutlineVertexConsumer outline) {
            for (var part : parts) PartModelCapture.outline(part.part(), part.pose(), outline);
            if (layer.isOutline()) return;
            consumer = outline.delegate();
        }
        var context = PBRMaterialContext.snapshot();
        var state = owner.captureDrawState(layer);
        var binding = sample(layer, consumer, context, state);
        for (var part : parts)
            groups.computeIfAbsent(
                            new PartPose(part.part(), part.pose().pose()), k -> new ArrayList<>())
                    .add(new Input(part, layer, consumer, light, overlay, context, state, binding));
    }

    private record Descriptor(
            PartModelCapture.Snapshot snapshot,
            RenderType layer,
            com.radiance.client.texture.TextureTasks.Owner texture,
            TextureAtlasSprite sprite,
            SurfaceMaterial.MaterialPolicy policy,
            int light,
            int overlay,
            int color,
            RenderType glint,
            com.radiance.client.texture.TextureTasks.Owner glintTexture,
            PBRMaterialContext.State context,
            int type,
            DrawStateSnapshot colour) {
        RigidModelCapture.Binding build() {
            try (var material = context.open()) {
                var geometry =
                        PartModelCapture.localVersion(
                                snapshot,
                                layer,
                                texture,
                                sprite,
                                policy,
                                light,
                                overlay,
                                color,
                                glint,
                                glintTexture);
                return geometry == null
                        ? null
                        : new RigidModelCapture.Binding(geometry, type, colour);
            }
        }
    }

    private LayerInput sample(
            RenderType layer,
            VertexConsumer consumer,
            PBRMaterialContext.State context,
            DrawStateSnapshot state) {
        if (!owner.declaredPartsEnabled) return null;
        VertexConsumer leaf = consumer;
        TextureAtlasSprite sprite = null;
        if (leaf.getClass() == SpriteCoordinateExpander.class
                && leaf instanceof PartModelCapture.SpriteAccess expanded) {
            sprite = expanded.radiance$sprite();
            leaf = expanded.radiance$delegate();
        }
        RenderType glint = null;
        com.radiance.client.texture.TextureTasks.Owner glintTexture = null;
        if (leaf instanceof PBRVertexConsumer.GLint decorated) {
            glint = decorated.sourceLayer();
            glintTexture = decorated.textureOwner();
            leaf = decorated.delegate();
        }
        if (glint != null && glintTexture == null || leaf.getClass() != PBRVertexConsumer.class)
            return null;
        var pbr = (PBRVertexConsumer) leaf;
        var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null || !state.uvTransform().finite()) return null;
        int type =
                MaterialFaces.encode(
                        com.radiance.client.constant.Constants.GeometryTypes.getGeometryType(
                                        layer, true)
                                .getValue(),
                        state.faces());
        type =
                SourceEmission.encode(
                        type,
                        VanillaEmissionAdapter.policy(layer, owner.surfaceRole(), false).bits());
        if (owner.surfaceRole() == SourceEmission.Role.SURFACE_LAYER_OWNER
                && VanillaMaterialAdapter.supportsSurfaceLayers(layer))
            type |= RenderAbi.SOURCE_SURFACE_LAYER_BIT;
        return new LayerInput(
                VanillaMaterialAdapter.localGeometryLayer(layer),
                texture,
                sprite,
                pbr.rigidPolicy(),
                glint,
                glintTexture,
                context,
                type,
                VanillaMaterialAdapter.sourceColour(layer, state),
                VanillaMaterialAdapter.sourceColour(layer, state.withoutUvTransform()));
    }

    private Descriptor describe(Input input, boolean uniform, PartModelCapture.Snapshot snapshot) {
        if (input.binding == null || !RigidModelCapture.compatiblePose(input.part.pose()))
            return null;
        var b = input.binding;
        return new Descriptor(
                snapshot,
                b.layer,
                b.texture,
                b.sprite,
                b.policy,
                uniform ? 0 : input.light,
                uniform
                        ? net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
                        : input.overlay,
                uniform ? -1 : input.part.color(),
                b.glint,
                b.glintTexture,
                b.context,
                b.type,
                uniform ? b.uniform : b.ordinary);
    }

    private static boolean unchanged(
            Descriptor d, Input input, boolean uniform, PartModelCapture.Snapshot snapshot) {
        var b = input.binding;
        return b != null
                && d.snapshot == snapshot
                && d.layer == b.layer
                && d.texture.equals(b.texture)
                && d.sprite == b.sprite
                && d.policy.equals(b.policy)
                && d.glint == b.glint
                && java.util.Objects.equals(d.glintTexture, b.glintTexture)
                && d.context.equals(b.context)
                && d.type == b.type
                && d.light == (uniform ? 0 : input.light)
                && d.overlay
                        == (uniform
                                ? net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
                                : input.overlay)
                && d.color == (uniform ? -1 : input.part.color())
                && d.colour.equals(uniform ? b.uniform : b.ordinary)
                && RigidModelCapture.compatiblePose(input.part.pose());
    }

    public void submit() {
        groups:
        for (var group : groups.values()) {
            var first = group.getFirst();
            var access = (PartModelCapture.SourceAccess) (Object) first.part.part();
            if (!access.radiance$declaredPart()) {
                group.forEach(BoundPartCapture::emit);
                continue;
            }
            var source = PartModelCapture.localSource(first.part.part(), access.radiance$cubes());
            if (source == null) {
                group.forEach(BoundPartCapture::emit);
                continue;
            }
            var identity =
                    source.identity(VanillaMaterialAdapter.localGeometryLayer(first.layer), frame);
            var occurrences = owner.partState().occurrences;
            int occurrence = occurrences.getOrDefault(identity, 0);
            if (identity == null || occurrence >= 32) {
                group.forEach(BoundPartCapture::emit);
                continue;
            }
            occurrences.put(identity, occurrence + 1);
            long history =
                    PartModelCapture.historyIdentity(owner.rigidInstance, identity.id, occurrence)
                            | owner.partHistoryDomain;
            boolean uniform =
                    group.stream()
                            .allMatch(
                                    input ->
                                            input.light == first.light
                                                    && input.overlay == first.overlay
                                                    && input.part.color() == first.part.color()
                                                    && input.state
                                                            .uvTransform()
                                                            .equals(first.state.uvTransform()));
            SourceBindings.Slot slot =
                    owner.sourceOwner instanceof SourceBindings.Owner sourceOwner
                            ? sourceOwner.radiance$bindings().slot(history, frame)
                            : null;
            Resource resource =
                    slot != null
                                    && slot.recipe instanceof Resource cached
                                    && RESOURCES.get(cached.key) == cached
                                    && cached.key.bindings.stream()
                                            .allMatch(b -> PartModelCapture.resident(b.geometry()))
                            ? cached
                            : null;
            if (resource != null) {
                if (!(slot.signature instanceof List<?> previous)
                        || previous.size() != group.size()) resource = null;
                else
                    for (int i = 0; i < group.size(); i++)
                        if (!(previous.get(i) instanceof Descriptor d)
                                || !unchanged(d, group.get(i), uniform, source.snapshot)) {
                            resource = null;
                            break;
                        }
            }
            if (resource == null) {
                var signature = new ArrayList<Descriptor>(group.size());
                for (var input : group) {
                    var descriptor = describe(input, uniform, source.snapshot);
                    if (descriptor == null) {
                        group.forEach(BoundPartCapture::emit);
                        continue groups;
                    }
                    signature.add(descriptor);
                }
                var bindings = new ArrayList<RigidModelCapture.Binding>(signature.size());
                for (var descriptor : signature) {
                    var binding = descriptor.build();
                    if (binding == null) {
                        group.forEach(BoundPartCapture::emit);
                        continue groups;
                    }
                    bindings.add(binding);
                }
                var key = new Key(List.copyOf(bindings));
                resource = RESOURCES.get(key);
                if (resource == null) {
                    long required = key.bindings.size() * 128L;
                    while (bytes + required > BYTE_CAPACITY) {
                        var oldest =
                                RESOURCES.entrySet().stream()
                                        .filter(e -> e.getValue().used != frame)
                                        .min(Comparator.comparingLong(e -> e.getValue().used))
                                        .orElse(null);
                        if (oldest == null) {
                            group.forEach(BoundPartCapture::emit);
                            continue groups;
                        }
                        RESOURCES.remove(oldest.getKey());
                        bytes -= oldest.getValue().bytes;
                    }
                    resource = new Resource(key);
                    RESOURCES.put(key, resource);
                    bytes += resource.bytes;
                }
                if (slot != null) {
                    slot.signature = List.copyOf(signature);
                    slot.recipe = resource;
                }
                com.radiance.api.audit.RenderAuditBridge.counter(
                        "SOURCE", "BINDING_PREPARED", 1, "");
            } else
                com.radiance.api.audit.RenderAuditBridge.counter("SOURCE", "BINDING_REUSED", 1, "");
            resource.used = frame;
            for (var binding : resource.key.bindings) PartModelCapture.touch(binding.geometry());
            var draw =
                    new RigidModelCapture.Draw(
                                    resource, new Matrix4f(first.part.pose().pose()), history)
                            .withState(resource.key.bindings.getFirst().state());
            if (uniform)
                draw.surface(
                        com.radiance.api.render.LocalSurface.uniform(
                                first.light,
                                first.overlay,
                                first.part.color(),
                                first.state.uvTransform()));
            owner.rigidDraws.add(draw);
        }
    }

    private static void emit(Input input) {
        try (var materialScope = input.context.open()) {
            var access = (PartModelCapture.SourceAccess) (Object) input.part.part();
            var source = PartModelCapture.localSource(input.part.part(), access.radiance$cubes());
            var snapshot =
                    source == null
                            ? PartModelCapture.Snapshot.read(access.radiance$cubes())
                            : source.snapshot;
            if (snapshot != null)
                snapshot.emit(
                        input.consumer,
                        input.part.pose(),
                        input.light,
                        input.overlay,
                        input.part.color());
            else
                for (var cube : access.radiance$cubes())
                    cube.compile(
                            input.part.pose(),
                            input.consumer,
                            input.light,
                            input.overlay,
                            input.part.color());
        }
    }

    /** Emit an immutable local template to procedural control/preview consumers. */
    public static void emitTemplate(
            List<VanillaModelPose.Part> parts, VertexConsumer consumer, int light, int overlay) {
        for (var part : parts) {
            var access = (PartModelCapture.SourceAccess) (Object) part.part();
            var source = PartModelCapture.localSource(part.part(), access.radiance$cubes());
            var snapshot =
                    source == null
                            ? PartModelCapture.Snapshot.read(access.radiance$cubes())
                            : source.snapshot;
            if (snapshot != null)
                snapshot.emit(consumer, part.pose(), light, overlay, part.color());
            else
                for (var cube : access.radiance$cubes())
                    cube.compile(part.pose(), consumer, light, overlay, part.color());
        }
    }
}
