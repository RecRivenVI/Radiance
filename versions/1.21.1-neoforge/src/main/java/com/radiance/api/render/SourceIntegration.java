package com.radiance.api.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.texture.TextureTasks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Source-side hooks for the current Minecraft target. Calls preserve the original render-thread
 * order. Inputs are borrowed for the duration of the call; a source retaining data must copy it.
 * Resource/instance packets use the source-independent render contracts, not these Minecraft types.
 */
public interface SourceIntegration {
    /**
     * Registration does not imply that a particular source draw has been encountered or verified.
     */
    String id();

    default void resourcesInvalidated() {}

    default void worldClosed() {}

    interface World extends SourceIntegration {
        /** Notification precedes any spatial adapter consuming the section change. */
        default void sectionInvalidated(BlockPos origin) {}

        default void blockEntityChanged(BlockEntity entity) {}

        default void frameStarted() {}

        default void update(ClientLevel level, Camera camera, float partialTick) {}

        default Frame begin(FrameInput input) {
            return Frame.EMPTY;
        }

        /** Furthest source coverage radius for direct imaging; does not cull transport geometry. */
        default float imagingDistance(float vanillaDistance) {
            return vanillaDistance;
        }

        default void blocks(ClientLevel level, float partialTick) {}

        default void blockEntities(
                ClientLevel level,
                Camera camera,
                BlockEntityRenderDispatcher dispatcher,
                float partialTick) {}

        default void worldEffects(
                Camera camera,
                net.minecraft.client.renderer.GameRenderer renderer,
                Matrix4f modelView,
                Matrix4f projection) {}

        default void debug(ClientLevel level, Camera camera, float partialTick) {}

        default void prepare(
                ClientLevel level, Camera camera, Frustum frustum, boolean spectator) {}

        default boolean ownsEntity(ClientLevel level, Entity entity) {
            return false;
        }

        default boolean ownsBlockEntity(ClientLevel level, BlockEntity entity) {
            return false;
        }
    }

    record FrameInput(
            LevelRenderer renderer,
            ClientLevel level,
            RenderBuffers buffers,
            Matrix4fc modelView,
            Matrix4f projection,
            Camera camera,
            DeltaTracker delta) {}

    interface Frame {
        Frame EMPTY = new Frame() {};

        default void afterEntities() {}

        default void beforeCrumbling(
                Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress) {}

        default boolean active() {
            return false;
        }
    }

    interface Spatial extends SourceIntegration {
        default Object blockSource(BlockEntity entity) {
            return null;
        }

        default void sleepingPose(
                net.minecraft.world.entity.LivingEntity entity,
                com.mojang.blaze3d.vertex.PoseStack pose) {}

        default Quaternionf nameOrientation(Entity entity, float partialTick, Quaternionf camera) {
            return camera;
        }

        default EntityTransform entityTransform(
                Entity entity, float partialTick, double x, double y, double z) {
            return null;
        }

        default Outline outline(
                ClientLevel level, Camera camera, BlockPos position, float partialTick) {
            return null;
        }

        default boolean sectionChanged(SectionRenderDispatcher.RenderSection section) {
            return false;
        }

        default boolean isExternal(SectionRenderDispatcher.RenderSection section) {
            return false;
        }

        default void nearSectionEmpty(int slot, int x, int y, int z) {}
    }

    record EntityTransform(
            Vector3d position,
            Quaternionf orientation,
            boolean rotatesModel,
            com.radiance.render.contract.LineContract.SourceKey lineSource,
            RenderSpace.Placement placement) {
        public EntityTransform(
                Vector3d position,
                Quaternionf orientation,
                boolean rotatesModel,
                com.radiance.render.contract.LineContract.SourceKey lineSource) {
            this(position, orientation, rotatesModel, lineSource, null);
        }
    }

    interface Outline extends AutoCloseable {
        Camera camera();

        Quaternionf orientation();

        Vec3 localCameraPosition();

        Vector3d worldPosition();

        void applyEventTransform(PoseStack pose, Vec3 camera);

        void applyLocalTransform(PoseStack pose, BlockPos position);

        @Override
        void close();
    }

    interface Geometry extends SourceIntegration {
        /** False only for a source's redundant raster-only prepass, not its visible surface. */
        default boolean sectionLayer(
                net.minecraft.world.level.block.state.BlockState state, RenderType layer) {
            return true;
        }

        enum Overlay {
            NONE,
            OPAQUE,
            SOURCE_OVER
        }

        /** Source-defined ordered surface semantics; NONE leaves the standard material intact. */
        default Overlay orderedOverlay(RenderType layer) {
            return Overlay.NONE;
        }

        /** A source only admits programs whose material behavior it has actually translated. */
        default boolean supportsWorldProgram(String program) {
            return false;
        }

        /** Keep a CPU/raster copy for an actual offscreen section consumer, not world raster. */
        default boolean retainOffscreenLayer(RenderType layer) {
            return false;
        }

        /** World PT excludes source wrappers used only to simulate raster illumination. */
        default VertexConsumer bulkConsumer(VertexConsumer consumer, boolean pathTraced) {
            return consumer;
        }

        /** Null leaves the ordinary canonical consumer in charge; it does not discard a draw. */
        default VertexConsumer quadConsumer(ByteBufferBuilder allocator, RenderType layer) {
            return null;
        }

        default boolean cameraRelative(RenderType layer) {
            return false;
        }

        default Integer cameraRelativeIdentity(Object owner) {
            return null;
        }

        default boolean prioritySurface(RenderType layer) {
            return false;
        }

        default boolean supportsColourState(RenderType layer) {
            return false;
        }
    }

    interface Raster extends SourceIntegration {
        default boolean supportsStaticState(RenderType layer) {
            return true;
        }

        default boolean skipState(Object state) {
            return false;
        }

        default void validateDraw(
                ShaderInstance shader,
                VertexFormat format,
                VertexFormat.Mode mode,
                int vertexCount,
                int indexCount) {}

        default int lightmap() {
            return -1;
        }

        default boolean compilingOffscreenSection() {
            return false;
        }
    }

    interface Textures extends SourceIntegration {
        default void atlas(TextureTasks.Owner owner, int width, int height) {}

        /** The address is a borrowed CPU upload span owned by TextureTasks, never a GPU address. */
        default void upload(
                TextureTasks.Owner owner,
                int level,
                long address,
                int sourceWidth,
                int sourceHeight,
                int channels,
                int skipX,
                int skipY,
                int x,
                int y,
                int width,
                int height) {}
    }
}
