package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.PartModelCapture;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Source-owned pose graph. Public animation fields are sampled; unchanged subtrees keep their
 * result.
 */
final class VanillaPoseMemo {
    private final List<Root> roots;
    private List<VanillaModelPose.Part> result = List.of();

    VanillaPoseMemo(List<VanillaModelPose.Group> groups) {
        this(groups, part -> (PartModelCapture.SourceAccess) (Object) part);
    }

    VanillaPoseMemo(
            List<VanillaModelPose.Group> groups,
            java.util.function.Function<ModelPart, PartModelCapture.SourceAccess> access) {
        roots = groups.stream().map(group -> new Root(group, access)).toList();
    }

    List<VanillaModelPose.Part> evaluate(PoseStack.Pose parent, int color) {
        boolean changed = result.isEmpty();
        int evaluated = 0, updated = 0;
        for (var root : roots) {
            changed |= root.update(parent, color);
            evaluated += root.evaluated;
            updated += root.updated;
        }
        if (changed) {
            var values = new ArrayList<VanillaModelPose.Part>();
            roots.forEach(root -> root.nodes.forEach(node -> node.collect(values)));
            result = List.copyOf(values);
        }
        com.radiance.api.audit.RenderAuditBridge.counter(
                "SOURCE", "POSE_NODES_SAMPLED", evaluated, "");
        com.radiance.api.audit.RenderAuditBridge.counter(
                "SOURCE", "POSE_NODES_COMPOSED", updated, "");
        com.radiance.api.audit.RenderAuditBridge.counter(
                "SOURCE", changed ? "POSE_RESULT_UPDATED" : "POSE_RESULT_REUSED", 1, "");
        return result;
    }

    private static final class Root {
        final VanillaModelPose.Group group;
        final List<Node> nodes;
        final PoseStack pose = new PoseStack();
        final org.joml.Matrix4f input = new org.joml.Matrix4f();
        final org.joml.Matrix3f normal = new org.joml.Matrix3f();
        boolean initialized;
        int evaluated, updated;

        Root(
                VanillaModelPose.Group group,
                java.util.function.Function<ModelPart, PartModelCapture.SourceAccess> access) {
            this.group = group;
            nodes = group.parts().stream().map(part -> new Node(part, access)).toList();
        }

        boolean update(PoseStack.Pose parent, int color) {
            if (!initialized || !input.equals(parent.pose()) || !normal.equals(parent.normal())) {
                input.set(parent.pose());
                normal.set(parent.normal());
                pose.last().pose().set(input);
                pose.last().normal().set(normal);
                pose.scale(group.sx(), group.sy(), group.sz());
                pose.translate(group.x(), group.y(), group.z());
                initialized = true;
            }
            evaluated = updated = 0;
            boolean changed = false;
            for (var node : nodes) changed |= node.update(pose.last(), color, this);
            return changed;
        }
    }

    private static final class Node {
        final ModelPart part;
        final PartModelCapture.SourceAccess source;
        final java.util.function.Function<ModelPart, PartModelCapture.SourceAccess> access;
        final PoseStack pose = new PoseStack();
        final org.joml.Matrix4f input = new org.joml.Matrix4f();
        final org.joml.Matrix3f normal = new org.joml.Matrix3f();
        final float[] state = new float[9];
        List<Node> children = List.of();
        VanillaModelPose.Part result;
        boolean initialized, visible;

        Node(
                ModelPart part,
                java.util.function.Function<ModelPart, PartModelCapture.SourceAccess> access) {
            this.part = part;
            this.access = access;
            source = access.apply(part);
        }

        boolean update(PoseStack.Pose parent, int color, Root counts) {
            counts.evaluated++;
            boolean changed = visible != part.visible;
            visible = part.visible;
            if (!visible) return changed;
            boolean transform =
                    !initialized
                            || !input.equals(parent.pose())
                            || !normal.equals(parent.normal())
                            || state[0] != part.x
                            || state[1] != part.y
                            || state[2] != part.z
                            || state[3] != part.xRot
                            || state[4] != part.yRot
                            || state[5] != part.zRot
                            || state[6] != part.xScale
                            || state[7] != part.yScale
                            || state[8] != part.zScale;
            if (transform) {
                input.set(parent.pose());
                normal.set(parent.normal());
                state[0] = part.x;
                state[1] = part.y;
                state[2] = part.z;
                state[3] = part.xRot;
                state[4] = part.yRot;
                state[5] = part.zRot;
                state[6] = part.xScale;
                state[7] = part.yScale;
                state[8] = part.zScale;
                pose.last().pose().set(input);
                pose.last().normal().set(normal);
                part.translateAndRotate(pose);
                initialized = true;
                counts.updated++;
            }
            boolean drawn = !part.skipDraw && !source.radiance$cubes().isEmpty();
            if (drawn && (transform || result == null || result.color() != color)) {
                result = new VanillaModelPose.Part(part, pose.last().copy(), color);
                changed = true;
            } else if (!drawn && result != null) {
                result = null;
                changed = true;
            }
            var actual = source.radiance$children().values();
            boolean same = actual.size() == children.size();
            int i = 0;
            if (same)
                for (var child : actual)
                    if (children.get(i++).part != child) {
                        same = false;
                        break;
                    }
            if (!same) {
                children = actual.stream().map(part -> new Node(part, access)).toList();
                changed = true;
            }
            for (var child : children) changed |= child.update(pose.last(), color, counts);
            return changed;
        }

        void collect(List<VanillaModelPose.Part> values) {
            if (!visible) return;
            if (result != null) values.add(result);
            children.forEach(child -> child.collect(values));
        }
    }
}
