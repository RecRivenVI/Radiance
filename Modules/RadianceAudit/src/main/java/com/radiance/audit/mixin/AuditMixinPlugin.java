package com.radiance.audit.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AuditMixinPlugin implements IMixinConfigPlugin {
    private final java.util.function.Predicate<String> available;
    private final java.util.function.BooleanSupplier experiments;
    public AuditMixinPlugin() { this(AuditMixinPlugin::resourceExists); }
    AuditMixinPlugin(java.util.function.Predicate<String> available) {
        this(available, com.radiance.audit.ExperimentAccess::permitted);
    }
    AuditMixinPlugin(java.util.function.Predicate<String> available,
        java.util.function.BooleanSupplier experiments) {
        this.available = available;
        this.experiments = experiments;
    }
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!com.radiance.audit.AuditConfiguration.enabled()) return false;
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        boolean radiance = classExists("com.radiance.client.proxy.vulkan.RendererProxy");
        if (name.startsWith("Profile") && !radiance) return false;
        if (name.equals("MinecraftAuditMixin") && !radiance) return false;
        if (name.equals("VanillaMinecraftAuditMixin") && radiance) return false;
        if (java.util.Set.of("RenderTypeAuditMixin", "BufferUploaderAuditMixin", "VertexBufferAuditMixin",
                "ShaderProxyAuditMixin").contains(name)
                && !com.radiance.audit.AuditHooks.accepts("BUFFER_DRAW")) return false;
        if (name.contains("ScreenEffect") && !com.radiance.audit.AuditHooks.accepts("SCREEN_EFFECT")) return false;
        // Explicit scenario drivers read this accessor even when chunk logging is disabled.
        // Do not enable the high-volume observer mixins merely to satisfy that dependency.
        if (name.equals("LevelRendererAccessor") && experiments.getAsBoolean())
            return classExists(targetClassName);
        if (java.util.Set.of("ChunkProxyAuditMixin", "RenderSectionAuditMixin", "LevelRendererAuditMixin",
                "LevelRendererAccessor").contains(name)
                && !com.radiance.audit.AuditHooks.accepts("CHUNK_BUILD")) return false;
        if (java.util.Set.of("CatnipDepthAuditMixin", "WorldMeshSinkAuditMixin", "RendererLifecycleAuditMixin").contains(name)
                && !experiments.getAsBoolean()) return false;
        return classExists(targetClassName);
    }

    private boolean classExists(String className) { return available.test(className); }
    private static boolean resourceExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        return AuditMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
        String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
        String mixinClassName, IMixinInfo mixinInfo) { }
}
