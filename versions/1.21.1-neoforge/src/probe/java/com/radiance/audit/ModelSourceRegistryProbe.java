package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.lang.reflect.Modifier;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Enumerates actual loaded registries. Synthetic input checks are never native scene submissions.
 */
public final class ModelSourceRegistryProbe {
    private record Source(String id, Object renderer) {}

    private static final List<Source> sources = new ArrayList<>();
    private static final List<Map<String, Object>> rows = new ArrayList<>();
    private static int next;
    private static long lastFrame;

    private ModelSourceRegistryProbe() {}

    @SuppressWarnings("unchecked")
    public static void begin(Minecraft mc) throws ReflectiveOperationException {
        if (!sources.isEmpty()) throw new IllegalStateException("Registry audit already started");
        var entities =
                (Map<net.minecraft.world.entity.EntityType<?>, ?>)
                        RigidModelProbe.field(mc.getEntityRenderDispatcher(), "renderers");
        entities.forEach(
                (type, renderer) -> {
                    var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (id.getNamespace().equals("minecraft"))
                        sources.add(new Source("entity/" + id, renderer));
                });
        mc.getEntityRenderDispatcher()
                .getSkinMap()
                .forEach(
                        (skin, renderer) ->
                                sources.add(new Source("player/" + skin.name(), renderer)));
        var blockEntities =
                (Map<net.minecraft.world.level.block.entity.BlockEntityType<?>, ?>)
                        RigidModelProbe.field(mc.getBlockEntityRenderDispatcher(), "renderers");
        blockEntities.forEach(
                (type, renderer) -> {
                    var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                    if (id.getNamespace().equals("minecraft"))
                        sources.add(new Source("ber/" + id, renderer));
                });
        sources.sort(Comparator.comparing(Source::id));
    }

    public static boolean poll(Minecraft mc) throws ReflectiveOperationException {
        if (next == sources.size()) return true;
        if (++lastFrame % 4 != 0) return false;
        Source source = sources.get(next++);
        var candidates = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        collect(source.renderer, candidates);
        boolean wholeOwner =
                source.renderer instanceof net.minecraft.client.renderer.entity.EnderDragonRenderer;
        if (source.renderer
                instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> living) {
            var layers =
                    ((com.radiance.mixins.vulkan_render_integration.accessor
                                            .LivingRendererLayersAccessor)
                                    (Object) living)
                            .radiance$getLayers();
            var plan =
                    com.radiance.compatibility.vanilla.VanillaLayerAdapter.describe(
                            layers, false, false);
            wholeOwner |= plan.eyeOwner() || plan.surfaceOwner();
        }
        long before = PartModelProbe.checked();
        for (var candidate : wholeOwner ? List.of() : candidates) {
            for (int poseIndex = 0; poseIndex < 5; poseIndex++) {
                var pose = new PoseStack();
                pose.translate(1.25, -.75, 2.5);
                pose.mulPose(
                        new org.joml.Quaternionf()
                                .rotationXYZ(poseIndex * .17f, poseIndex * -.21f, .31f));
                pose.scale(poseIndex % 2 == 0 ? .75f : -.75f, .75f, .75f);
                try (var owner = new Provider()) {
                    owner.enableRigidModels(700000 + next);
                    owner.enablePartModels(new Object(), source.renderer, false);
                    var buffer =
                            owner.getBuffer(
                                    RenderType.entityCutoutNoCull(
                                            ResourceLocation.withDefaultNamespace(
                                                    "textures/entity/zombie/zombie.png")));
                    int colour = 0x90a0b0c0, light = 0x00c000b0;
                    int overlay = (poseIndex % 2 == 0 ? 10 : 3) << 16;
                    PartModelProbe.precise(
                            () -> {
                                if (candidate instanceof Model model) {
                                    model.renderToBuffer(pose, buffer, light, overlay, colour);
                                } else if (candidate instanceof ModelPart part) {
                                    var sprite =
                                            mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                                                    .apply(
                                                            ResourceLocation.withDefaultNamespace(
                                                                    "block/stone"));
                                    part.render(pose, sprite.wrap(buffer), light, overlay, colour);
                                }
                            });
                }
            }
        }
        long compared = PartModelProbe.checked() - before;
        var row = new LinkedHashMap<String, Object>();
        row.put("source", source.id);
        row.put("renderer", source.renderer.getClass().getName());
        if (Boolean.getBoolean("radiance.audit.rendererReplacement"))
            row.put("replacement", RendererReplacementProbe.compare(mc, source.id));
        row.put("localModels", candidates.size());
        row.put("actualCubeComparisons", compared);
        row.put("route", compared > 0 ? "MODEL_PART_INPUT_COMPARED" : "CAPTURE");
        row.put(
                "reason",
                compared > 0
                        ? "Original model traversal, pose, colour, overlay and sprite wrapper inputs compared; renderer callbacks retained"
                        : wholeOwner
                                ? "Coincident eye/surface layer composition retains the complete captured owner"
                                : candidates.isEmpty()
                                        ? "Procedural/baked/source-specific model; no declared local ModelPart resource"
                                        : "Source qualification, consumer or bounded capacity requires original capture");
        if (source.renderer
                instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> living) {
            var layers =
                    ((com.radiance.mixins.vulkan_render_integration.accessor
                                            .LivingRendererLayersAccessor)
                                    (Object) living)
                            .radiance$getLayers();
            row.put("layers", layers.stream().map(v -> v.getClass().getName()).toList());
            row.put(
                    "layerPlan",
                    com.radiance.compatibility.vanilla.VanillaLayerAdapter.describe(
                            layers, true, false));
        }
        rows.add(row);
        return next == sources.size();
    }

    private static void collect(Object renderer, Set<Object> target)
            throws ReflectiveOperationException {
        for (Class<?> type = renderer.getClass();
                type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(renderer);
                add(value, target, 0);
            }
        }
    }

    private static void add(Object value, Set<Object> target, int depth) {
        if (value == null || depth > 3) return;
        if (value instanceof Model || value instanceof ModelPart) target.add(value);
        else if (value instanceof Map<?, ?> map && map.size() <= 128)
            for (var item : map.values()) add(item, target, depth + 1);
        else if (value instanceof Iterable<?> list) {
            int count = 0;
            for (var item : list) {
                if (++count > 128) break;
                add(item, target, depth + 1);
            }
        } else if (value instanceof com.mojang.datafixers.util.Pair<?, ?> pair) {
            add(pair.getFirst(), target, depth + 1);
            add(pair.getSecond(), target, depth + 1);
        }
    }

    public static List<Map<String, Object>> rows() {
        return List.copyOf(rows);
    }

    private static final class Provider extends StorageVertexConsumerProvider
            implements AutoCloseable {
        Provider() {
            super(256);
        }
    }
}
