package com.radiance.audit.mixin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuditMixinPluginTest {
    @Test
    void vanillaPerformanceDoesNotModifyTakeoverSourceModels() {
        String key = "radiance.audit.vanillaPerformance";
        try {
            System.setProperty(key, "true");
            var plugin = new AuditMixinPlugin(name -> true);
            for (String source : java.util.List.of("Model", "Baked", "Item"))
                assertFalse(
                        plugin.shouldApplyMixin(
                                "net.minecraft.client.model.geom.ModelPart",
                                "com.radiance.audit.mixin.ProfileProducer" + source + "Mixin"));
            for (String observer :
                    java.util.List.of("LevelRendererAccessor", "ProfileLevelSetupMixin"))
                assertFalse(
                        plugin.shouldApplyMixin(
                                "net.minecraft.client.renderer.LevelRenderer",
                                "com.radiance.audit.mixin." + observer));
            assertTrue(
                    plugin.shouldApplyMixin(
                            "com.radiance.client.proxy.world.EntityProxy",
                            "com.radiance.audit.mixin.ProfileEntityProxyMixin"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void sourceMutationNeverLoadsInAnOrdinaryOrPerformanceRun() {
        String key = "radiance.audit.unregisteredSource";
        String previous = System.getProperty(key);
        try {
            var plugin = new AuditMixinPlugin(name -> true);
            System.clearProperty(key);
            assertFalse(
                    plugin.shouldApplyMixin(
                            "net.minecraft.client.model.geom.ModelPart$Cube",
                            "com.radiance.audit.mixin.UnregisteredPartMutationMixin"));
            System.setProperty(key, "true");
            assertTrue(
                    plugin.shouldApplyMixin(
                            "net.minecraft.client.model.geom.ModelPart$Cube",
                            "com.radiance.audit.mixin.UnregisteredPartMutationMixin"));
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    @Test
    void explicitScenarioRetainsAccessorWithoutEnablingChunkObservers() {
        AuditMixinPlugin ordinary = new AuditMixinPlugin(name -> true, () -> false);
        AuditMixinPlugin scenario = new AuditMixinPlugin(name -> true, () -> true);
        String target = "net.minecraft.client.renderer.LevelRenderer";
        String prefix = "com.radiance.audit.mixin.";
        assertTrue(scenario.shouldApplyMixin(target, prefix + "LevelRendererAccessor"));
        assertEquals(
                ordinary.shouldApplyMixin(target, prefix + "LevelRendererAuditMixin"),
                scenario.shouldApplyMixin(target, prefix + "LevelRendererAuditMixin"));
        assertFalse(
                new AuditMixinPlugin(name -> false, () -> true)
                        .shouldApplyMixin(target, prefix + "LevelRendererAccessor"));
    }

    @Test
    void missingRendererSelectsOnlyVanillaClientMixinBeforeTransformation() {
        AuditMixinPlugin plugin = new AuditMixinPlugin(name -> name.startsWith("net.minecraft."));
        assertFalse(
                plugin.shouldApplyMixin(
                        "net.minecraft.client.Minecraft",
                        "com.radiance.audit.mixin.MinecraftAuditMixin"));
        assertTrue(
                plugin.shouldApplyMixin(
                        "net.minecraft.client.Minecraft",
                        "com.radiance.audit.mixin.VanillaMinecraftAuditMixin"));
        assertFalse(
                plugin.shouldApplyMixin(
                        "com.radiance.client.proxy.vulkan.ShaderProxy",
                        "com.radiance.audit.mixin.ShaderProxyAuditMixin"));
    }

    @Test
    void installedRendererSelectsOnlyRadianceClientMixin() {
        AuditMixinPlugin plugin = new AuditMixinPlugin(name -> true);
        assertTrue(
                plugin.shouldApplyMixin(
                        "net.minecraft.client.Minecraft",
                        "com.radiance.audit.mixin.MinecraftAuditMixin"));
        assertFalse(
                plugin.shouldApplyMixin(
                        "net.minecraft.client.Minecraft",
                        "com.radiance.audit.mixin.VanillaMinecraftAuditMixin"));
    }
}
