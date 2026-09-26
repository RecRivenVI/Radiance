package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class PBRVertexFormatElements {

    /**
     * Allocates the next free element id instead of hardcoding one. Other renderer
     * mods (Veil, Sodium, ...) register their own elements on load, so fixed ids
     * collide and abort vertex format initialization.
     */
    private static VertexFormatElement register(VertexFormatElement.Type type,
        VertexFormatElement.Usage usage, int count) {
        return VertexFormatElement.register(VertexFormatElement.findNextId(), 0, type, usage, count);
    }

    public static final VertexFormatElement
        PBR_POS = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_USE_NORM = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_NORM = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_USE_COLOR_LAYER = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COLOR_LAYER = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

    public static final VertexFormatElement
        PBR_USE_TEXTURE = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_USE_OVERLAY = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_UV = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_OVERLAY_UV = register(VertexFormatElement.Type.INT, VertexFormatElement.Usage.UV, 2);

    public static final VertexFormatElement
        PBR_USE_GLINT = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_ID = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_UV = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_GLINT_TEXTURE = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_USE_LIGHT = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_LIGHT_UV = register(VertexFormatElement.Type.INT, VertexFormatElement.Usage.UV, 2);

    public static final VertexFormatElement
        PBR_COORDINATE = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_POST_BASE = register(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_ALBEDO_EMISSION = register(VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);

    /**
     * Compact-v1 gives the existing UINT/UV/1 slot a new per-format name. The compact consumer
     * uses the canonical PBR_TRIANGLE mask for logical one-shot setters, so this physical alias
     * does not replace the PBR_USE_NORM logical field.
     */
    public static final VertexFormatElement PBR_PACKED_MODES = PBR_USE_NORM;
}
