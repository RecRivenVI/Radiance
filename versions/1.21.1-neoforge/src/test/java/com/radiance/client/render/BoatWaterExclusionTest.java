package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.RaftModel;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class BoatWaterExclusionTest {
    @Test
    void rendererInjectionTargetsOneActualPatchDrawWithOneLiveSourceModel() throws Exception {
        ClassNode renderer = new ClassNode();
        try (var bytes =
                getClass()
                        .getResourceAsStream(
                                "/net/minecraft/client/renderer/entity/BoatRenderer.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(renderer, 0);
        }
        var render =
                renderer.methods.stream()
                        .filter(
                                m ->
                                        m.name.equals("render")
                                                && m.desc.startsWith(
                                                        "(Lnet/minecraft/world/entity/vehicle/Boat;"))
                        .findFirst()
                        .orElseThrow();
        int patchCalls = 0;
        for (var instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/model/geom/ModelPart")
                    && call.name.equals("render")
                    && call.desc.equals(
                            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V")) {
                ++patchCalls;
                int at = render.instructions.indexOf(call);
                assertEquals(
                        1,
                        render.localVariables.stream()
                                .filter(
                                        v ->
                                                v.desc.equals(
                                                                "Lnet/minecraft/client/model/ListModel;")
                                                        && render.instructions.indexOf(v.start)
                                                                <= at
                                                        && render.instructions.indexOf(v.end) > at)
                                .count());
            }
        }
        assertEquals(1, patchCalls);
    }

    @Test
    void actualBoatAndChestBoatCapReachTheHullAndExcludeTheEquilibriumWaterLevel() {
        for (boolean chest : List.of(false, true)) {
            ModelPart root =
                    (chest ? ChestBoatModel.createBodyModel() : BoatModel.createBodyModel())
                            .bakeRoot();
            BoatModel model = chest ? new ChestBoatModel(root) : new BoatModel(root);
            PoseStack pose = sourcePose();
            Sink old = new Sink();
            model.waterPatch().render(pose, old, 0, 0);
            Sink fixed = new Sink();
            BoatWaterExclusion.render(
                    model.waterPatch(), root.getChild("bottom"), pose, fixed, 0, 0);
            assertEquals(24, fixed.positions.size());
            assertEquals(.375f, old.minY(), 1e-6);
            assertEquals(.1875f, fixed.minY(), 1e-6);
            assertEquals(.5625f, fixed.maxY(), 1e-6);
            float waterLevel = .65f * .5625f;
            assertFalse(old.contains(new Vector3f(0, waterLevel, 0)));
            assertTrue(fixed.contains(new Vector3f(0, waterLevel, 0)));
            assertFalse(fixed.contains(new Vector3f(0, .1f, 0)));
            assertFalse(fixed.contains(new Vector3f(2, waterLevel, 0)));
            Sink unchanged = new Sink();
            model.waterPatch().render(pose, unchanged, 0, 0);
            assertEquals(old.positions, unchanged.positions);
        }
    }

    @Test
    void capturedVolumeFollowsHurtBubbleSublevelAndMirroredOwnerPosesExactlyOnce() {
        ModelPart root = BoatModel.createBodyModel().bakeRoot();
        var patch = new BoatModel(root).waterPatch();
        for (float mirror : new float[] {-1, 1}) {
            PoseStack pose = new PoseStack();
            pose.translate(80, -12, 40);
            pose.mulPose(new Quaternionf().rotationXYZ(.32f, .61f, -.19f));
            pose.scale(mirror, 2, .75f);
            Matrix4f owner = new Matrix4f(pose.last().pose());
            pose.mulPose(sourcePose().last().pose());
            Sink transformed = new Sink();
            BoatWaterExclusion.render(patch, root.getChild("bottom"), pose, transformed, 0, 0);
            Sink reference = new Sink();
            BoatWaterExclusion.render(
                    patch, root.getChild("bottom"), sourcePose(), reference, 0, 0);
            for (int i = 0; i < reference.positions.size(); ++i)
                assertTrue(
                        owner.transformPosition(new Vector3f(reference.positions.get(i)))
                                        .distance(transformed.positions.get(i))
                                < 2e-5);
        }
    }

    @Test
    void raftHasNoSourceWaterPatchAndHiddenPatchDoesNotInventAnInterior() {
        assertFalse(WaterPatchModel.class.isAssignableFrom(RaftModel.class));
        ModelPart root = BoatModel.createBodyModel().bakeRoot();
        var patch = new BoatModel(root).waterPatch();
        patch.visible = false;
        Sink sink = new Sink();
        BoatWaterExclusion.render(patch, root.getChild("bottom"), sourcePose(), sink, 0, 0);
        assertTrue(sink.positions.isEmpty());
    }

    @Test
    void customModelWithoutASourceFloorKeepsItsFiniteOriginalCap() {
        ModelPart root = BoatModel.createBodyModel().bakeRoot();
        var patch = new BoatModel(root).waterPatch();
        Sink original = new Sink(), fallback = new Sink();
        patch.render(sourcePose(), original, 0, 0);
        BoatWaterExclusion.render(patch, null, sourcePose(), fallback, 0, 0);
        assertEquals(original.positions, fallback.positions);
    }

    @Test
    void floorBoundaryUsesRenderedPolygonDeformationInsteadOfUndeformedCubeBounds() {
        ModelPart root = BoatModel.createBodyModel().bakeRoot();
        ModelPart floor =
                new ModelPart(
                        List.of(
                                new ModelPart.Cube(
                                        0,
                                        0,
                                        -14,
                                        -9,
                                        -3,
                                        28,
                                        16,
                                        3,
                                        .5f,
                                        .5f,
                                        .5f,
                                        false,
                                        128,
                                        64,
                                        EnumSet.allOf(Direction.class))),
                        Map.of());
        floor.loadPose(root.getChild("bottom").getInitialPose());
        Sink sink = new Sink();
        BoatWaterExclusion.render(
                new BoatModel(root).waterPatch(), floor, sourcePose(), sink, 0, 0);
        assertEquals(.21875f, sink.minY(), 1e-6);
        assertEquals(.5625f, sink.maxY(), 1e-6);
    }

    static PoseStack sourcePose() {
        PoseStack pose = new PoseStack();
        pose.translate(0, .375, 0);
        pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
        pose.scale(-1, -1, 1);
        pose.mulPose(new Quaternionf().rotationY((float) Math.PI / 2));
        return pose;
    }

    static class Sink implements VertexConsumer {
        final List<Vector3f> positions = new ArrayList<>();

        public VertexConsumer addVertex(float x, float y, float z) {
            positions.add(new Vector3f(x, y, z));
            return this;
        }

        float minY() {
            return (float) positions.stream().mapToDouble(p -> p.y).min().orElseThrow();
        }

        float maxY() {
            return (float) positions.stream().mapToDouble(p -> p.y).max().orElseThrow();
        }

        boolean contains(Vector3f p) {
            Vector3f low = new Vector3f(Float.POSITIVE_INFINITY),
                    high = new Vector3f(Float.NEGATIVE_INFINITY);
            positions.forEach(
                    v -> {
                        low.min(v);
                        high.max(v);
                    });
            return p.x >= low.x
                    && p.x <= high.x
                    && p.y >= low.y
                    && p.y <= high.y
                    && p.z >= low.z
                    && p.z <= high.z;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
