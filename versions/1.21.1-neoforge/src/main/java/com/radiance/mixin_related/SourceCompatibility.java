package com.radiance.mixin_related;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformerActivity;
import java.util.*;

/** Current-Target adapter: use the audit of actually defined classes, not installed-mod names. */
public final class SourceCompatibility {
    public enum Source {
        PARTS(
                MixinPlugin.PART_SOURCE_PROPERTY,
                List.of(
                        "net.minecraft.client.model.geom.ModelPart",
                        "net.minecraft.client.model.geom.ModelPart$Cube",
                        "net.minecraft.client.model.geom.builders.PartDefinition",
                        "net.minecraft.client.model.geom.builders.CubeDefinition",
                        "net.minecraft.client.renderer.SpriteCoordinateExpander")),
        BAKED(
                MixinPlugin.BAKED_SOURCE_PROPERTY,
                List.of(
                        "net.minecraft.client.resources.model.SimpleBakedModel",
                        "net.minecraft.client.resources.model.WeightedBakedModel",
                        "net.minecraft.client.resources.model.MultiPartBakedModel",
                        "net.neoforged.neoforge.client.model.IDynamicBakedModel",
                        "net.minecraft.client.resources.model.BakedModel",
                        "net.neoforged.neoforge.client.extensions.IBakedModelExtension",
                        "net.minecraft.client.renderer.block.model.ItemOverrides",
                        "net.minecraft.client.renderer.block.model.ItemOverrides$BakedOverride",
                        "net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper",
                        "net.neoforged.neoforge.client.model.ElementsModel",
                        "net.minecraft.client.renderer.block.model.BlockModel",
                        "net.minecraft.client.renderer.block.model.BakedQuad",
                        "net.minecraft.client.resources.model.ModelManager",
                        "net.minecraft.client.renderer.entity.ItemRenderer",
                        "net.neoforged.neoforge.client.model.lighting.LightPipelineAwareModelBlockRenderer",
                        "net.neoforged.neoforge.client.model.lighting.QuadLighter",
                        "net.minecraft.client.renderer.block.ModelBlockRenderer")),
        STATIC_STATE(
                MixinPlugin.STATE_SOURCE_PROPERTY,
                List.of(
                        "net.minecraft.client.renderer.RenderType",
                        "net.minecraft.client.renderer.RenderType$CompositeState",
                        "net.minecraft.client.renderer.RenderStateShard",
                        "net.minecraft.client.renderer.Sheets")),
        PAINTING(
                MixinPlugin.PAINTING_SOURCE_PROPERTY,
                List.of(
                        "net.minecraft.client.renderer.entity.PaintingRenderer",
                        "net.minecraft.client.renderer.texture.TextureAtlasSprite"));

        final String property;
        final List<String> classes;

        Source(String property, List<String> classes) {
            this.property = property;
            this.classes = classes;
        }
    }

    public record Verdict(boolean compatible, String reason, List<String> evidence) {
        public Verdict {
            evidence = List.copyOf(evidence);
        }
    }

    private static final Map<Source, Verdict> VERDICTS = new EnumMap<>(Source.class);
    private static final Map<Class<?>, Verdict> PRODUCERS = new WeakHashMap<>();
    private static final Map<Class<?>, Verdict> DECLARATIONS = new WeakHashMap<>();

    private SourceCompatibility() {}

    public static synchronized boolean compatible(Source source) {
        var verdict = VERDICTS.get(source);
        if (verdict == null) {
            verdict = inspect(source);
            VERDICTS.put(source, verdict);
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "TAKEOVER_SOURCE source={} route={} reason={} evidence={}",
                            source,
                            verdict.compatible ? "DIRECT" : "CAPTURE",
                            verdict.reason,
                            verdict.evidence);
        }
        return verdict.compatible;
    }

    public static synchronized Map<String, Verdict> coverage() {
        var result = new LinkedHashMap<String, Verdict>();
        for (var entry : VERDICTS.entrySet()) result.put(entry.getKey().name(), entry.getValue());
        for (var entry : PRODUCERS.entrySet())
            result.put(entry.getKey().getName(), entry.getValue());
        return Map.copyOf(result);
    }

    public static synchronized boolean producer(Class<?> actual) {
        return producer(actual, true, PRODUCERS);
    }

    /** A known leaf algorithm whose ambient parent query is deliberately not evaluated. */
    public static synchronized boolean declaredProducer(Class<?> actual) {
        return producer(actual, false, DECLARATIONS);
    }

    private static boolean producer(
            Class<?> actual, boolean hierarchy, Map<Class<?>, Verdict> cache) {
        var existing = cache.get(actual);
        if (existing != null) return existing.compatible;
        var evidence = new ArrayList<String>();
        Verdict verdict;
        try {
            if (!actual.getName().startsWith("net.minecraft.")) {
                verdict =
                        new Verdict(
                                false, "Producer is outside the current vanilla stage", evidence);
                cache.put(actual, verdict);
                com.mojang.logging.LogUtils.getLogger()
                        .info(
                                "TAKEOVER_SOURCE source={} route=CAPTURE reason={}",
                                actual.getName(),
                                verdict.reason);
                return false;
            }
            var trail =
                    Launcher.INSTANCE
                            .environment()
                            .getProperty(IEnvironment.Keys.AUDITTRAIL.get())
                            .orElseThrow(
                                    () -> new IllegalStateException("No ModLauncher class audit"));
            verdict =
                    new Verdict(
                            true,
                            hierarchy
                                    ? "loaded producer hierarchy verified"
                                    : "loaded algorithm declaration verified",
                            evidence);
            for (var type = actual;
                    type != null && !type.getName().startsWith("java.");
                    type = hierarchy ? type.getSuperclass() : null) {
                var activities = trail.getActivityFor(type.getName());
                if (activities.isEmpty()) {
                    verdict =
                            new Verdict(
                                    false, "No loaded producer audit: " + type.getName(), evidence);
                    break;
                }
                for (var activity : activities) {
                    evidence.add(type.getName() + ":" + activity.getActivityString());
                    var reason = rejection(activity.getType().name(), activity.getContext());
                    if (reason != null) {
                        verdict = new Verdict(false, type.getName() + ":" + reason, evidence);
                        break;
                    }
                }
                if (!verdict.compatible) break;
            }
            if (verdict.compatible) verdict = new Verdict(true, verdict.reason, evidence);
        } catch (LinkageError | RuntimeException failure) {
            verdict = new Verdict(false, "Loaded producer audit unavailable: " + failure, evidence);
        }
        cache.put(actual, verdict);
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "TAKEOVER_SOURCE source={} route={} reason={} evidence={}",
                        (hierarchy ? "" : "declared/") + actual.getName(),
                        verdict.compatible
                                ? (hierarchy ? "DIRECT_ELIGIBLE" : "DECLARATION_VERIFIED")
                                : "CAPTURE",
                        verdict.reason,
                        verdict.evidence);
        return verdict.compatible;
    }

    private static Verdict inspect(Source source) {
        String selection = System.getProperty(source.property);
        String selectionReason =
                System.getProperty(
                        source.property + ".reason", "source Mixin selection not proven");
        if (!Set.of("independent", "modified").contains(selection == null ? "" : selection)
                || selectionReason.equals("Required source adapter not selected"))
            return new Verdict(false, selectionReason, List.of());
        var evidence = new ArrayList<String>();
        try {
            var trail =
                    Launcher.INSTANCE
                            .environment()
                            .getProperty(IEnvironment.Keys.AUDITTRAIL.get())
                            .orElseThrow(
                                    () -> new IllegalStateException("No ModLauncher class audit"));
            for (String name : source.classes) {
                // false avoids running a helper's static initialization merely to inspect it.
                Class<?> actual =
                        Class.forName(name, false, SourceCompatibility.class.getClassLoader());
                var activities = trail.getActivityFor(actual.getName());
                if (activities.isEmpty())
                    return new Verdict(false, "No loaded-class audit: " + name, evidence);
                for (var activity : activities) {
                    evidence.add(name + ":" + activity.getActivityString());
                    String reason = rejection(activity.getType().name(), activity.getContext());
                    if (reason != null) return new Verdict(false, name + ": " + reason, evidence);
                }
            }
            return new Verdict(true, "loaded-class transformations verified", evidence);
        } catch (LinkageError | RuntimeException | ClassNotFoundException failure) {
            return new Verdict(false, "Loaded-class audit unavailable: " + failure, evidence);
        }
    }

    static String rejection(String type, String[] context) {
        if (type.equals(ITransformerActivity.Type.REASON.name())) return null;
        if (context.length == 0) return "Unattributed bytecode transformation";
        if (!type.equals(ITransformerActivity.Type.PLUGIN.name()))
            return "Unregistered transformer " + String.join(":", context);
        return switch (context[0]) {
            // These loader plugins alter access/dist membership, not source geometry evaluation.
            // Their supported client behavior is covered by the ordinary reference comparison.
            case "accesstransformer", "runtimedistcleaner" -> null;
            case "mixin" -> {
                if (context.length == 2 && Set.of("A", "B").contains(context[1])) yield null;
                if (context.length == 3
                        && context[1].equals("APP")
                        && (context[2].startsWith("radiance.mixins.json:")
                                || context[2].startsWith("com.radiance.mixins.")
                                || com.radiance.api.render.ReviewedTransformations.accepts(
                                        context[2]))) yield null;
                yield "Unregistered Mixin activity " + String.join(":", context);
            }
            default -> "Unregistered launch plugin " + String.join(":", context);
        };
    }
}
