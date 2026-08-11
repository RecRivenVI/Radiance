package com.radiance.audit;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded provenance for bitmap storage in the isolated functional regression only. */
public final class FontSourceProbe {
    private static final Map<NativeImage, String> SOURCES = new WeakHashMap<>();
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.lines")
                    || Boolean.getBoolean("radiance.audit.cityComparison");
    private static int reports;
    private static int violations;

    private FontSourceProbe() {}

    public static synchronized void register(NativeImage image) {
        if (ENABLED && SOURCES.size() < 512) SOURCES.put(image, "open");
    }

    public static synchronized void closing(NativeImage image) {
        if (!ENABLED || !SOURCES.containsKey(image) || !SOURCES.get(image).equals("open")) return;
        var trace =
                StackWalker.getInstance()
                        .walk(s -> s.limit(24).map(Object::toString).toList())
                        .toString();
        SOURCES.put(image, trace);
        if (reports++ < 48)
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "FONT_SOURCE closing image={} caller={}",
                            System.identityHashCode(image),
                            trace);
    }

    public static synchronized void baking(NativeImage image) {
        if (!ENABLED) return;
        long pointer =
                ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                                (Object) image)
                        .radiance$getPointer();
        if (pointer == 0) {
            violations++;
            if (reports++ < 64)
                com.mojang.logging.LogUtils.getLogger()
                        .error(
                                "FONT_SOURCE baking-closed image={} closedBy={}",
                                System.identityHashCode(image),
                                SOURCES.get(image));
        }
    }

    public static synchronized void staleLookup() {
        violations++;
    }

    public static synchronized int violations() {
        return violations;
    }
}
