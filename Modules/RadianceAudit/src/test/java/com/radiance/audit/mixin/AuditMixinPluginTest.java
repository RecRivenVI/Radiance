package com.radiance.audit.mixin;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuditMixinPluginTest {
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
