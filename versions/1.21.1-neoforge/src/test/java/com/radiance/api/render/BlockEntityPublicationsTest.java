package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.texture.FontAtlasLifecycle;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.junit.jupiter.api.*;

class BlockEntityPublicationsTest {
    private static final class Storage extends StorageVertexConsumerProvider
            implements AutoCloseable {
        Storage() {
            super(128);
        }
    }

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.neoforged.fml.loading.LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void clear() {
        BlockEntityPublications.clear();
        PersistentInstances.invalidate();
    }

    private static EntityProxy.EntityRenderData data() {
        return new EntityProxy.EntityRenderData(1, 0, 0, 0, 1, 0, -1, false);
    }

    private static SignBlockEntity owner() {
        return new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());
    }

    @Test
    void onlyExplicitHoldsKeepRemovedEmptyPublicationsAlive() {
        var entity = owner();
        try (var storage = new Storage()) {
            BlockEntityPublications.beginFrame();
            assertFalse(
                    BlockEntityPublications.capture(entity, storage, List.of(), data(), data()));
            assertTrue(BlockEntityPublications.removed(entity));
            var hold = BlockEntityPublications.retainSource(null, null, () -> true);
            BlockEntityPublications.endFrame();
            assertEquals(1, BlockEntityPublications.counters()[0]);
            assertTrue(hold.ready());
            hold.cancel();
            BlockEntityPublications.endFrame();
            assertEquals(0, BlockEntityPublications.counters()[0]);
            assertFalse(BlockEntityPublications.removed(entity));
        }
    }

    @Test
    void unseenUnheldOwnersDoNotKeepTheirCaptureStorage() {
        var entity = owner();
        try (var storage = new Storage()) {
            BlockEntityPublications.beginFrame();
            BlockEntityPublications.capture(entity, storage, List.of(), data(), data());
            assertFalse(BlockEntityPublications.owns(storage));
            BlockEntityPublications.beginFrame();
            BlockEntityPublications.endFrame();
            assertEquals(0, BlockEntityPublications.counters()[0]);
        }
    }

    @Test
    void fontRetirementRemovesBuiltGlyphPublicationBeforeTextureNameReuse() {
        var id = ResourceLocation.withDefaultNamespace("publication_font/0");
        var page =
                new AbstractTexture() {
                    public void load(ResourceManager resources) {}
                };
        FontAtlasLifecycle.registerPage(id, page);
        var type = RenderType.text(id);
        try (var allocator = new ByteBufferBuilder(512);
                var storage = new Storage()) {
            var builder = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, type.format());
            for (int i = 0; i < 4; i++)
                builder.addVertex(i & 1, i >> 1, 0).setColor(-1).setUv(0, 0).setLight(0);
            var mesh = builder.buildOrThrow();
            var body = data();
            body.add(new EntityProxy.EntityRenderLayer(type, mesh, false, "font"));
            BlockEntityPublications.beginFrame();
            BlockEntityPublications.capture(owner(), storage, List.of(), body, data());
            assertTrue(BlockEntityPublications.owns(mesh));
            FontAtlasLifecycle.beforeClose(page);
            assertTrue(body.isEmpty());
            assertFalse(BlockEntityPublications.owns(mesh));
            // Repeated callbacks and final owner cleanup must not double-close the old mesh.
            FontAtlasLifecycle.beforeClose(page);
            BlockEntityPublications.clear();
        }
    }
}
