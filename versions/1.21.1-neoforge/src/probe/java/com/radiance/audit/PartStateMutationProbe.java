package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Real baker/traversal checks for public writes, child replacement and shared-model owners. */
public final class PartStateMutationProbe {
    private PartStateMutationProbe() {}

    private static ModelPart bake() {
        var mesh = new MeshDefinition();
        var first =
                mesh.getRoot()
                        .addOrReplaceChild(
                                "first",
                                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4),
                                PartPose.ZERO);
        first.addOrReplaceChild(
                "nested",
                CubeListBuilder.create().texOffs(0, 8).addBox(0, 0, 0, 2, 3, 4),
                PartPose.offset(0, 5, 0));
        mesh.getRoot()
                .addOrReplaceChild(
                        "second",
                        CubeListBuilder.create().texOffs(8, 0).addBox(0, 0, 0, 3, 4, 5),
                        PartPose.offset(5, 0, 0));
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> verify(Minecraft mc)
            throws ReflectiveOperationException {
        var root = bake();
        var first = root.getChild("first");
        var children = (Map<String, ModelPart>) RigidModelProbe.field(root, "children");
        var renderer =
                mc.getBlockEntityRenderDispatcher()
                        .getRenderer(
                                mc.level.getBlockEntity(
                                        new net.minecraft.core.BlockPos(-10, 65, 8)));
        var layer =
                RenderType.entityCutoutNoCull(
                        ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"));
        var rows = new ArrayList<Map<String, Object>>();
        Set<Long> initialHistory = null;
        for (int state = 0; state < 9; state++) {
            if (state == 1) {
                first.x = 3.25f;
                first.yRot = .65f;
                first.xRot = -.4f;
            }
            if (state == 2) first.visible = false;
            if (state == 3) {
                first.visible = true;
                first.skipDraw = true;
            }
            if (state == 4) children.remove("first");
            if (state == 5) children.put("first", bake().getChild("first"));
            var pose = new PoseStack();
            pose.translate(1.5, -2.25, 3.5);
            pose.scale(state == 1 ? -.75f : .75f, state == 6 ? 1.5f : .75f, .75f);
            var policy =
                    state == 8
                            ? new com.radiance.render.contract.SurfaceMaterial.MaterialPolicy(
                                    PBRVertexConsumer.getAlphaMode(layer),
                                    com.radiance.render.contract.SurfaceMaterial.ColorSemantics
                                            .SURFACE_MIX,
                                    true,
                                    true,
                                    true,
                                    com.radiance.render.contract.SurfaceMaterial.NormalSemantics
                                            .GEOMETRIC_BRDF)
                            : null;
            try (var provider = new Provider(policy);
                    var referenceStorage = new ByteBufferBuilder(8192)) {
                provider.enableRigidModels(state == 7 ? 700002 : 700001);
                provider.enablePartModels(root, renderer, false);
                var actual = (PBRVertexConsumer) provider.getBuffer(layer);
                root.render(pose, actual, 0x00c000d0, 3 << 16, 0x80a0b0c0);
                var reference =
                        policy == null
                                ? new PBRVertexConsumer(referenceStorage, layer)
                                : PBRVertexConsumer.dynamicMaterial(
                                        referenceStorage, layer, false, policy);
                root.render(pose, reference, 0x00c000d0, 3 << 16, 0x80a0b0c0);
                var draws = provider.takeRigidModels();
                var history = new HashSet<Long>();
                int direct = 0;
                for (var draw : draws) {
                    history.add((Long) RigidModelProbe.field(draw, "explicitHistory"));
                    var entry = RigidModelProbe.field(draw, "entry");
                    direct +=
                            ((MeshData) RigidModelProbe.field(entry, "mesh"))
                                    .drawState()
                                    .vertexCount();
                }
                try (var fallback = actual.endNullable();
                        var expected = reference.end()) {
                    int captured = fallback == null ? 0 : fallback.drawState().vertexCount();
                    if (direct + captured != expected.drawState().vertexCount())
                        throw new AssertionError(
                                "Public state/children traversal changed at " + state);
                    // Precise PartModelProbe independently expands the selected local mesh using
                    // this source's pose, normal and all material attributes, including state 6.
                    if (direct == 0 || captured != 0)
                        throw new AssertionError(
                                "Declared test parts not directly submitted at " + state);
                    if (state == 0) initialHistory = Set.copyOf(history);
                    if (state == 1 && !initialHistory.equals(history))
                        throw new AssertionError("Pose-only change replaced source history");
                    if ((state == 2 || state == 3 || state == 4)
                            && !initialHistory.containsAll(history))
                        throw new AssertionError("Hidden/removed part donated history");
                    if (state == 7 && history.stream().anyMatch(initialHistory::contains))
                        throw new AssertionError("Shared model mixed independent owners");
                    rows.add(
                            Map.of(
                                    "state",
                                    state,
                                    "directVertices",
                                    direct,
                                    "fallbackVertices",
                                    captured,
                                    "history",
                                    history.stream().map(String::valueOf).sorted().toList()));
                }
            }
        }
        return rows;
    }

    private static final class Provider extends StorageVertexConsumerProvider
            implements AutoCloseable {
        private final com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy;

        Provider(com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy) {
            super(8192);
            this.policy = policy;
        }

        @Override
        protected VertexConsumer createQuadConsumer(ByteBufferBuilder buffer, RenderType layer) {
            return policy == null
                    ? super.createQuadConsumer(buffer, layer)
                    : PBRVertexConsumer.dynamicMaterial(buffer, layer, false, policy);
        }
    }
}
