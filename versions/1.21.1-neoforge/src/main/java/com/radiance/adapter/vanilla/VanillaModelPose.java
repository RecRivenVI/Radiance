package com.radiance.adapter.vanilla;

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

    private static final ThreadLocal<com.radiance.client.vertex.SourceBindings> OWNER =
            new ThreadLocal<>();

    private record PoseSlot(Object model, boolean young) {}

    public static final class Scope implements AutoCloseable {
        private final com.radiance.client.vertex.SourceBindings previous = OWNER.get();

        private Scope(Object source) {
            if (source instanceof com.radiance.client.vertex.SourceBindings.Owner owner)
                OWNER.set(owner.radiance$bindings());
            else OWNER.remove();
        }

        public void close() {
            if (previous == null) OWNER.remove();
            else OWNER.set(previous);
        }
    }

    public static Scope source(Object source) {
        return new Scope(source);
    }

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
        boolean young = model instanceof EntityModel<?> entity && entity.young;
        pose.pushPose();
        try {
            if (model instanceof RootOffsetAccess offset)
                pose.translate(0, offset.radiance$rootYOffset(), 0);
            if (model instanceof ColorAccess colored)
                color = FastColor.ARGB32.multiply(color, colored.radiance$modelColor());
            var owner = OWNER.get();
            if (owner != null) {
                var memo =
                        owner.definition(
                                new PoseSlot(model, young),
                                () -> new VanillaPoseMemo(groups(model, young)));
                return memo.evaluate(pose.last(), color);
            }
            var result = new ArrayList<Part>();
            for (var group : groups(model, young)) {
                pose.pushPose();
                pose.scale(group.sx, group.sy, group.sz);
                pose.translate(group.x, group.y, group.z);
                for (var part : group.parts) visit(part, pose, color, result);
                pose.popPose();
            }
            return result;
        } finally {
            pose.popPose();
        }
    }

    private static List<Group> groups(Model model, boolean young) {
        if (model instanceof DefinedRoots defined) {
            return defined.radiance$rootGroups(young);
        } else if (model instanceof HierarchicalModel<?> hierarchical) {
            if (young && model instanceof YoungRootAccess scaled) {
                float factor = scaled.radiance$youngScale();
                return List.of(
                        new Group(
                                List.of(hierarchical.root()),
                                factor,
                                factor,
                                factor,
                                0,
                                scaled.radiance$youngYOffset(),
                                0));
            }
            return List.of(Group.ordinary(hierarchical.root()));
        } else if (model instanceof AgeableAccess ageable) {
            var head = new ArrayList<ModelPart>();
            ageable.radiance$headParts().forEach(head::add);
            var body = new ArrayList<ModelPart>();
            ageable.radiance$bodyParts().forEach(body::add);
            if (young) {
                float h =
                        ageable.radiance$scaleHead() ? 1.5F / ageable.radiance$babyHeadScale() : 1;
                float b = 1 / ageable.radiance$babyBodyScale();
                return List.of(
                        new Group(
                                List.copyOf(head),
                                h,
                                h,
                                h,
                                0,
                                ageable.radiance$babyYHeadOffset() / 16,
                                ageable.radiance$babyZHeadOffset() / 16),
                        new Group(
                                List.copyOf(body),
                                b,
                                b,
                                b,
                                0,
                                ageable.radiance$bodyYOffset() / 16,
                                0));
            }
            return List.of(
                    new Group(List.copyOf(head), 1, 1, 1, 0, 0, 0),
                    new Group(List.copyOf(body), 1, 1, 1, 0, 0, 0));
        } else if (model instanceof ListModel<?> list) {
            var parts = new ArrayList<ModelPart>();
            list.parts().forEach(parts::add);
            return List.of(new Group(List.copyOf(parts), 1, 1, 1, 0, 0, 0));
        } else throw new IllegalArgumentException("Model roots have not been qualified");
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
        var owner = OWNER.get();
        if (owner != null)
            return owner.definition(
                            new PoseSlot(root, false),
                            () -> new VanillaPoseMemo(List.of(Group.ordinary(root))))
                    .evaluate(pose.last(), color);
        var result = new ArrayList<Part>();
        visit(root, pose, color, result);
        return result;
    }
}
