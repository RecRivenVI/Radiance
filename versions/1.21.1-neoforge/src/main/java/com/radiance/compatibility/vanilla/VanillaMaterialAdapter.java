package com.radiance.compatibility.vanilla;

import static com.radiance.client.constant.Constants.GeometryTypes.*;
import static net.minecraft.client.renderer.RenderStateShard.*;

import com.radiance.client.constant.Constants.GeometryTypes;
import com.radiance.render.contract.RenderAbi;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Version-specific source classification; backends consume its fields, never source names. */
public final class VanillaMaterialAdapter {
    private VanillaMaterialAdapter() {}

    private static final int ALPHA_MODE_OPAQUE = RenderAbi.ALPHA_OPAQUE;
    private static final int ALPHA_MODE_CUTOUT = RenderAbi.ALPHA_CUTOUT;
    private static final int ALPHA_MODE_TRANSMISSION = RenderAbi.ALPHA_TRANSMISSION;
    public static final int ALPHA_MODE_CUTOUT_LOW = RenderAbi.ALPHA_CUTOUT_LOW;
    private static final int ALPHA_MODE_COVERAGE = RenderAbi.ALPHA_COVERAGE;
    private static final int ALPHA_MODE_ADDITIVE = RenderAbi.ALPHA_ADDITIVE;
    // Shared with MCVR's existing multiplicative crumbling hit-shader branch.
    private static final int ALPHA_MODE_CRUMBLING = RenderAbi.ALPHA_FLYWHEEL_CRUMBLING;
    // Keep text modes outside the material alpha-mode range. These values are
    // packed alongside the regular alpha mode and are consumed by both the
    // post-text raster path and world text hit shaders.
    private static final int POST_TEXT_MODE_BACKGROUND = RenderAbi.TEXT_BACKGROUND;
    private static final int POST_TEXT_MODE_INTENSITY = RenderAbi.TEXT_INTENSITY;
    private static final int POST_TEXT_MODE_RGBA = RenderAbi.TEXT_RGBA;
    private static final int POST_TEXT_MODE_BACKGROUND_SEE_THROUGH =
            RenderAbi.TEXT_BACKGROUND_SEE_THROUGH;
    private static final int POST_TEXT_MODE_INTENSITY_SEE_THROUGH =
            RenderAbi.TEXT_INTENSITY_SEE_THROUGH;
    private static final int POST_TEXT_MODE_RGBA_SEE_THROUGH = RenderAbi.TEXT_RGBA_SEE_THROUGH;
    private static final int POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET =
            RenderAbi.TEXT_INTENSITY_POLYGON_OFFSET;
    private static final int POST_TEXT_MODE_RGBA_POLYGON_OFFSET =
            RenderAbi.TEXT_RGBA_POLYGON_OFFSET;
    private static final int GLINT_MODE_ENTITY = RenderAbi.GLINT_ENTITY;
    private static final int GLINT_MODE_ITEM = RenderAbi.GLINT_ITEM;

    private static java.util.Map<ShaderStateShard, Integer> sourceAlphaRegistry() {
        var map = new java.util.IdentityHashMap<ShaderStateShard, Integer>();
        var entries =
                java.util.List.of(
                        java.util.Map.entry(RENDERTYPE_SOLID_SHADER, ALPHA_MODE_OPAQUE),
                        java.util.Map.entry(RENDERTYPE_ENTITY_SOLID_SHADER, ALPHA_MODE_OPAQUE),
                        java.util.Map.entry(RENDERTYPE_TRANSLUCENT_SHADER, ALPHA_MODE_TRANSMISSION),
                        java.util.Map.entry(
                                RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER,
                                ALPHA_MODE_TRANSMISSION),
                        java.util.Map.entry(
                                RENDERTYPE_TEXT_BACKGROUND_SHADER, POST_TEXT_MODE_BACKGROUND),
                        java.util.Map.entry(
                                RENDERTYPE_TEXT_BACKGROUND_SEE_THROUGH_SHADER,
                                POST_TEXT_MODE_BACKGROUND_SEE_THROUGH),
                        java.util.Map.entry(RENDERTYPE_TEXT_SHADER, POST_TEXT_MODE_RGBA),
                        java.util.Map.entry(
                                RENDERTYPE_TEXT_INTENSITY_SHADER, POST_TEXT_MODE_INTENSITY),
                        java.util.Map.entry(
                                RENDERTYPE_TEXT_SEE_THROUGH_SHADER,
                                POST_TEXT_MODE_RGBA_SEE_THROUGH),
                        java.util.Map.entry(
                                RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER,
                                POST_TEXT_MODE_INTENSITY_SEE_THROUGH));
        entries.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        map.put(RENDERTYPE_CUTOUT_SHADER, ALPHA_MODE_CUTOUT_LOW);
        map.put(
                RENDERTYPE_ENERGY_SWIRL_SHADER,
                alpha(ALPHA_MODE_ADDITIVE, RenderAbi.SOURCE_ALPHA_POST_LOW));
        map.put(RENDERTYPE_EYES_SHADER, ALPHA_MODE_ADDITIVE);
        map.put(
                RENDERTYPE_BREEZE_WIND_SHADER,
                alpha(ALPHA_MODE_COVERAGE, RenderAbi.SOURCE_ALPHA_POST_LOW));
        map.put(RENDERTYPE_CUTOUT_MIPPED_SHADER, ALPHA_MODE_CUTOUT);
        map.put(
                RENDERTYPE_ARMOR_CUTOUT_NO_CULL_SHADER,
                alpha(ALPHA_MODE_OPAQUE, RenderAbi.SOURCE_ALPHA_POST_LOW));
        for (var shader :
                java.util.List.of(
                        RENDERTYPE_ENTITY_CUTOUT_SHADER,
                        RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER,
                        RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER,
                        RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER))
            map.put(shader, alpha(ALPHA_MODE_OPAQUE, RenderAbi.SOURCE_ALPHA_TEXEL_LOW));
        for (var shader :
                java.util.List.of(
                        RENDERTYPE_ENTITY_TRANSLUCENT_SHADER,
                        RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER))
            map.put(shader, alpha(ALPHA_MODE_COVERAGE, RenderAbi.SOURCE_ALPHA_TEXEL_LOW));
        for (var shader :
                java.util.List.of(
                        RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER,
                        RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER))
            map.put(shader, alpha(ALPHA_MODE_COVERAGE, RenderAbi.SOURCE_ALPHA_POST_LOW));
        map.replaceAll(
                (shader, descriptor) ->
                        descriptor >= RenderAbi.TEXT_BACKGROUND
                                        && descriptor <= RenderAbi.TEXT_RGBA_POLYGON_OFFSET
                                ? alpha(
                                        descriptor,
                                        descriptor >= RenderAbi.TEXT_BACKGROUND_SEE_THROUGH
                                                        && descriptor
                                                                <= RenderAbi.TEXT_RGBA_SEE_THROUGH
                                                ? RenderAbi.SOURCE_ALPHA_PRE_GLOBAL_LOW
                                                : RenderAbi.SOURCE_ALPHA_POST_LOW)
                                : descriptor);
        return java.util.Collections.unmodifiableMap(map);
    }

    private static final java.util.Map<ShaderStateShard, Integer> SOURCE_ALPHA =
            sourceAlphaRegistry();

    public static com.radiance.render.contract.DrawStateSnapshot sourceColour(
            RenderType layer, com.radiance.render.contract.DrawStateSnapshot state) {
        if (!state.finiteColour()) {
            com.radiance.client.render.RenderCaptureContract.reportDiscard(
                    "draw colour", "non-finite source modulation");
            return new com.radiance.render.contract.DrawStateSnapshot(
                    state.faces(), state.lineWidth(), 1, 1, 1, 1, state.uvTransform());
        }
        boolean known =
                com.radiance.client.render.LineDrawAdapter.isLine(layer.mode())
                        || com.radiance.compatibility.simulated.SpringDrawContract.from(layer)
                                != null;
        if (layer instanceof RenderType.CompositeRenderType composite) {
            var shader = composite.state.shaderState;
            known |=
                    SOURCE_ALPHA.containsKey(shader)
                            || shader == RENDERTYPE_CUTOUT_SHADER
                            || shader == RENDERTYPE_CUTOUT_MIPPED_SHADER
                            || shader == RENDERTYPE_ENTITY_CUTOUT_SHADER
                            || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER
                            || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER
                            || shader == RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                            || shader == RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER
                            || shader == RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER
                            || shader == RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER
                            || shader == RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER
                            || shader == RENDERTYPE_EYES_SHADER
                            || shader == RENDERTYPE_ENERGY_SWIRL_SHADER
                            || shader == RENDERTYPE_LIGHTNING_SHADER
                            || shader == RENDERTYPE_ENTITY_ALPHA_SHADER
                            || shader == RENDERTYPE_BEACON_BEAM_SHADER;
        }
        return known
                ? state
                : new com.radiance.render.contract.DrawStateSnapshot(
                        state.faces(), state.lineWidth(), 1, 1, 1, 1, state.uvTransform());
    }

    public static boolean transformsPrimaryUv(RenderType layer) {
        return layer instanceof RenderType.CompositeRenderType composite
                && (composite.state.shaderState == RENDERTYPE_ENERGY_SWIRL_SHADER
                        || composite.state.shaderState == RENDERTYPE_BREEZE_WIND_SHADER);
    }

    public static boolean supportsSurfaceLayers(RenderType layer) {
        if (!(layer instanceof RenderType.CompositeRenderType composite)) return false;
        Integer descriptor = SOURCE_ALPHA.get(composite.state.shaderState);
        if (descriptor == null || composite.state.depthTestState != LEQUAL_DEPTH_TEST) return false;
        int mode = descriptor & RenderAbi.PACKED_ALPHA_MASK;
        return ((mode == ALPHA_MODE_OPAQUE
                                || mode == ALPHA_MODE_CUTOUT
                                || mode == ALPHA_MODE_CUTOUT_LOW)
                        && composite.state.transparencyState == NO_TRANSPARENCY)
                || (mode == ALPHA_MODE_COVERAGE
                        && composite.state.transparencyState == TRANSLUCENT_TRANSPARENCY);
    }

    public static com.radiance.render.contract.DrawStateSnapshot.UvTransform primaryUv(
            RenderType layer, org.joml.Matrix4fc matrix) {
        if (!transformsPrimaryUv(layer))
            return com.radiance.render.contract.DrawStateSnapshot.UvTransform.IDENTITY;
        return new com.radiance.render.contract.DrawStateSnapshot.UvTransform(
                matrix.m00(), matrix.m10(), matrix.m30(), matrix.m01(), matrix.m11(), matrix.m31());
    }

    private static int alpha(int mode, int rule) {
        return com.radiance.render.contract.SourceAlpha.encode(mode, rule);
    }

    public static int getAlphaMode(RenderType renderLayer) {
        if (!(renderLayer instanceof RenderType.CompositeRenderType multiPhase)) {
            return ALPHA_MODE_OPAQUE;
        }

        Integer registered = SOURCE_ALPHA.get(multiPhase.state.shaderState);
        int base =
                registered == null ? -1 : com.radiance.render.contract.SourceAlpha.mode(registered);
        boolean normalSourceBlend =
                registered != null
                        && (base == ALPHA_MODE_OPAQUE
                                        || base == ALPHA_MODE_CUTOUT
                                        || base == ALPHA_MODE_CUTOUT_LOW
                                ? multiPhase.state.transparencyState == NO_TRANSPARENCY
                                : base == ALPHA_MODE_ADDITIVE
                                        ? multiPhase.state.transparencyState
                                                == ADDITIVE_TRANSPARENCY
                                        : multiPhase.state.transparencyState
                                                == TRANSLUCENT_TRANSPARENCY);
        if (normalSourceBlend) {
            if (multiPhase.state.layeringState == POLYGON_OFFSET_LAYERING) {
                if (base == POST_TEXT_MODE_RGBA)
                    return alpha(
                            POST_TEXT_MODE_RGBA_POLYGON_OFFSET, RenderAbi.SOURCE_ALPHA_POST_LOW);
                if (base == POST_TEXT_MODE_INTENSITY)
                    return alpha(
                            POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET,
                            RenderAbi.SOURCE_ALPHA_POST_LOW);
            }
            return registered;
        }
        int postTextMode = getPostTextMode(multiPhase.name);
        if (postTextMode != ALPHA_MODE_OPAQUE) {
            return postTextMode;
        }

        if (RenderStateShard.CRUMBLING_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            // Vanilla uses DST_COLOR / SRC_COLOR: the stage RGB multiplies the underlying
            // surface by twice its value. Coverage blending loses that destruction pattern.
            return ALPHA_MODE_CRUMBLING;
        }

        if (multiPhase.name.contains("solid")) {
            return ALPHA_MODE_OPAQUE;
        }

        if (multiPhase.name.contains("cutout")) {
            return ALPHA_MODE_CUTOUT;
        }

        if (RenderStateShard.NO_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
            return ALPHA_MODE_CUTOUT;
        }

        // The two vanilla block layers contain glass, water and similar dielectric media.
        // Other translucent render types use Minecraft's ordered alpha blending and must not
        // be reinterpreted as refractive glass by the path tracer.
        if (multiPhase.name.equals("translucent")
                || multiPhase.name.equals("translucent_moving_block")) {
            return ALPHA_MODE_TRANSMISSION;
        }

        return drawBlendMode(renderLayer);
    }

    public static int drawBlendMode(RenderType layer) {
        if (!(layer instanceof RenderType.CompositeRenderType composite))
            return RenderAbi.ALPHA_OPAQUE;
        var blend = composite.state.transparencyState;
        if (blend == NO_TRANSPARENCY) return RenderAbi.ALPHA_OPAQUE;
        if (blend == CRUMBLING_TRANSPARENCY) return RenderAbi.ALPHA_FLYWHEEL_CRUMBLING;
        if (blend == LIGHTNING_TRANSPARENCY) return RenderAbi.ALPHA_FLYWHEEL_LIGHTNING;
        if (blend == GLINT_TRANSPARENCY) return RenderAbi.ALPHA_FLYWHEEL_GLINT;
        if (blend == ADDITIVE_TRANSPARENCY) return RenderAbi.ALPHA_ADDITIVE;
        return RenderAbi.ALPHA_COVERAGE;
    }

    public static String normalizeTextLayerName(String layerName) {
        return switch (layerName) {
            case "neoforge_text" -> "text";
            case "neoforge_text_intensity" -> "text_intensity";
            case "neoforge_text_polygon_offset" -> "text_polygon_offset";
            case "neoforge_text_intensity_polygon_offset" -> "text_intensity_polygon_offset";
            case "neoforge_text_see_through" -> "text_see_through";
            default -> layerName;
        };
    }

    private static int getPostTextMode(String layerName) {
        return switch (normalizeTextLayerName(layerName)) {
            case "text_background" -> POST_TEXT_MODE_BACKGROUND;
            case "text_intensity" -> POST_TEXT_MODE_INTENSITY;
            case "text" -> POST_TEXT_MODE_RGBA;
            case "text_background_see_through" -> POST_TEXT_MODE_BACKGROUND_SEE_THROUGH;
            case "text_intensity_see_through" -> POST_TEXT_MODE_INTENSITY_SEE_THROUGH;
            case "text_see_through" -> POST_TEXT_MODE_RGBA_SEE_THROUGH;
            case "text_intensity_polygon_offset" -> POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
            case "text_polygon_offset" -> POST_TEXT_MODE_RGBA_POLYGON_OFFSET;
            default -> ALPHA_MODE_OPAQUE;
        };
    }

    public static int glintModeForRenderTypeName(String name) {
        return name.startsWith("entity_glint") || name.equals("armor_entity_glint")
                ? GLINT_MODE_ENTITY
                : GLINT_MODE_ITEM;
    }

    public static GeometryTypes getGeometryType(RenderType renderLayer, boolean reflect) {
        if (com.radiance.client.render.LineDrawAdapter.isLine(renderLayer.mode()))
            return reflect ? WORLD_TRANSPARENT : WORLD_NO_REFLECT;
        if (renderLayer instanceof RenderType.CompositeRenderType known) {
            var shader = known.state.shaderState;
            if (shader == RENDERTYPE_WATER_MASK_SHADER) return BOAT_WATER_MASK;
            if (shader == RENDERTYPE_END_PORTAL_SHADER) return END_PORTAL;
            if (shader == RENDERTYPE_END_GATEWAY_SHADER) return END_GATEWAY;
            if (shader == RENDERTYPE_CLOUDS_SHADER) return WORLD_CLOUD;
            if ((shader == RENDERTYPE_SOLID_SHADER || shader == RENDERTYPE_ENTITY_SOLID_SHADER)
                    && known.state.transparencyState == NO_TRANSPARENCY)
                return reflect ? WORLD_SOLID : WORLD_NO_REFLECT;
            // A known discard/blend program must retain any-hit even if its source name says solid.
            if (SOURCE_ALPHA.containsKey(shader))
                return reflect ? WORLD_TRANSPARENT : WORLD_NO_REFLECT;
        }
        // Unregistered custom programs retain their current source adapter policy (adr-5).
        if (renderLayer.name.contains("water_mask")) {
            return BOAT_WATER_MASK;
        } else if (renderLayer.name.contains("end_portal")) {
            return END_PORTAL;
        } else if (renderLayer.name.contains("end_gateway")) {
            return END_GATEWAY;
        }

        if (renderLayer.name.contains("cloud")) {
            return WORLD_CLOUD;
        }

        if (!reflect) {
            return WORLD_NO_REFLECT;
        }

        RenderType.CompositeRenderType multiPhase = (RenderType.CompositeRenderType) renderLayer;
        if (multiPhase.name.contains("solid")) {
            // solid
            return WORLD_SOLID;
        }

        if (multiPhase.sortOnUpload()) {
            // transparent
            if (RenderStateShard.NO_TRANSPARENCY.equals(multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else if (RenderStateShard.ADDITIVE_TRANSPARENCY.equals(
                    multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else if (RenderStateShard.LIGHTNING_TRANSPARENCY.equals(
                    multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else if (RenderStateShard.GLINT_TRANSPARENCY.equals(
                    multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else if (RenderStateShard.CRUMBLING_TRANSPARENCY.equals(
                    multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else if (RenderStateShard.TRANSLUCENT_TRANSPARENCY.equals(
                    multiPhase.state.transparencyState)) {
                return WORLD_TRANSPARENT;
            } else {
                throw new IllegalArgumentException("Invalid render layer " + multiPhase);
            }
        } else {
            // cut out
            return WORLD_TRANSPARENT;
        }
    }
}
