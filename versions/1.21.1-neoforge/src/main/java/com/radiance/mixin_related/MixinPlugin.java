package com.radiance.mixin_related;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {

    public static boolean ENABLED = true;
    public static final String PAINTING_SOURCE_PROPERTY = "radiance.internal.paintingSource";
    public static final String BAKED_SOURCE_PROPERTY = "radiance.internal.bakedSource";
    public static final String PART_SOURCE_PROPERTY = "radiance.internal.partSource";
    public static final String STATE_SOURCE_PROPERTY = "radiance.internal.stateSource";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ENABLED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        var parts = SourceCompatibility.Source.PARTS.classes;
        boolean partIndependent = parts.stream().noneMatch(s -> containsTarget(otherTargets, s));
        eligibility(PART_SOURCE_PROPERTY, partIndependent, parts, otherTargets);
        var baked = SourceCompatibility.Source.BAKED.classes;
        boolean bakedIndependent = baked.stream().noneMatch(s -> containsTarget(otherTargets, s));
        eligibility(BAKED_SOURCE_PROPERTY, bakedIndependent, baked, otherTargets);
        var states = SourceCompatibility.Source.STATIC_STATE.classes;
        eligibility(
                STATE_SOURCE_PROPERTY,
                states.stream().noneMatch(s -> containsTarget(otherTargets, s)),
                states,
                otherTargets);
        var sources = SourceCompatibility.Source.PAINTING.classes;
        boolean independent =
                sources.stream().allMatch(s -> containsTarget(myTargets, s))
                        && sources.stream().noneMatch(s -> containsTarget(otherTargets, s));
        // Plugin and GAME classes may be in different module layers. Publish only eligibility,
        // before target application; do not depend on an optional runtime ClassInfo cache.
        eligibility(PAINTING_SOURCE_PROPERTY, independent, sources, otherTargets);
    }

    private static void eligibility(
            String property,
            boolean independent,
            java.util.Collection<String> sources,
            Set<String> otherTargets) {
        if ("modified".equals(System.getProperty(property))) return;
        System.setProperty(property, independent ? "independent" : "modified");
        if (!independent) {
            var changed =
                    sources.stream().filter(s -> containsTarget(otherTargets, s)).sorted().toList();
            System.setProperty(
                    property + ".reason",
                    changed.isEmpty()
                            ? "Required source adapter not selected"
                            : "Unregistered Mixin targets: " + changed);
        }
    }

    private static boolean containsTarget(Set<String> targets, String target) {
        return targets.contains(target) || targets.contains(target.replace('.', '/'));
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }
}
