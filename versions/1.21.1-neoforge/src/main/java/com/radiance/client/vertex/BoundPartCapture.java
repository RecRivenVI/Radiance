package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.compatibility.vanilla.VanillaEmissionAdapter;
import com.radiance.compatibility.vanilla.VanillaMaterialAdapter;
import com.radiance.compatibility.vanilla.VanillaModelPose;
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
            DrawStateSnapshot state) {}

    private record Key(List<RigidModelCapture.Binding> bindings) {}

    private static final Map<Key, Resource> RESOURCES = new HashMap<>();
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;
    private static long frame, bytes;

    static void invalidate() {
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
            bytes =
                    key.bindings.stream()
                            .mapToLong(
                                    binding -> binding.geometry().mesh().vertexBuffer().remaining())
                            .sum();
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
        if (consumer
                instanceof
                StorageOutlineVertexConsumerProvider.WorldOutlineVertexConsumer outline) {
            for (var part : parts) PartModelCapture.outline(part.part(), part.pose(), outline);
            if (layer.isOutline()) return;
            consumer = outline.delegate();
        }
        var context = PBRMaterialContext.snapshot();
        var state = owner.captureDrawState(layer);
        for (var part : parts)
            groups.computeIfAbsent(
                            new PartPose(part.part(), part.pose().pose()), k -> new ArrayList<>())
                    .add(new Input(part, layer, consumer, light, overlay, context, state));
    }

    public void submit() {
        groups:
        for (var group : groups.values()) {
            var bindings = new ArrayList<RigidModelCapture.Binding>();
            boolean direct = owner.declaredPartsEnabled;
            for (var input : group) {
                if (!direct) break;
                try (var materialScope = input.context.open()) {
                    if (!RigidModelCapture.uniformPose(input.part.pose())) {
                        direct = false;
                        break;
                    }
                    VertexConsumer leaf = input.consumer;
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
                    if (glint != null && glintTexture == null) {
                        direct = false;
                        break;
                    }
                    if (leaf.getClass() != PBRVertexConsumer.class) {
                        direct = false;
                        break;
                    }
                    var pbr = (PBRVertexConsumer) leaf;
                    var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
                    var state = input.state;
                    if (texture == null
                            || !state.uvTransform()
                                    .equals(DrawStateSnapshot.UvTransform.IDENTITY)) {
                        direct = false;
                        break;
                    }
                    var sourceAccess = (PartModelCapture.SourceAccess) (Object) input.part.part();
                    if (!sourceAccess.radiance$declaredPart()) {
                        direct = false;
                        break;
                    }
                    var source =
                            PartModelCapture.localSource(
                                    input.part.part(), sourceAccess.radiance$cubes());
                    if (source == null) {
                        direct = false;
                        break;
                    }
                    var geometry =
                            PartModelCapture.localVersion(
                                    source.snapshot,
                                    input.layer,
                                    texture,
                                    sprite,
                                    pbr.rigidPolicy(),
                                    input.light,
                                    input.overlay,
                                    input.part.color(),
                                    glint,
                                    glintTexture);
                    if (geometry == null) {
                        direct = false;
                        break;
                    }
                    int type =
                            MaterialFaces.encode(
                                    com.radiance.client.constant.Constants.GeometryTypes
                                            .getGeometryType(input.layer, true)
                                            .getValue(),
                                    state.faces());
                    type =
                            SourceEmission.encode(
                                    type,
                                    VanillaEmissionAdapter.policy(
                                                    input.layer, owner.surfaceRole(), false)
                                            .bits());
                    if (owner.surfaceRole() == SourceEmission.Role.SURFACE_LAYER_OWNER
                            && VanillaMaterialAdapter.supportsSurfaceLayers(input.layer))
                        type |= RenderAbi.SOURCE_SURFACE_LAYER_BIT;
                    bindings.add(
                            new RigidModelCapture.Binding(
                                    geometry,
                                    type,
                                    VanillaMaterialAdapter.sourceColour(input.layer, state)));
                }
            }
            if (!direct) {
                for (var input : group) emit(input);
                continue;
            }
            var key = new Key(List.copyOf(bindings));
            var resource = RESOURCES.get(key);
            if (resource == null) {
                long required =
                        key.bindings.stream()
                                .mapToLong(
                                        binding ->
                                                binding.geometry()
                                                        .mesh()
                                                        .vertexBuffer()
                                                        .remaining())
                                .sum();
                while (bytes + required > BYTE_CAPACITY) {
                    var oldest =
                            RESOURCES.entrySet().stream()
                                    .filter(e -> e.getValue().used != frame)
                                    .min(Comparator.comparingLong(e -> e.getValue().used))
                                    .orElse(null);
                    if (oldest == null) {
                        for (var input : group) emit(input);
                        continue groups;
                    }
                    RESOURCES.remove(oldest.getKey());
                    bytes -= oldest.getValue().bytes;
                }
                resource = new Resource(key);
                RESOURCES.put(key, resource);
                bytes += resource.bytes;
            }
            resource.used = frame;
            var first = group.getFirst();
            var access = (PartModelCapture.SourceAccess) (Object) first.part.part();
            var source = PartModelCapture.localSource(first.part.part(), access.radiance$cubes());
            var identity = source.identity(first.layer, frame);
            var occurrences = owner.partState().occurrences;
            int occurrence = occurrences.getOrDefault(identity, 0);
            if (identity == null || occurrence >= 32) {
                for (var input : group) emit(input);
                continue;
            }
            occurrences.put(identity, occurrence + 1);
            owner.rigidDraws.add(
                    new RigidModelCapture.Draw(
                                    resource,
                                    new Matrix4f(first.part.pose().pose()),
                                    PartModelCapture.historyIdentity(
                                                    owner.rigidInstance, identity.id, occurrence)
                                            | owner.partHistoryDomain)
                            .withState(bindings.getFirst().state()));
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
