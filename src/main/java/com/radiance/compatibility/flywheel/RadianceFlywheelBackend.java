package com.radiance.compatibility.flywheel;

import com.radiance.client.proxy.world.NativeInstancingProxy;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import net.minecraft.resources.ResourceLocation;

public final class RadianceFlywheelBackend {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        "radiance", "vulkan_instancing");
    private static Backend backend;

    private RadianceFlywheelBackend() {
    }

    public static synchronized void register() {
        if (backend != null || Backend.REGISTRY.get(ID) != null) return;
        backend = SimpleBackend.builder()
            .engineFactory(RadianceFlywheelEngine::new)
            .priority(2_000)
            .supported(RadianceFlywheelBackend::nativeSupported)
            .register(ID);
    }

    /** Preserve explicit off/custom choices while translating the two upstream GL engines. */
    public static ResourceLocation translatePreference(ResourceLocation configured) {
        if (configured != null && configured.getNamespace().equals("flywheel")
            && (configured.getPath().equals("instancing") || configured.getPath().equals("indirect"))) {
            return ID;
        }
        return configured;
    }

    public static Backend resolvePreference(Backend configured) {
        ResourceLocation original = Backend.REGISTRY.getId(configured);
        ResourceLocation translated = translatePreference(original);
        if (java.util.Objects.equals(original, translated)) return configured;
        Backend replacement = Backend.REGISTRY.get(translated);
        if (replacement == null) {
            throw new IllegalStateException("Radiance Vulkan Flywheel backend was not registered");
        }
        return replacement;
    }

    private static boolean nativeSupported() {
        try {
            return NativeInstancingProxy.isSupported();
        } catch (LinkageError | RuntimeException unavailable) {
            return false;
        }
    }
}
