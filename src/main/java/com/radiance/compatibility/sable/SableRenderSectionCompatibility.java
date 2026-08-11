package com.radiance.compatibility.sable;

import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * Owns the negative render-section index contract used by Sable sublevels.
 */
public final class SableRenderSectionCompatibility {

    private SableRenderSectionCompatibility() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object collectConstructorStream(int index, Stream<?> stream,
        Collector<?, ?, ?> collector) {
        return stream.collect((Collector) collector);
    }

    public static boolean handleDirty(SectionRenderDispatcher.RenderSection section) {
        if (section.index >= 0) {
            return false;
        }
        SableSubLevelBridge.markDirty(section);
        return true;
    }

    public static boolean isExternal(SectionRenderDispatcher.RenderSection section) {
        return section.index < 0;
    }
}
