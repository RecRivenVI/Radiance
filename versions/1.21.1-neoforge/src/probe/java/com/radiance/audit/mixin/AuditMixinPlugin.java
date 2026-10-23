package com.radiance.audit.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AuditMixinPlugin implements IMixinConfigPlugin {
    private final java.util.function.Predicate<String> available;
    private final java.util.function.BooleanSupplier experiments;

    public AuditMixinPlugin() {
        this(AuditMixinPlugin::resourceExists);
    }

    AuditMixinPlugin(java.util.function.Predicate<String> available) {
        this(available, com.radiance.audit.ExperimentAccess::permitted);
    }

    AuditMixinPlugin(
            java.util.function.Predicate<String> available,
            java.util.function.BooleanSupplier experiments) {
        this.available = available;
        this.experiments = experiments;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!com.radiance.audit.AuditConfiguration.enabled()) return false;
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (name.startsWith("InstanceCensusMixins$")
                && !Boolean.getBoolean("radiance.audit.instanceCensus")) return false;
        if (name.equals("ReplacementInputComparisonMixin")
                && !Boolean.getBoolean("radiance.audit.rendererReplacement")) return false;
        // These observers modify ModelPart/ItemRenderer/ModelBlockRenderer and correctly make
        // the product reject direct takeover. Performance attribution must retain normal inputs.
        if (Boolean.getBoolean("radiance.audit.vanillaPerformance")
                && (name.startsWith("ProfileProducer")
                        || Set.of("LevelRendererAccessor", "ProfileLevelSetupMixin")
                                .contains(name))) return false;
        if (Boolean.getBoolean("radiance.audit.drawingCensus") && name.startsWith("Profile"))
            return false;
        if (Boolean.getBoolean("radiance.audit.drawingCensus")
                && name.equals("LevelRendererAccessor")) return false;
        if (name.equals("TakeoverParticleComparisonMixin")
                && !Boolean.getBoolean("radiance.audit.particleVerify")) return false;
        boolean radiance = classExists("com.radiance.client.proxy.vulkan.RendererProxy");
        if (name.equals("UnregisteredPartMutationMixin")
                && (!radiance || !Boolean.getBoolean("radiance.audit.unregisteredSource")))
            return false;
        if (name.equals("ProfileProducerModelMixin")
                && Boolean.getBoolean("radiance.audit.entityTakeover")) return false;
        if (name.equals("TakeoverPartComparisonMixin")
                && (!radiance
                        || !Boolean.getBoolean("radiance.audit.entityTakeover")
                        || !Boolean.getBoolean("radiance.audit.partVerify"))) return false;
        // This sample has only FRAME_ONLY/TIMED windows. Do not weave dormant producer
        // census wrappers into the source methods whose independence it is validating.
        if ((Boolean.getBoolean("radiance.audit.takeoverStages")
                        || Boolean.getBoolean("radiance.audit.entityTakeover"))
                && Set.of("ProfileProducerItemMixin", "ProfileProducerBakedMixin").contains(name))
            return false;
        if (name.equals("TakeoverBakedComparisonMixin")
                && (!radiance
                        || !Boolean.getBoolean("radiance.audit.takeoverCompare")
                        || !classExists("com.radiance.client.vertex.BakedModelTakeover")))
            return false;
        if (name.equals("TakeoverFlywheelSourceMixin")
                && (!radiance
                        || !classExists("dev.engine_room.flywheel.api.backend.Engine")
                        || !"1"
                                .equals(
                                        com.radiance.audit.ExperimentAccess.getenv(
                                                "RADIANCE_TAKEOVER_SOURCES")))) return false;
        if (Set.of(
                                "FontProviderLifetimeMixin",
                                "FontImageLifetimeMixin",
                                "FontGlyphLifetimeMixin",
                                "FontLookupLifetimeMixin")
                        .contains(name)
                && (!radiance || !Boolean.getBoolean("radiance.audit.lines"))) return false;
        if (name.startsWith("Profile") && !radiance) return false;
        if (name.equals("MinecraftAuditMixin") && !radiance) return false;
        if (name.equals("VanillaMinecraftAuditMixin") && radiance) return false;
        if (name.equals("SpringMenuSamplerMixin")
                && !"1"
                        .equals(
                                com.radiance.audit.ExperimentAccess.getenv(
                                        "RADIANCE_SPRING_MENU_PROBE"))) return false;
        if (name.equals("SpringTwistFixtureMixin")
                && com.radiance.audit.ExperimentAccess.getenv("RADIANCE_SPRING_TWIST_STEP") == null)
            return false;
        if (name.equals("SpringSourceCaptureMixin")
                && !"1"
                        .equals(
                                com.radiance.audit.ExperimentAccess.getenv(
                                        "RADIANCE_SPRING_SOURCE_CAPTURE"))) return false;
        if (java.util.Set.of("SpringMenuDrawStateMixin", "SpringMenuMeshMixin").contains(name)
                && (!radiance
                        || !"1"
                                .equals(
                                        com.radiance.audit.ExperimentAccess.getenv(
                                                "RADIANCE_SPRING_MENU_PROBE")))) return false;
        if (java.util.Set.of(
                                "SpringWorldSegmentMixin",
                                "SpringWorldLoweringMixin",
                                "SpringWorldPbrEndMixin")
                        .contains(name)
                && (!radiance
                        || !"1"
                                .equals(
                                        com.radiance.audit.ExperimentAccess.getenv(
                                                "RADIANCE_SPRING_DIRECT_WORLD")))) return false;
        if (java.util.Set.of(
                                "RenderTypeAuditMixin",
                                "BufferUploaderAuditMixin",
                                "VertexBufferAuditMixin",
                                "ShaderProxyAuditMixin")
                        .contains(name)
                && !com.radiance.audit.AuditHooks.accepts("BUFFER_DRAW")) return false;
        if (name.contains("ScreenEffect")
                && !com.radiance.audit.AuditHooks.accepts("SCREEN_EFFECT")) return false;
        // Explicit scenario drivers read this accessor even when chunk logging is disabled.
        // Do not enable the high-volume observer mixins merely to satisfy that dependency.
        if (name.equals("LevelRendererAccessor") && experiments.getAsBoolean())
            return classExists(targetClassName);
        if (java.util.Set.of(
                                "ChunkProxyAuditMixin",
                                "RenderSectionAuditMixin",
                                "LevelRendererAuditMixin",
                                "LevelRendererAccessor")
                        .contains(name)
                && !com.radiance.audit.AuditHooks.accepts("CHUNK_BUILD")) return false;
        if (java.util.Set.of(
                                "CatnipDepthAuditMixin",
                                "WorldMeshSinkAuditMixin",
                                "RendererLifecycleAuditMixin")
                        .contains(name)
                && !experiments.getAsBoolean()) return false;
        return classExists(targetClassName);
    }

    private boolean classExists(String className) {
        return available.test(className);
    }

    private static boolean resourceExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        return AuditMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

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
}
