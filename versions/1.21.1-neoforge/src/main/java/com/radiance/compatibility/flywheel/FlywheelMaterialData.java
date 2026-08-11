package com.radiance.compatibility.flywheel;

import com.radiance.client.texture.TextureTracker;
import com.radiance.render.contract.RenderAbi;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.BackendConfig;
import java.util.StringJoiner;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

record FlywheelMaterialData(int textureId, int alphaMode, int flags, String key) {
    private static final int USE_OVERLAY = RenderAbi.FLYWHEEL_OVERLAY_BIT;
    private static final int USE_LIGHT = RenderAbi.FLYWHEEL_LIGHT_BIT;
    private static final int POLYGON_OFFSET = RenderAbi.FLYWHEEL_POLYGON_OFFSET_BIT;
    private static final int AMBIENT_OCCLUSION = RenderAbi.FLYWHEEL_AO_BIT;
    private static final int WRITE_COLOR = RenderAbi.FLYWHEEL_WRITE_COLOR_BIT;
    private static final int WRITE_DEPTH = RenderAbi.FLYWHEEL_WRITE_DEPTH_BIT;
    private static final int CARDINAL_SHIFT = RenderAbi.FLYWHEEL_CARDINAL_SHIFT;
    private static final int LIGHT_MODE_SHIFT = RenderAbi.FLYWHEEL_LIGHT_MODE_SHIFT;
    private static final int DEPTH_TEST_SHIFT = RenderAbi.FLYWHEEL_DEPTH_TEST_SHIFT;
    private static final int TRANSPARENCY_SHIFT = RenderAbi.FLYWHEEL_TRANSPARENCY_SHIFT;
    private static final int SMOOTHNESS_SHIFT = RenderAbi.FLYWHEEL_SMOOTHNESS_SHIFT;

    static FlywheelMaterialData capture(Material material) {
        ResourceLocation texture = material.texture();
        int textureId = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        if (textureId == 0 || !TextureTracker.GLID2Texture.containsKey(textureId)) {
            throw new IllegalStateException(
                    "Flywheel material texture is not live in Vulkan: " + texture);
        }
        int alphaMode =
                switch (material.transparency()) {
                    case OPAQUE -> cutoutMode(material.cutout().source());
                    case ADDITIVE -> RenderAbi.ALPHA_ADDITIVE;
                    case LIGHTNING -> RenderAbi.ALPHA_FLYWHEEL_LIGHTNING;
                    case GLINT -> RenderAbi.ALPHA_FLYWHEEL_GLINT;
                    case CRUMBLING -> RenderAbi.ALPHA_FLYWHEEL_CRUMBLING;
                    case TRANSLUCENT, ORDER_INDEPENDENT -> RenderAbi.ALPHA_FLYWHEEL_TRANSLUCENT;
                };
        int lightMode = lightMode(material.light().source());
        int flags =
                (material.useOverlay() ? USE_OVERLAY : 0)
                        | (material.useLight() ? USE_LIGHT : 0)
                        | com.radiance.client.render.MaterialFaces.withEnabled(
                                material.backfaceCulling())
                        | (material.polygonOffset() ? POLYGON_OFFSET : 0)
                        | (material.ambientOcclusion() ? AMBIENT_OCCLUSION : 0)
                        | (material.writeMask().color() ? WRITE_COLOR : 0)
                        | (material.writeMask().depth() ? WRITE_DEPTH : 0)
                        | (material.cardinalLightingMode().ordinal() << CARDINAL_SHIFT)
                        | (lightMode << LIGHT_MODE_SHIFT)
                        | (material.depthTest().ordinal() << DEPTH_TEST_SHIFT)
                        | (material.transparency().ordinal() << TRANSPARENCY_SHIFT)
                        | (BackendConfig.INSTANCE.lightSmoothness().ordinal() << SMOOTHNESS_SHIFT);
        String key =
                new StringJoiner("|")
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

    static int cutoutMode(ResourceLocation shader) {
        if (!shader.getNamespace().equals("flywheel")) return unregisteredCutoutMode(shader);
        return switch (shader.getPath()) {
            case "cutout/off.glsl" -> RenderAbi.ALPHA_OPAQUE;
            case "cutout/epsilon.glsl" -> RenderAbi.ALPHA_CUTOUT_EPSILON;
            case "cutout/one_tenth.glsl" -> RenderAbi.ALPHA_CUTOUT_LOW;
            case "cutout/half.glsl" -> RenderAbi.ALPHA_CUTOUT;
            default -> unregisteredCutoutMode(shader);
        };
    }

    // Keep unregistered programs unchanged pending the visible compatibility decision in adr-5.
    private static int unregisteredCutoutMode(ResourceLocation shader) {
        return shader.getPath().endsWith("off.glsl")
                ? RenderAbi.ALPHA_OPAQUE
                : RenderAbi.ALPHA_CUTOUT;
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
