package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.mixins.vanilla_resource_tracker.FontStorageMixins;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class FontAtlasLifecycleTest {
    private static Thread previousRenderThread;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.server.Bootstrap.bootStrap();
        var field = RenderSystem.class.getDeclaredField("renderThread");
        field.setAccessible(true);
        previousRenderThread = (Thread) field.get(null);
        field.set(null, Thread.currentThread());
    }

    @AfterAll
    static void restoreRenderThread() throws Exception {
        var field = RenderSystem.class.getDeclaredField("renderThread");
        field.setAccessible(true);
        field.set(null, previousRenderThread);
    }

    @Test
    void nonFontSourcesNeverRegisterEvenWhenTheyRemainLive() throws Exception {
        int initial = registeredOwners().size();
        var sources = new ArrayList<Batches>();
        try {
            for (int i = 0; i < 2048; i++) {
                var source = new Batches();
                sources.add(source);
                quad(source.getBuffer(RenderType.gui()));
            }
            assertEquals(initial, registeredOwners().size());
            assertEquals(2048, sources.size());
        } finally {
            sources.forEach(Batches::close);
        }
    }

    @Test
    void registrationFollowsPendingFontPagesAcrossFlushRetireAndReuse() throws Exception {
        int initial = registeredOwners().size();
        var manager = new TextureFixture();
        var a = ResourceLocation.withDefaultNamespace("fixture_registration_a/0");
        var b = ResourceLocation.withDefaultNamespace("fixture_registration_b/0");
        var pageA = new Page();
        var pageB = new Page();
        manager.register(a, pageA);
        manager.register(b, pageB);
        var typeA = RenderType.text(a);
        var typeB = RenderType.text(b);
        try (var source = new Batches(typeA, typeB)) {
            assertFalse(registeredOwners().containsKey(source));
            for (int i = 0; i < 50; i++) quad(source.getBuffer(typeA));
            quad(source.getBuffer(typeB));
            assertTrue(registeredOwners().containsKey(source));
            assertEquals(initial + 1, registeredOwners().size());
            assertEquals(2, source.pages.size());
            pageA.close();
            assertFalse(source.has(typeA));
            assertTrue(source.has(typeB));
            assertTrue(registeredOwners().containsKey(source));
            source.finish(typeB);
            assertFalse(registeredOwners().containsKey(source));
            quad(source.getBuffer(typeB));
            assertTrue(registeredOwners().containsKey(source));
            pageB.close();
            assertFalse(source.has(typeB));
            assertFalse(registeredOwners().containsKey(source));
        } finally {
            manager.release(a);
            manager.release(b);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<FontAtlasLifecycle.BatchOwner, Boolean> registeredOwners() throws Exception {
        var field = FontAtlasLifecycle.class.getDeclaredField("batchOwners");
        field.setAccessible(true);
        return (Map<FontAtlasLifecycle.BatchOwner, Boolean>) field.get(null);
    }

    @Test
    void oldCloseLeavesARegisteredPageThatAllocatesUninitializedStorageOnUse() {
        var manager = new TextureFixture();
        var page = new Page();
        var location = ResourceLocation.withDefaultNamespace("fixture_legacy/0");
        manager.register(location, page);
        page.legacyClose();
        assertSame(page, manager.getTexture(location, null));
        assertThrows(
                IllegalStateException.class,
                () -> manager.getTexture(location, null).setFilter(false, false));
        assertEquals(1, page.emptyAllocations);
        manager.release(location);
    }

    @Test
    void actualFontCloseEntryDiscardsOldSharedBatchBeforeReleasingItsAtlas() throws Exception {
        var manager = new TextureFixture();
        var font = ResourceLocation.withDefaultNamespace("fixture_reload");
        var location = font.withSuffix("/0");
        var page = new Page();
        manager.register(location, page);
        try (var buffers = new Batches()) {
            RenderType layer = RenderType.text(location);
            quad(buffers.getBuffer(layer));
            assertSame(layer, buffers.last());
            page.beforeRelease = () -> assertFalse(buffers.has(layer));
            var pages = new ArrayList<>(List.of(page));
            closeFont(manager, font, pages);
            assertTrue(pages.isEmpty());
            assertNull(manager.getTexture(location, null));
            assertNull(buffers.last());
            assertEquals(1, page.closes);
            assertEquals(0, page.emptyAllocations);
            // This is the original failure boundary: switching types flushes lastSharedType.
            // The actual BufferSource must not attempt any draw of the retired font page.
            assertDoesNotThrow(() -> buffers.getBuffer(RenderType.gui()));
        }
    }

    @Test
    void multiplePagesRetireAcrossSourcesButOtherBatchesSurviveAndNewGlyphsWork() throws Exception {
        var manager = new TextureFixture();
        var font = ResourceLocation.withDefaultNamespace("fixture_pages");
        var a = font.withSuffix("/0");
        var b = font.withSuffix("/1");
        var oldA = new Page();
        var oldB = new Page();
        manager.register(a, oldA);
        manager.register(b, oldB);
        var typeA = RenderType.text(a);
        var typeB = RenderType.text(b);
        var unrelated = RenderType.gui();
        try (var one = new Batches(typeA, typeB, unrelated);
                var two = new Batches(typeB)) {
            quad(one.getBuffer(typeA));
            quad(one.getBuffer(typeB));
            quad(one.getBuffer(unrelated));
            quad(two.getBuffer(typeB));
            closeFont(manager, font, new ArrayList<>(List.of(oldA, oldB)));
            assertFalse(one.has(typeA));
            assertFalse(one.has(typeB));
            assertFalse(two.has(typeB));
            assertTrue(one.has(unrelated));
            assertNull(
                    manager.getTexture(b, null),
                    "an unused old atlas page must not be resurrected");
            var fresh = new Page();
            manager.register(a, fresh);
            quad(one.getBuffer(typeA));
            assertTrue(one.has(typeA));
            assertDoesNotThrow(() -> fresh.setFilter(true, false));
            assertEquals(0, fresh.emptyAllocations);
            closeFont(manager, font, new ArrayList<>(List.of(fresh)));
        }
    }

    @Test
    void delayedOldCloseDoesNotRemoveOrDiscardTheNewOwnerAtTheSameName() throws Exception {
        var manager = new TextureFixture();
        var font = ResourceLocation.withDefaultNamespace("fixture_new_owner");
        var location = font.withSuffix("/0");
        var old = new Page();
        manager.register(location, old);
        var fresh = new Page();
        try (var buffers = new Batches()) {
            var layer = RenderType.text(location);
            quad(buffers.getBuffer(layer));
            manager.register(location, fresh);
            assertFalse(buffers.has(layer), "replacement must retire the previous owner's batch");
            quad(buffers.getBuffer(layer));
            closeFont(manager, font, new ArrayList<>(List.of(old)));
            assertSame(fresh, manager.getTexture(location, null));
            assertTrue(buffers.has(layer));
            assertSame(layer, buffers.last());
            assertEquals(0, fresh.closes);
            closeFont(manager, font, new ArrayList<>(List.of(fresh)));
            assertFalse(buffers.has(layer));
        }
    }

    @Test
    void directPageCloseAlsoInvalidatesPendingGlyphsAndCloseFailureIsNotSwallowed() {
        var manager = new TextureFixture();
        var location = ResourceLocation.withDefaultNamespace("fixture_close/0");
        var page = new Page();
        manager.register(location, page);
        try (var buffers = new Batches()) {
            var layer = RenderType.text(location);
            quad(buffers.getBuffer(layer));
            page.close();
            assertFalse(buffers.has(layer));
        }
        var failure = new IllegalStateException("original close failure");
        page.beforeRelease =
                () -> {
                    throw failure;
                };
        assertSame(failure, assertThrows(IllegalStateException.class, page::close));
        page.beforeRelease = () -> {};
        manager.release(location);
    }

    @Test
    void offThreadFontCloseKeepsStorageValidUntilTheRenderQueueRetiresTheBatches()
            throws Exception {
        var manager = new TextureFixture();
        var font = ResourceLocation.withDefaultNamespace("fixture_queued");
        var location = font.withSuffix("/0");
        var page = new Page();
        manager.register(location, page);
        try (var buffers = new Batches()) {
            var layer = RenderType.text(location);
            quad(buffers.getBuffer(layer));
            var pages = new ArrayList<>(List.of(page));
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var worker =
                    new Thread(
                            () -> {
                                try {
                                    closeFont(manager, font, pages);
                                } catch (Throwable problem) {
                                    failure.set(problem);
                                }
                            });
            worker.start();
            worker.join();
            assertNull(failure.get());
            assertTrue(pages.isEmpty());
            assertTrue(buffers.has(layer));
            assertTrue(page.initialized);
            assertSame(page, manager.getTexture(location, null));
            RenderSystem.replayQueue();
            assertFalse(buffers.has(layer));
            assertNull(manager.getTexture(location, null));
            assertEquals(1, page.closes);
        }
    }

    private static void closeFont(TextureManager manager, ResourceLocation font, List<Page> pages)
            throws Exception {
        var mixin = new FontStorageMixins();
        for (var entry :
                Map.of("textureManager", manager, "name", font, "textures", pages).entrySet()) {
            var field = FontStorageMixins.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(mixin, entry.getValue());
        }
        var method =
                FontStorageMixins.class.getDeclaredMethod(
                        "radiance$retireFontPages", CallbackInfo.class);
        method.setAccessible(true);
        var callback = new CallbackInfo("closeTextures", true);
        try {
            method.invoke(mixin, callback);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw failure;
        }
        assertTrue(callback.isCancelled(), "the original close-only loop must be replaced");
    }

    private static void quad(VertexConsumer out) {
        for (int i = 0; i < 4; i++)
            out.addVertex(i & 1, i >> 1, 0).setColor(255, 255, 255, 255).setUv(0, 0).setLight(0);
    }

    private static final class Batches extends MultiBufferSource.BufferSource
            implements FontAtlasLifecycle.BatchOwner, AutoCloseable {
        final Map<RenderType, AbstractTexture> pages = new HashMap<>();

        Batches(RenderType... fixed) {
            super(new ByteBufferBuilder(256), new Object2ObjectLinkedOpenHashMap<>());
            for (var layer : fixed) fixedBuffers.put(layer, new ByteBufferBuilder(256));
        }

        @Override
        public VertexConsumer getBuffer(RenderType layer) {
            var result = super.getBuffer(layer);
            FontAtlasLifecycle.capture(this, pages, layer);
            return result;
        }

        @Override
        public void radiance$discardFontBatches(Set<AbstractTexture> retiring) {
            lastSharedType =
                    FontAtlasLifecycle.discardPending(
                            this, startedBuilders, pages, lastSharedType, retiring);
        }

        void finish(RenderType layer) {
            var builder = startedBuilders.remove(layer);
            var mesh = builder.build();
            if (mesh != null) mesh.close();
            FontAtlasLifecycle.release(this, pages, layer);
            if (layer.equals(lastSharedType)) lastSharedType = null;
        }

        boolean has(RenderType layer) {
            return startedBuilders.containsKey(layer);
        }

        RenderType last() {
            return lastSharedType;
        }

        @Override
        public void close() {
            for (var layer : List.copyOf(startedBuilders.keySet())) finish(layer);
            fixedBuffers.values().forEach(ByteBufferBuilder::close);
            sharedBuffer.close();
        }
    }

    /** Only texture allocation/storage is replaced; real BufferSource and MeshData are used. */
    private static final class Page extends AbstractTexture {
        boolean initialized = true;
        int closes, emptyAllocations;
        Runnable beforeRelease = () -> {};

        Page() {
            id = 305;
        }

        @Override
        public void load(ResourceManager manager) {}

        @Override
        public int getId() {
            if (id == -1) {
                id = 305;
                emptyAllocations++;
                initialized = false;
            }
            return id;
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            getId();
            if (!initialized) throw new IllegalStateException("no initialized sampler");
        }

        void legacyClose() {
            id = -1;
            initialized = false;
        }

        @Override
        public void releaseId() {
            id = -1;
            initialized = false;
        }

        @Override
        public void close() {
            FontAtlasLifecycle.beforeClose(this);
            beforeRelease.run();
            closes++;
            releaseId();
        }
    }

    private static final class TextureFixture extends TextureManager {
        final Map<ResourceLocation, AbstractTexture> registered = new HashMap<>();

        TextureFixture() {
            super(null);
        }

        @Override
        public void register(ResourceLocation location, AbstractTexture texture) {
            var previous = registered.put(location, texture);
            if (previous != null && previous != texture) previous.close();
            FontAtlasLifecycle.registerPage(location, texture);
        }

        @Override
        public AbstractTexture getTexture(ResourceLocation location, AbstractTexture fallback) {
            return registered.getOrDefault(location, fallback);
        }

        @Override
        public void release(ResourceLocation location) {
            var texture = registered.remove(location);
            if (texture != null) {
                texture.close();
                texture.releaseId();
            }
        }
    }
}
