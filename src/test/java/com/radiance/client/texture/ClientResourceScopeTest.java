package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

final class ClientResourceScopeTest {
    @Test
    void identicalPngLocationIsMarkedOnlyForClientResources() {
        ResourceLocation shared = ResourceLocation.fromNamespaceAndPath(
            "radiance", "shared/same-name.png");

        assertTrue(ClientResourceScope.isClientTexture(PackType.CLIENT_RESOURCES, shared));
        assertFalse(ClientResourceScope.isClientTexture(PackType.SERVER_DATA, shared));
    }

    @Test
    void nonTextureResourcesRetainTheOrdinaryResourcePath() {
        ResourceLocation json = ResourceLocation.fromNamespaceAndPath(
            "radiance", "textures/not-an-image.json");

        assertFalse(ClientResourceScope.isClientTexture(PackType.CLIENT_RESOURCES, json));
        assertFalse(ClientResourceScope.isClientTexture(PackType.SERVER_DATA, json));
    }

    @Test
    void onlyThePhysicalClientManagerRoutesVulkanReloadWork() {
        Object clientManager = new Object();
        Object serverDataManager = new Object();
        AtomicInteger reloads = new AtomicInteger();

        ClientResourceScope.runIfClientManager(clientManager, serverDataManager,
            reloads::incrementAndGet);
        ClientResourceScope.runIfClientManager(clientManager, clientManager,
            reloads::incrementAndGet);

        assertEquals(1, reloads.get());
    }

    @Test
    void clientResourceWrapperPreservesResourceAndStreamOwnership() throws IOException {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
            PackResources.class.getClassLoader(), new Class<?>[] {PackResources.class},
            (proxy, method, arguments) -> method.getReturnType().isPrimitive()
                ? primitiveDefault(method.getReturnType()) : null);
        CloseTrackingStream source = new CloseTrackingStream(new byte[] {4, 5, 6});
        Resource original = new Resource(pack, () -> source, ResourceMetadata.EMPTY_SUPPLIER);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
            "radiance", "textures/block/test.png");

        Resource wrapped = ClientResourceScope.wrapClientTexture(
            PackType.CLIENT_RESOURCES, id, original);
        assertNotSame(original, wrapped);
        assertSame(pack, wrapped.source());
        assertSame(ResourceMetadata.EMPTY, wrapped.metadata());
        try (var stream = wrapped.open()) {
            assertInstanceOf(IdentifierInputStream.class, stream);
            assertEquals(id, ((IdentifierInputStream) stream).getResourceId());
            assertEquals(4, stream.read());
        }
        assertEquals(1, source.closeCount);

        assertSame(original, ClientResourceScope.wrapClientTexture(
            PackType.SERVER_DATA, id, original));
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        throw new AssertionError("Unexpected primitive " + type);
    }

    private static final class CloseTrackingStream extends ByteArrayInputStream {
        private int closeCount;

        private CloseTrackingStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
        }
    }
}
