package com.radiance.audit.mixin;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuditMixinPluginTest {
    @Test void explicitScenarioRetainsAccessorWithoutEnablingChunkObservers() {
        AuditMixinPlugin ordinary = new AuditMixinPlugin(name -> true, () -> false);
        AuditMixinPlugin scenario = new AuditMixinPlugin(name -> true, () -> true);
        String target = "net.minecraft.client.renderer.LevelRenderer";
        String prefix = "com.radiance.audit.mixin.";
        assertTrue(scenario.shouldApplyMixin(target, prefix + "LevelRendererAccessor"));
        assertEquals(ordinary.shouldApplyMixin(target, prefix + "LevelRendererAuditMixin"),
            scenario.shouldApplyMixin(target, prefix + "LevelRendererAuditMixin"));
        assertFalse(new AuditMixinPlugin(name -> false, () -> true)
            .shouldApplyMixin(target, prefix + "LevelRendererAccessor"));
    }
    @Test void missingRendererSelectsOnlyVanillaClientMixinBeforeTransformation() {
        AuditMixinPlugin plugin = new AuditMixinPlugin(name -> name.startsWith("net.minecraft."));
        assertFalse(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.radiance.audit.mixin.MinecraftAuditMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.radiance.audit.mixin.VanillaMinecraftAuditMixin"));
        assertFalse(plugin.shouldApplyMixin("com.radiance.client.proxy.vulkan.ShaderProxy", "com.radiance.audit.mixin.ShaderProxyAuditMixin"));
    }
    @Test void installedRendererSelectsOnlyRadianceClientMixin() {
        AuditMixinPlugin plugin = new AuditMixinPlugin(name -> true);
        assertTrue(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.radiance.audit.mixin.MinecraftAuditMixin"));
        assertFalse(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.radiance.audit.mixin.VanillaMinecraftAuditMixin"));
    }
}
