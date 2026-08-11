package com.radiance.client.texture;

import java.io.FilterInputStream;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;

public final class IdentifierInputStream extends FilterInputStream {

    private final ResourceLocation resourceId;
    public IdentifierInputStream(InputStream originalStream, ResourceLocation id) {
        super(originalStream);
        this.resourceId = id;
    }

    public ResourceLocation getResourceId() {
        return this.resourceId;
    }

}
