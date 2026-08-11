package com.radiance.mixin_related;

import com.radiance.compatibility.veil.VeilAdapter;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {

    public static boolean ENABLED = true;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compatibility.simulated.SimulatedGroup")
            || mixinClassName.endsWith(".SimulatedSimpleSubLevelGroupMixins")) {
            ClassLoader loader = getClass().getClassLoader();
            return ENABLED && loader.getResource("dev/simulated_team/simulated/util/SimpleSubLevelGroupRenderer.class") != null
                && loader.getResource("dev/ryanhcode/sable/Sable.class") != null
                && loader.getResource("dev/engine_room/flywheel/api/internal/FlwApiLink.class") != null;
        }
        if (mixinClassName.endsWith(".VeilWorldBlockLayersMixins")) {
            return ENABLED && getClass().getClassLoader().getResource("foundry/veil/ext/LevelRendererBlockLayerExtension.class") != null;
        }
        if (mixinClassName.endsWith(".SableFlywheelEmbeddingMixins")) {
            ClassLoader loader = getClass().getClassLoader();
            return ENABLED && loader.getResource("dev/ryanhcode/sable/neoforge/mixinterface/compatibility/flywheel/EmbeddedEnvironmentExtension.class") != null
                && loader.getResource("dev/engine_room/flywheel/api/internal/FlwApiLink.class") != null;
        }
        return ENABLED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {
        VeilAdapter.postApplyMixin(targetClassName, targetClass);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }
}
