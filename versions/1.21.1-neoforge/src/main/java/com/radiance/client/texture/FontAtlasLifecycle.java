package com.radiance.client.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/** Retires CPU font batches before their atlas registrations and native owners are released. */
public final class FontAtlasLifecycle {
    public interface BatchOwner {
        void radiance$discardFontBatches(Set<AbstractTexture> pages);
    }

    private static final Map<BatchOwner, Boolean> batchOwners = new WeakHashMap<>();
    private static final Map<ResourceLocation, AbstractTexture> registeredPages = new HashMap<>();
    private static long revision;

    public static long revision() {
        return revision;
    }

    private FontAtlasLifecycle() {}

    private static void register(BatchOwner owner) {
        synchronized (batchOwners) {
            batchOwners.put(owner, Boolean.TRUE);
        }
    }

    public static void registerPage(ResourceLocation location, AbstractTexture texture) {
        registeredPages.put(location, texture);
    }

    public static void capture(
            BatchOwner owner, Map<RenderType, AbstractTexture> owners, RenderType layer) {
        if (owners.containsKey(layer)) return;
        if (layer instanceof RenderType.CompositeRenderType composite) {
            var location = composite.state.textureState.cutoutTexture().orElse(null);
            AbstractTexture page = registeredPages.get(location);
            if (page != null) {
                // Most BufferSources never hold glyphs. Do not retain their weak keys until GC.
                if (owners.isEmpty()) register(owner);
                owners.put(layer, page);
            }
        }
    }

    public static void release(
            BatchOwner owner, Map<RenderType, AbstractTexture> owners, RenderType layer) {
        if (owners.remove(layer) != null && owners.isEmpty()) unregister(owner);
    }

    private static void unregister(BatchOwner owner) {
        synchronized (batchOwners) {
            batchOwners.remove(owner);
        }
    }

    public static void beforeClose(AbstractTexture texture) {
        Set<AbstractTexture> pages = Collections.newSetFromMap(new IdentityHashMap<>());
        pages.add(texture);
        if (registeredPages.containsValue(texture)) ++revision;
        discardBatches(pages);
        registeredPages.values().removeIf(page -> page == texture);
    }

    public static void retire(
            TextureManager manager,
            ResourceLocation font,
            List<? extends AbstractTexture> textures) {
        if (textures.isEmpty()) return;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            var retiring = List.copyOf(textures);
            RenderSystem.recordRenderCall(() -> retire(manager, font, retiring));
            return;
        }
        Set<AbstractTexture> pages = Collections.newSetFromMap(new IdentityHashMap<>());
        ++revision;
        pages.addAll(textures);
        discardBatches(pages);
        registeredPages.values().removeIf(pages::contains);
        for (int i = 0; i < textures.size(); i++) {
            AbstractTexture texture = textures.get(i);
            ResourceLocation location = font.withSuffix("/" + i);
            if (manager.getTexture(location, null) == texture) {
                manager.release(location);
            } else {
                // A later registration owns the name; only close this old object's owner.
                texture.close();
            }
        }
    }

    private static void discardBatches(Set<AbstractTexture> pages) {
        List<BatchOwner> owners;
        synchronized (batchOwners) {
            owners = new ArrayList<>(batchOwners.keySet());
        }
        // These CPU vertices contain UVs for the old pages, not the replacement atlas.
        // Do not submit them outside a frame or let name reuse retarget them to new glyphs.
        for (BatchOwner owner : owners) owner.radiance$discardFontBatches(pages);
    }

    public static RenderType discardPending(
            BatchOwner owner,
            Map<RenderType, BufferBuilder> pending,
            Map<RenderType, AbstractTexture> owners,
            RenderType lastSharedType,
            Set<AbstractTexture> pages) {
        var entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!pages.contains(owners.get(entry.getKey()))) continue;
            var mesh = entry.getValue().build();
            if (mesh != null) mesh.close();
            owners.remove(entry.getKey());
            if (entry.getKey().equals(lastSharedType)) lastSharedType = null;
            entries.remove();
        }
        if (owners.isEmpty()) unregister(owner);
        return lastSharedType;
    }
}
