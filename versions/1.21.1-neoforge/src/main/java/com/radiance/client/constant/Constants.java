package com.radiance.client.constant;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.render.contract.RenderAbi;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.RenderType;

public class Constants {

    public enum IndexTypes {
        SHORT(VertexFormat.IndexType.SHORT, RenderAbi.INDEX_SHORT),
        INT(VertexFormat.IndexType.INT, RenderAbi.INDEX_INT);

        private static final Map<VertexFormat.IndexType, Integer> BY_INDEX_TYPE =
                Collections.unmodifiableMap(
                        Arrays.stream(values())
                                .collect(
                                        Collectors.toMap(
                                                IndexTypes::getIndexType, IndexTypes::getValue)));

        private final VertexFormat.IndexType indexType;
        private final int value;

        IndexTypes(VertexFormat.IndexType indexType, int value) {
            this.indexType = indexType;
            this.value = value;
        }

        public static int getValue(VertexFormat.IndexType indexType) {
            return BY_INDEX_TYPE.get(indexType);
        }

        public VertexFormat.IndexType getIndexType() {
            return indexType;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DrawModes {
        LINES(VertexFormat.Mode.LINES, RenderAbi.MODE_LINES),
        LINE_STRIP(VertexFormat.Mode.LINE_STRIP, RenderAbi.MODE_LINE_STRIP),
        DEBUG_LINES(VertexFormat.Mode.DEBUG_LINES, RenderAbi.MODE_DEBUG_LINES),
        DEBUG_LINE_STRIP(VertexFormat.Mode.DEBUG_LINE_STRIP, RenderAbi.MODE_DEBUG_LINE_STRIP),
        TRIANGLES(VertexFormat.Mode.TRIANGLES, RenderAbi.MODE_TRIANGLES),
        TRIANGLE_STRIP(VertexFormat.Mode.TRIANGLE_STRIP, RenderAbi.MODE_TRIANGLE_STRIP),
        TRIANGLE_FAN(VertexFormat.Mode.TRIANGLE_FAN, RenderAbi.MODE_TRIANGLE_FAN),
        QUADS(VertexFormat.Mode.QUADS, RenderAbi.MODE_QUADS);

        private static final Map<VertexFormat.Mode, Integer> BY_DRAW_MODE =
                Collections.unmodifiableMap(
                        Arrays.stream(values())
                                .collect(
                                        Collectors.toMap(
                                                DrawModes::getDrawMode, DrawModes::getValue)));

        private final VertexFormat.Mode drawMode;
        private final int value;

        DrawModes(VertexFormat.Mode drawMode, int value) {
            this.drawMode = drawMode;
            this.value = value;
        }

        public static int getValue(VertexFormat.Mode drawMode) {
            return BY_DRAW_MODE.get(drawMode);
        }

        public VertexFormat.Mode getDrawMode() {
            return drawMode;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VertexFormats {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,
                RenderAbi.FORMAT_POSITION_COLOR_TEXTURE_LIGHT_NORMAL),
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                RenderAbi.FORMAT_POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL),
        POSITION_TEXTURE_COLOR_LIGHT(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE,
                RenderAbi.FORMAT_POSITION_TEXTURE_COLOR_LIGHT),
        POSITION(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, RenderAbi.FORMAT_POSITION),
        POSITION_COLOR(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                RenderAbi.FORMAT_POSITION_COLOR),
        LINES(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                RenderAbi.FORMAT_LINES),
        POSITION_COLOR_LIGHT(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
                RenderAbi.FORMAT_POSITION_COLOR_LIGHT),
        POSITION_TEXTURE(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX,
                RenderAbi.FORMAT_POSITION_TEXTURE),
        POSITION_TEXTURE_COLOR(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                RenderAbi.FORMAT_POSITION_TEXTURE_COLOR),
        POSITION_COLOR_TEXTURE_LIGHT(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                RenderAbi.FORMAT_POSITION_COLOR_TEXTURE_LIGHT),
        POSITION_TEXTURE_LIGHT_COLOR(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR,
                RenderAbi.FORMAT_POSITION_TEXTURE_LIGHT_COLOR),
        POSITION_TEXTURE_COLOR_NORMAL(
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                RenderAbi.FORMAT_POSITION_TEXTURE_COLOR_NORMAL),
        PBR_TRIANGLE(PBRVertexFormats.PBR_TRIANGLE, RenderAbi.FORMAT_PBR_TRIANGLE),
        PBR_COMPACT_V1(PBRVertexFormats.PBR_COMPACT_V1, RenderAbi.FORMAT_PBR_COMPACT_V1),
        CAPTURED_LINES(
                com.radiance.client.vertex.CapturedLineVertexConsumer.FORMAT,
                RenderAbi.FORMAT_CAPTURED_LINES);

        private static final Map<VertexFormat, Integer> BY_VERTEX_FORMAT =
                Collections.unmodifiableMap(
                        Arrays.stream(values())
                                .collect(
                                        Collectors.toMap(
                                                VertexFormats::getVertexFormat,
                                                VertexFormats::getValue)));

        private final VertexFormat vertexFormat;
        private final int value;

        VertexFormats(VertexFormat vertexFormat, int value) {
            this.vertexFormat = vertexFormat;
            this.value = value;
        }

        public static int getValue(VertexFormat vertexFormat) {
            return BY_VERTEX_FORMAT.get(vertexFormat);
        }

        public VertexFormat getVertexFormat() {
            return vertexFormat;
        }

        public int getValue() {
            return value;
        }
    }

    public enum GeometryTypes {
        SHADOW(RenderAbi.GEOMETRY_SHADOW),
        WORLD_SOLID(RenderAbi.GEOMETRY_WORLD_SOLID),
        WORLD_TRANSPARENT(RenderAbi.GEOMETRY_WORLD_TRANSPARENT),
        WORLD_NO_REFLECT(RenderAbi.GEOMETRY_WORLD_NO_REFLECT),
        WORLD_CLOUD(RenderAbi.GEOMETRY_WORLD_CLOUD),
        BOAT_WATER_MASK(RenderAbi.GEOMETRY_BOAT_WATER_MASK),
        END_PORTAL(RenderAbi.GEOMETRY_END_PORTAL),
        END_GATEWAY(RenderAbi.GEOMETRY_END_GATEWAY);

        private final int value;

        GeometryTypes(int value) {
            this.value = value;
        }

        public static GeometryTypes getGeometryType(RenderType layer, boolean reflect) {
            return com.radiance.compatibility.vanilla.VanillaMaterialAdapter.getGeometryType(
                    layer, reflect);
        }

        public int getValue() {
            return value;
        }
    }

    public enum Coordinates {
        WORLD(com.radiance.render.contract.SceneSpace.WORLD),
        CAMERA(com.radiance.render.contract.SceneSpace.CAMERA),
        CAMERA_SHIFT(com.radiance.render.contract.SceneSpace.CAMERA_SHIFT);

        private final int value;

        Coordinates(com.radiance.render.contract.SceneSpace space) {
            this.value = space.value();
        }

        public int getValue() {
            return value;
        }
    }

    public enum RayTracingFlags {
        WORLD(RenderAbi.QUERY_WORLD),
        /**
         * Geometry owned by an attached, awake local camera entity. First-person direct imaging,
         * including continuous refraction, excludes this bit; transport queries include it.
         * Source-visible camera-entity states such as sleeping or a detached camera use the normal
         * world masks.
         */
        PLAYER(RenderAbi.QUERY_SELF),
        /**
         * Geometry hidden from direct imaging, including continuous refraction. Transport and the
         * depth-ignoring priority image retain it, and its material receives world illumination.
         */
        PRIORITY_ONLY(RenderAbi.QUERY_PRIORITY),
        /** Camera-dedicated geometry receives lighting but is excluded from world transport. */
        HAND(RenderAbi.QUERY_HAND),
        WEATHER(RenderAbi.QUERY_WEATHER),
        PARTICLE(RenderAbi.QUERY_PARTICLE),
        CLOUD(RenderAbi.QUERY_CLOUD),
        /** Source control volume for removing actual interior water, never a visible surface. */
        BOAT_WATER_MASK(RenderAbi.QUERY_BOAT_WATER_MASK);

        private final int value;

        RayTracingFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
