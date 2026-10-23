package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.PartModelCapture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.FastColor;

/** Target adapter for vanilla model roots. Animation remains the model's original calculation. */
public final class VanillaModelPose {
    private VanillaModelPose() {}

    public interface AgeableAccess {
        Iterable<ModelPart> radiance$headParts();

        Iterable<ModelPart> radiance$bodyParts();

        boolean radiance$scaleHead();

        float radiance$babyYHeadOffset();

        float radiance$babyZHeadOffset();

        float radiance$babyHeadScale();

        float radiance$babyBodyScale();

        float radiance$bodyYOffset();
    }

    public interface ColorAccess {
        int radiance$modelColor();
    }

    public interface YoungRootAccess {
        float radiance$youngScale();

        float radiance$youngYOffset();
    }

    public interface RootOffsetAccess {
        float radiance$rootYOffset();
    }

    public interface PlayerExtras {
        ModelPart radiance$cloak();

        ModelPart radiance$ear();
    }

    public interface ShoulderPose {
        void radiance$shoulder(float position, float speed, float yaw, float pitch, int ticks);
    }

    public interface DefinedRoots {
        List<Group> radiance$rootGroups(boolean young);
    }

    /** Immutable root definitions are owned by the loaded model, not rebuilt for each instance. */
    public record Group(
            List<ModelPart> parts, float sx, float sy, float sz, float x, float y, float z) {
        public static Group ordinary(ModelPart... parts) {
            return new Group(List.of(parts), 1, 1, 1, 0, 0, 0);
        }
    }

    public record Part(ModelPart part, PoseStack.Pose pose, int color) {}

    private static final ClassValue<Boolean> SUPPORTED =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    try {
                        var owner =
                                type.getMethod(
                                                "renderToBuffer",
                                                PoseStack.class,
                                                com.mojang.blaze3d.vertex.VertexConsumer.class,
                                                int.class,
                                                int.class,
                                                int.class)
                                        .getDeclaringClass();
                        return owner == HierarchicalModel.class
                                || owner == ListModel.class
                                || owner == AgeableListModel.class
                                || owner == ColorableAgeableListModel.class
                                || owner == AgeableHierarchicalModel.class
                                || owner == ColorableHierarchicalModel.class
                                || owner == CamelModel.class
                                || owner == LlamaModel.class
                                || owner == RabbitModel.class
                                || owner == TurtleModel.class
                                || owner == BookModel.class
                                || owner == ShieldModel.class
                                || owner == SkullModel.class
                                || owner == PiglinHeadModel.class
                                || owner == TridentModel.class
                                || owner == net.minecraft.client.model.dragon.DragonHeadModel.class;
                    } catch (NoSuchMethodException failure) {
                        throw new IllegalStateException("Vanilla model entrypoint", failure);
                    }
                }
            };

    public static boolean supports(Model model) {
        return SUPPORTED.get(model.getClass())
                && com.radiance.mixin_related.SourceCompatibility.producer(model.getClass());
    }

    /**
     * Public part fields are read after setupAnim, including visible/skipDraw and changed children.
     */
    public static List<Part> extract(Model model, PoseStack pose, int color) {
        var result = new ArrayList<Part>();
        boolean young = model instanceof EntityModel<?> entity && entity.young;
        pose.pushPose();
        if (model instanceof RootOffsetAccess offset)
            pose.translate(0, offset.radiance$rootYOffset(), 0);
        if (model instanceof ColorAccess colored)
            color = FastColor.ARGB32.multiply(color, colored.radiance$modelColor());
        if (model instanceof DefinedRoots defined) {
            for (var group : defined.radiance$rootGroups(young)) {
                pose.pushPose();
                pose.scale(group.sx, group.sy, group.sz);
                pose.translate(group.x, group.y, group.z);
                for (var part : group.parts) visit(part, pose, color, result);
                pose.popPose();
            }
        } else if (model instanceof HierarchicalModel<?> hierarchical) {
            if (young && model instanceof YoungRootAccess scaled) {
                float factor = scaled.radiance$youngScale();
                pose.scale(factor, factor, factor);
                pose.translate(0, scaled.radiance$youngYOffset(), 0);
            }
            visit(hierarchical.root(), pose, color, result);
        } else if (model instanceof AgeableAccess ageable) {
            if (young) {
                pose.pushPose();
                if (ageable.radiance$scaleHead()) {
                    float f = 1.5F / ageable.radiance$babyHeadScale();
                    pose.scale(f, f, f);
                }
                pose.translate(
                        0,
                        ageable.radiance$babyYHeadOffset() / 16,
                        ageable.radiance$babyZHeadOffset() / 16);
                for (var part : ageable.radiance$headParts()) visit(part, pose, color, result);
                pose.popPose();
                pose.pushPose();
                float f = 1 / ageable.radiance$babyBodyScale();
                pose.scale(f, f, f);
                pose.translate(0, ageable.radiance$bodyYOffset() / 16, 0);
                for (var part : ageable.radiance$bodyParts()) visit(part, pose, color, result);
                pose.popPose();
            } else {
                for (var part : ageable.radiance$headParts()) visit(part, pose, color, result);
                for (var part : ageable.radiance$bodyParts()) visit(part, pose, color, result);
            }
        } else if (model instanceof ListModel<?> list) {
            for (var part : list.parts()) visit(part, pose, color, result);
        } else throw new IllegalArgumentException("Model roots have not been qualified");
        pose.popPose();
        return result;
    }

    private static void visit(ModelPart part, PoseStack pose, int color, List<Part> result) {
        if (!part.visible) return;
        var source = (PartModelCapture.SourceAccess) (Object) part;
        pose.pushPose();
        part.translateAndRotate(pose);
        if (!part.skipDraw && !source.radiance$cubes().isEmpty())
            result.add(new Part(part, pose.last().copy(), color));
        for (var child : source.radiance$children().values()) visit(child, pose, color, result);
        pose.popPose();
    }

    public static List<Part> extract(ModelPart root, PoseStack pose, int color) {
        var result = new ArrayList<Part>();
        visit(root, pose, color, result);
        return result;
    }
}
