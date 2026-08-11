package com.radiance.api.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.texture.TextureTasks;
import java.util.ArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed dispatch at the actual producer seams; no source names or optional class probes. */
public final class SourceHooks {
    private SourceHooks() {}

    public static boolean sectionLayer(
            net.minecraft.world.level.block.state.BlockState state, RenderType layer) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Geometry geometry
                    && !geometry.sectionLayer(state, layer)) return false;
        return true;
    }

    public static void blockEntityChanged(BlockEntity entity) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.World world) world.blockEntityChanged(entity);
    }

    public static SourceIntegration.Geometry.Overlay orderedOverlay(RenderType layer) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Geometry geometry) {
                var mode = geometry.orderedOverlay(layer);
                if (mode != SourceIntegration.Geometry.Overlay.NONE) return mode;
            }
        return SourceIntegration.Geometry.Overlay.NONE;
    }

    public static boolean supportsWorldProgram(String program) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Geometry geometry
                    && geometry.supportsWorldProgram(program)) return true;
        return false;
    }

    public static boolean retainOffscreenLayer(RenderType layer) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Geometry geometry
                    && geometry.retainOffscreenLayer(layer)) return true;
        return false;
    }

    public static void frameStarted() {
        BlockEntityPublications.beginFrame();
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.frameStarted();
    }

    public static void update(ClientLevel l, Camera c, float t) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.update(l, c, t);
        BlockEntityPublications.reconcileRemovals();
    }

    public static SourceIntegration.Frame begin(SourceIntegration.FrameInput input) {
        var frames = new ArrayList<SourceIntegration.Frame>();
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) {
                var frame = w.begin(input);
                if (frame.active()) frames.add(frame);
            }
        if (frames.isEmpty()) return SourceIntegration.Frame.EMPTY;
        return new SourceIntegration.Frame() {
            public boolean active() {
                return true;
            }

            public void afterEntities() {
                for (var f : frames) f.afterEntities();
            }

            public void beforeCrumbling(
                    it.unimi.dsi.fastutil.longs.Long2ObjectMap<
                                    java.util.SortedSet<
                                            net.minecraft.server.level.BlockDestructionProgress>>
                            p) {
                for (var f : frames) f.beforeCrumbling(p);
            }
        };
    }

    public static float imagingDistance(float distance) {
        for (var source : RenderPlatform.sources()) {
            if (source instanceof SourceIntegration.World world) {
                float requested = world.imagingDistance(distance);
                if (Float.isFinite(requested)) distance = Math.max(distance, requested);
            }
        }
        return distance;
    }

    public static void blocks(ClientLevel l, float t) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.blocks(l, t);
    }

    public static void blockEntities(
            ClientLevel l, Camera c, BlockEntityRenderDispatcher d, float t) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.blockEntities(l, c, d, t);
        BlockEntityPublications.endFrame();
    }

    public static Object blockSource(BlockEntity entity) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Spatial spatial) {
                Object identity = spatial.blockSource(entity);
                if (identity != null) return identity;
            }
        return null;
    }

    public static void worldEffects(
            Camera c,
            net.minecraft.client.renderer.GameRenderer r,
            org.joml.Matrix4f m,
            org.joml.Matrix4f p) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.worldEffects(c, r, m, p);
    }

    public static void debug(ClientLevel l, Camera c, float t) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.debug(l, c, t);
    }

    public static void prepare(ClientLevel l, Camera c, Frustum f, boolean p) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.prepare(l, c, f, p);
    }

    public static boolean ownsEntity(ClientLevel l, Entity e) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w && w.ownsEntity(l, e)) return true;
        return false;
    }

    public static boolean ownsBlockEntity(ClientLevel l, BlockEntity e) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w && w.ownsBlockEntity(l, e)) return true;
        return false;
    }

    public static SourceIntegration.EntityTransform entityTransform(
            Entity e, float t, double x, double y, double z) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Spatial w) {
                var p = w.entityTransform(e, t, x, y, z);
                if (p != null) return p;
            }
        return null;
    }

    public static SourceIntegration.Outline outline(ClientLevel l, Camera c, BlockPos p, float t) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Spatial w) {
                var result = w.outline(l, c, p, t);
                if (result != null) return result;
            }
        return null;
    }

    public static boolean sectionChanged(SectionRenderDispatcher.RenderSection section) {
        invalidateSectionShape(section.getOrigin());
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Spatial w && w.sectionChanged(section)) return true;
        return false;
    }

    /** Shape dependencies also change where no RenderSection currently owns geometry. */
    public static void invalidateSectionShape(BlockPos position) {
        BlockPos origin =
                new BlockPos(
                        (position.getX() >> 4) << 4,
                        (position.getY() >> 4) << 4,
                        (position.getZ() >> 4) << 4);
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.World w) w.sectionInvalidated(origin);
    }

    public static boolean isExternal(SectionRenderDispatcher.RenderSection section) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Spatial w && w.isExternal(section)) return true;
        return false;
    }

    public static void nearSectionEmpty(int slot, int x, int y, int z) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Spatial w) w.nearSectionEmpty(slot, x, y, z);
    }

    public static VertexConsumer bulkConsumer(VertexConsumer c, boolean pathTraced) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g) c = g.bulkConsumer(c, pathTraced);
        return c;
    }

    public static VertexConsumer quadConsumer(ByteBufferBuilder allocator, RenderType layer) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g) {
                var c = g.quadConsumer(allocator, layer);
                if (c != null) return c;
            }
        return null;
    }

    public static boolean cameraRelative(RenderType layer) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g && g.cameraRelative(layer)) return true;
        return false;
    }

    public static int cameraRelativeIdentity(Object owner) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g) {
                var value = g.cameraRelativeIdentity(owner);
                if (value != null) return value;
            }
        return System.identityHashCode(owner);
    }

    public static boolean prioritySurface(RenderType layer) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g && g.prioritySurface(layer)) return true;
        return false;
    }

    public static boolean supportsColourState(RenderType layer) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Geometry g && g.supportsColourState(layer))
                return true;
        return false;
    }

    public static boolean skipState(Object state) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Raster r && r.skipState(state)) return true;
        return false;
    }

    public static boolean supportsStaticState(RenderType layer) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Raster raster
                    && !raster.supportsStaticState(layer)) return false;
        return true;
    }

    public static void sleepingPose(
            net.minecraft.world.entity.LivingEntity entity,
            com.mojang.blaze3d.vertex.PoseStack pose) {
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Spatial spatial)
                spatial.sleepingPose(entity, pose);
    }

    public static org.joml.Quaternionf nameOrientation(
            Entity entity, float partial, org.joml.Quaternionf camera) {
        var result = camera;
        for (var source : RenderPlatform.sources())
            if (source instanceof SourceIntegration.Spatial spatial)
                result = spatial.nameOrientation(entity, partial, result);
        return result;
    }

    public static boolean validateDraw(
            ShaderInstance shader,
            VertexFormat format,
            VertexFormat.Mode mode,
            int vertices,
            int indices) {
        try {
            for (var source : RenderPlatform.sources())
                if (source instanceof SourceIntegration.Raster raster)
                    raster.validateDraw(shader, format, mode, vertices, indices);
            return true;
        } catch (UnsupportedDraw refusal) {
            com.radiance.client.render.RenderCaptureContract.reportDiscard(
                    "source raster", refusal.getMessage());
            return false;
        }
    }

    public static int lightmap() {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Raster r) {
                int result = r.lightmap();
                if (result >= 0) return result;
            }
        return -1;
    }

    public static boolean compilingOffscreenSection() {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Raster r && r.compilingOffscreenSection())
                return true;
        return false;
    }

    public static void atlas(TextureTasks.Owner o, int w, int h) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Textures t) t.atlas(o, w, h);
    }

    public static void textureUpload(
            TextureTasks.Owner o,
            int level,
            long address,
            int sourceWidth,
            int sourceHeight,
            int channels,
            int skipX,
            int skipY,
            int x,
            int y,
            int w,
            int h) {
        for (var s : RenderPlatform.sources())
            if (s instanceof SourceIntegration.Textures t)
                t.upload(
                        o,
                        level,
                        address,
                        sourceWidth,
                        sourceHeight,
                        channels,
                        skipX,
                        skipY,
                        x,
                        y,
                        w,
                        h);
    }
}
