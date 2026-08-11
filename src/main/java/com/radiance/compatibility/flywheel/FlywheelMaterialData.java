package com.radiance.compatibility.flywheel;

import com.radiance.client.texture.TextureTracker;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.backend.BackendConfig;
import java.util.StringJoiner;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

record FlywheelMaterialData(int textureId, int alphaMode, int flags, String key) {
    private static final int USE_OVERLAY = 1;
    private static final int USE_LIGHT = 1 << 1;
    private static final int BACKFACE_CULL = 1 << 2;
    private static final int POLYGON_OFFSET = 1 << 3;
    private static final int AMBIENT_OCCLUSION = 1 << 4;
    private static final int WRITE_COLOR = 1 << 5;
    private static final int WRITE_DEPTH = 1 << 6;
    private static final int CARDINAL_SHIFT = 8;
    private static final int LIGHT_MODE_SHIFT = 10;
    private static final int DEPTH_TEST_SHIFT = 12;
    private static final int TRANSPARENCY_SHIFT = 16;
    private static final int SMOOTHNESS_SHIFT = 20;

    static FlywheelMaterialData capture(Material material) {
        ResourceLocation texture = material.texture();
        int textureId = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        if (textureId == 0 || !TextureTracker.GLID2Texture.containsKey(textureId)) {
            throw new IllegalStateException("Flywheel material texture is not live in Vulkan: " + texture);
        }
        int alphaMode = switch (material.transparency()) {
            case OPAQUE -> material.cutout().source().getPath().endsWith("off.glsl") ? 0 : 1;
            case ADDITIVE -> 11;
            case LIGHTNING -> 20;
            case GLINT -> 21;
            case CRUMBLING -> 22;
            case TRANSLUCENT, ORDER_INDEPENDENT -> 23;
        };
        int lightMode = lightMode(material.light().source());
        int flags = (material.useOverlay() ? USE_OVERLAY : 0)
            | (material.useLight() ? USE_LIGHT : 0)
            | com.radiance.client.render.MaterialFaces.withEnabled(material.backfaceCulling())
            | (material.polygonOffset() ? POLYGON_OFFSET : 0)
            | (material.ambientOcclusion() ? AMBIENT_OCCLUSION : 0)
            | (material.writeMask().color() ? WRITE_COLOR : 0)
            | (material.writeMask().depth() ? WRITE_DEPTH : 0)
            | (material.cardinalLightingMode().ordinal() << CARDINAL_SHIFT)
            | (lightMode << LIGHT_MODE_SHIFT)
            | (material.depthTest().ordinal() << DEPTH_TEST_SHIFT)
            | (material.transparency().ordinal() << TRANSPARENCY_SHIFT)
            | (BackendConfig.INSTANCE.lightSmoothness().ordinal() << SMOOTHNESS_SHIFT);
        String key = new StringJoiner("|")
            .add("texture=" + texture)
            .add("vertex=" + material.shaders().vertexSource())
            .add("fragment=" + material.shaders().fragmentSource())
            .add("fog=" + material.fog().source())
            .add("cutout=" + material.cutout().source())
            .add("light=" + material.light().source())
            .add("transparency=" + material.transparency())
            .add("depth=" + material.depthTest())
            .add("write=" + material.writeMask())
            .add("cardinal=" + material.cardinalLightingMode())
            .add("blur=" + material.blur())
            .add("mipmap=" + material.mipmap())
            .toString();
        return new FlywheelMaterialData(textureId, alphaMode, flags, key);
    }

    private static int lightMode(ResourceLocation shader) {
        String path = shader.getPath();
        if (path.endsWith("/off.glsl")) return 0;
        if (path.endsWith("/flat.glsl")) return 1;
        if (path.endsWith("/smooth.glsl")) return 2;
        if (path.endsWith("/smooth_when_embedded.glsl")) return 3;
        throw new UnsupportedOperationException("Unsupported Flywheel light shader " + shader);
    }
}
