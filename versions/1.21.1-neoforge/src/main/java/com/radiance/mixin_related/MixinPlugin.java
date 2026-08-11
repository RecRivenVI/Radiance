package com.radiance.mixin_related;

import com.radiance.compatibility.veil.VeilAdapter;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {

    public static boolean ENABLED = true;
    public static final String PAINTING_SOURCE_PROPERTY = "radiance.internal.paintingSource";

    @Override
    public void onLoad(String mixinPackage) {
        if (com.radiance.compatibility.voxy.VoxyPresence.PRESENT)
            com.radiance.compatibility.voxy.VoxyMixinBootstrap.register();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compatibility.voxy."))
            return ENABLED && com.radiance.compatibility.voxy.VoxyPresence.PRESENT;
        if (mixinClassName.contains(".compatibility.simulated.SimulatedGroup")
                || mixinClassName.endsWith(".SimulatedSimpleSubLevelGroupMixins")) {
            ClassLoader loader = getClass().getClassLoader();
            return ENABLED
                    && loader.getResource(
                                    "dev/simulated_team/simulated/util/SimpleSubLevelGroupRenderer.class")
                            != null
                    && loader.getResource("dev/ryanhcode/sable/Sable.class") != null
                    && loader.getResource("dev/engine_room/flywheel/api/internal/FlwApiLink.class")
                            != null;
        }
        if (mixinClassName.endsWith(".VeilWorldBlockLayersMixins")) {
            return ENABLED
                    && getClass()
                                    .getClassLoader()
                                    .getResource(
                                            "foundry/veil/ext/LevelRendererBlockLayerExtension.class")
                            != null;
        }
        if (mixinClassName.endsWith(".SableFlywheelEmbeddingMixins")) {
            ClassLoader loader = getClass().getClassLoader();
            return ENABLED
                    && loader.getResource(
                                    "dev/ryanhcode/sable/neoforge/mixinterface/compatibility/flywheel/EmbeddedEnvironmentExtension.class")
                            != null
                    && loader.getResource("dev/engine_room/flywheel/api/internal/FlwApiLink.class")
                            != null;
        }
        return ENABLED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        Set<String> sources =
                Set.of(
                        "net.minecraft.client.renderer.entity.PaintingRenderer",
                        "net.minecraft.client.renderer.texture.TextureAtlasSprite");
        boolean independent =
                sources.stream().allMatch(s -> containsTarget(myTargets, s))
                        && sources.stream().noneMatch(s -> containsTarget(otherTargets, s));
        // Plugin and GAME classes may be in different module layers. Publish only eligibility,
        // before target application; do not depend on an optional runtime ClassInfo cache.
        System.setProperty(
                PAINTING_SOURCE_PROPERTY,
                independent && !"modified".equals(System.getProperty(PAINTING_SOURCE_PROPERTY))
                        ? "independent"
                        : "modified");
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
            IMixinInfo mixinInfo) {
        VeilAdapter.postApplyMixin(targetClassName, targetClass);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }
}
