package com.radiance.client.texture;

import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.PackType;

/** Client-resource ownership checks shared by texture marking and reload routing. */
public final class ClientResourceScope {
    private ClientResourceScope() {
    }

    public static boolean isClientTexture(PackType type, ResourceLocation id) {
        return type == PackType.CLIENT_RESOURCES && id != null
            && id.getPath().endsWith(".png");
    }

    public static Resource wrapClientTexture(PackType type, ResourceLocation id,
        Resource resource) {
        if (!isClientTexture(type, id)) return resource;
        return new Resource(resource.source(), () -> {
            InputStream stream = resource.open();
            return stream instanceof IdentifierInputStream
                ? stream
                : new IdentifierInputStream(stream, id);
        }, resource::metadata);
    }

    public static boolean isClientManager(Object clientResourceManager, Object candidate) {
        return clientResourceManager != null && clientResourceManager == candidate;
    }

    public static void runIfClientManager(Object clientResourceManager, Object candidate,
        Runnable action) {
        if (isClientManager(clientResourceManager, candidate)) action.run();
    }
}
