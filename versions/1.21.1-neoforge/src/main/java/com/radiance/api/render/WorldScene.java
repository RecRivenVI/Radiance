package com.radiance.api.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.*;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeCompositeStateAccessor;
import com.radiance.mixins.vulkan_render_integration.accessor.RenderTypeShaderStateAccessor;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

/** Synchronous bridge from a RenderType-owned MeshData draw to the native world BLAS batch. */
public final class WorldScene {

    public enum StageKey {
        AFTER_SKY,
        AFTER_SOLID_BLOCKS,
        AFTER_CUTOUT_MIPPED_BLOCKS,
        AFTER_CUTOUT_BLOCKS,
        AFTER_ENTITIES,
        AFTER_BLOCK_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_TRIPWIRE_BLOCKS,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_LEVEL,
        DIMENSION_RENDER_SKY,
        DIMENSION_RENDER_CLOUDS,
        DIMENSION_RENDER_WEATHER,
        UNKNOWN;

        public static StageKey from(String name) {
            if (name == null) return UNKNOWN;
            String compact = name.replace("_", "").toLowerCase();
            if (compact.endsWith("rendersky")) return DIMENSION_RENDER_SKY;
            if (compact.endsWith("renderclouds")) return DIMENSION_RENDER_CLOUDS;
            if (compact.endsWith("rendersnowandrain") || compact.endsWith("renderweather")) {
                return DIMENSION_RENDER_WEATHER;
            }
            String normalized =
                    name.toUpperCase()
                            .replace("RENDERLEVELSTAGEEVENT.STAGE.", "")
                            .replace("RENDER_", "");
            if (normalized.equals("SKY")) return DIMENSION_RENDER_SKY;
            if (normalized.equals("CLOUDS")) return DIMENSION_RENDER_CLOUDS;
            if (normalized.equals("SNOWANDRAIN") || normalized.equals("WEATHER")) {
                return DIMENSION_RENDER_WEATHER;
            }
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum CoordinateSpace {
        WORLD(Constants.Coordinates.WORLD),
        CAMERA(Constants.Coordinates.CAMERA),
        CAMERA_SHIFT(Constants.Coordinates.CAMERA_SHIFT),
        SKYBOX(null);

        private final Constants.Coordinates nativeCoordinate;

        CoordinateSpace(Constants.Coordinates nativeCoordinate) {
            this.nativeCoordinate = nativeCoordinate;
        }
    }

    public enum TargetKind {
        DEFAULT_WORLD,
        EXPLICIT_CUSTOM_TARGET,
        SKYBOX
    }

    public enum Status {
        ACCEPTED,
        UNSUPPORTED,
        STALE,
        NOT_WORLD_TARGET
    }

    public record Submission(Status status, String reason) {
        public boolean consumed() {
            return status != Status.NOT_WORLD_TARGET;
        }
    }

    public record FrameContext(
            Object levelIdentity,
            String dimensionKey,
            long worldToken,
            long frameToken,
            long resourceGeneration,
            Vec3 cameraOrigin) {}

    public record StageContext(
            FrameContext frame,
            long stageToken,
            StageKey stage,
            CoordinateSpace coordinateSpace,
            Vec3 coordinateOrigin,
            TargetKind targetKind,
            String sourceId) {}

    private static final int NATIVE_ACCEPTED = 1;
    private static final int NATIVE_STALE = 2;
    private static final int NATIVE_UNSUPPORTED = 3;
    private static final int NATIVE_AUDIT_BUILT = 3;
    private static final int NATIVE_AUDIT_ROLLED_BACK = 4;
    private static final int NATIVE_AUDIT_STALE = 5;
    // Match the transparent_only.rchit modes used with the "lightning" hit group:
    // 23 blends source-over (translucent overlays), 24 writes unlit opaque color (outline strokes).
    private static final int ALPHA_MODE_ORDERED_TRANSLUCENT =
            com.radiance.render.contract.RenderAbi.ALPHA_BLENDED_SURFACE;
    private static final int ALPHA_MODE_ORDERED_OPAQUE =
            com.radiance.render.contract.RenderAbi.ALPHA_ORDERED_OPAQUE;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final ThreadLocal<FrameToken> CURRENT_FRAME = new ThreadLocal<>();
    private static final ThreadLocal<Deque<StageToken>> STAGES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<Long> PENDING_NATIVE_AUDITS = ConcurrentHashMap.newKeySet();
    private static Object activeLevelIdentity;
    private static String activeDimensionKey = "";
    private static long worldSequence;
    private static long frameSequence;
    private static long resourceGeneration = 1;
    private static long stageSequence;

    private WorldScene() {}

    public static FrameToken beginFrame(ClientLevel level, Vec3 cameraOrigin) {
        flushNativeAuditOutcomes();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cameraOrigin, "cameraOrigin");
        if (CURRENT_FRAME.get() != null) {
            throw new IllegalStateException("World mesh frame is already active on this thread");
        }
        FrameContext context;
        synchronized (LIFECYCLE_LOCK) {
            String dimensionKey = level.dimension().location().toString();
            if (requiresNewWorldToken(
                    activeLevelIdentity, activeDimensionKey, level, dimensionKey)) {
                activeLevelIdentity = level;
                activeDimensionKey = dimensionKey;
                worldSequence++;
            }
            context =
                    new FrameContext(
                            level,
                            dimensionKey,
                            worldSequence,
                            ++frameSequence,
                            resourceGeneration,
                            cameraOrigin);
        }
        int nativeStatus =
                EntityProxy.beginWorldMeshFrame(
                        context.worldToken(), context.frameToken(), context.resourceGeneration());
        FrameToken token =
                new FrameToken(context, Thread.currentThread(), nativeStatus == NATIVE_ACCEPTED);
        CURRENT_FRAME.set(token);
        if (nativeStatus == NATIVE_ACCEPTED)
            com.radiance.api.render.PersistentInstances.flushRemovals();
        return token;
    }

    /** The level renderer may participate in the longer GameRenderer frame. */
    public static FrameLease joinOrBeginFrame(ClientLevel level, Vec3 cameraOrigin) {
        FrameToken current = CURRENT_FRAME.get();
        if (current == null) return new FrameLease(beginFrame(level, cameraOrigin), true);
        if (current.closed || current.context.levelIdentity() != level) {
            throw new IllegalStateException("Cannot join a different world mesh frame");
        }
        return new FrameLease(current, false);
    }

    public static final class FrameLease implements AutoCloseable {
        private final FrameToken token;
        private final boolean owned;

        private FrameLease(FrameToken token, boolean owned) {
            this.token = token;
            this.owned = owned;
        }

        public void commit() {
            if (owned) token.commit();
        }

        @Override
        public void close() {
            if (owned) token.close();
        }
    }

    public static StageToken enterStage(
            StageKey stage,
            CoordinateSpace coordinateSpace,
            Vec3 origin,
            TargetKind targetKind,
            String sourceId) {
        FrameToken frameToken = CURRENT_FRAME.get();
        if (frameToken == null || frameToken.closed) {
            return StageToken.stale("no active world frame");
        }
        if (frameToken.context.resourceGeneration() != currentResourceGeneration()) {
            return StageToken.stale("resource generation changed during the frame");
        }
        StageContext context =
                new StageContext(
                        frameToken.context,
                        ++stageSequence,
                        Objects.requireNonNullElse(stage, StageKey.UNKNOWN),
                        Objects.requireNonNull(coordinateSpace, "coordinateSpace"),
                        Objects.requireNonNull(origin, "origin"),
                        Objects.requireNonNull(targetKind, "targetKind"),
                        Objects.requireNonNullElse(sourceId, "unknown"));
        boolean nativeOpen =
                frameToken.nativeOpen
                        && targetKind == TargetKind.DEFAULT_WORLD
                        && EntityProxy.beginWorldMeshStage(
                                        context.frame().worldToken(),
                                        context.frame().frameToken(),
                                        context.frame().resourceGeneration(),
                                        context.stageToken(),
                                        context.stage().ordinal())
                                == NATIVE_ACCEPTED;
        StageToken token = new StageToken(context, Thread.currentThread(), nativeOpen, null);
        STAGES.get().push(token);
        return token;
    }

    static int nextLineSequence(com.radiance.render.contract.LineContract.SourceKey source) {
        StageToken stage = STAGES.get().peek();
        if (stage == null || stage.closed) throw new IllegalStateException("No live line stage");
        if (stage.lineSequences == null) stage.lineSequences = new java.util.HashMap<>();
        return stage.lineSequences.merge(source.id(), 1, Integer::sum);
    }

    public static boolean isDefaultTarget() {
        var stage = currentStage();
        return stage != null
                && stage.targetKind() == TargetKind.DEFAULT_WORLD
                && FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) == 0;
    }

    public static StageContext currentStage() {
        StageToken token = STAGES.get().peek();
        return token == null || token.closed ? null : token.context;
    }

    /** Includes source callbacks outside named stages; raster previews keep their own scope. */
    public static boolean capturingWorld() {
        FrameToken token = CURRENT_FRAME.get();
        return token != null && !token.closed;
    }

    static long activeFrameToken() {
        FrameToken token = CURRENT_FRAME.get();
        return token == null || token.closed ? -1 : token.context.frameToken();
    }

    public static Submission submit(
            RenderType renderType, MeshData mesh, Object sourceOwner, String contentName) {
        return submit(renderType, mesh, sourceOwner, contentName, Constants.RayTracingFlags.WORLD);
    }

    /**
     * Already interpreted, deforming local quads. CPU input is copied before return; fixed quad
     * topology and source identity retain the ordinary dynamic history/retirement contract. The
     * caller owns the buffer and must supply a unique stable identity in its live world.
     */
    public static Submission submitDeformingQuads(
            int identity,
            ByteBuffer vertices,
            int count,
            com.radiance.client.texture.TextureTasks.Owner texture,
            Vec3 origin) {
        Objects.requireNonNull(texture);
        Objects.requireNonNull(origin);
        if (!vertices.isDirect()
                || count <= 0
                || count % 4 != 0
                || vertices.remaining() != Math.multiplyExact(count, 128)
                || !Double.isFinite(origin.x)
                || !Double.isFinite(origin.y)
                || !Double.isFinite(origin.z))
            throw new IllegalArgumentException("Invalid deforming quad input");
        var stage = STAGES.get().peek();
        if (stage == null || stage.closed || !stage.nativeOpen || !isDefaultTarget())
            return new Submission(Status.STALE, "No live world stage");
        var c = stage.context;
        if (c.frame().resourceGeneration() != currentResourceGeneration())
            return new Submission(Status.STALE, "Resource generation changed");
        ByteBuffer shader = MemoryUtil.memUTF8("radiance:interpreted_surface", true);
        ByteBuffer material = MemoryUtil.memUTF8("default|source_deformation", true);
        try {
            int[] status = {NATIVE_STALE};
            TextureProxy.TASKS.use(
                    texture,
                    () ->
                            status[0] =
                                    EntityProxy.queueWorldMeshV3(
                                            c.frame().worldToken(),
                                            c.frame().frameToken(),
                                            c.frame().resourceGeneration(),
                                            c.stageToken(),
                                            c.stage().ordinal(),
                                            Constants.Coordinates.WORLD.getValue(),
                                            origin.x,
                                            origin.y,
                                            origin.z,
                                            identity,
                                            com.radiance.render.contract.RenderAbi.QUERY_WORLD,
                                            com.radiance.render.contract.RenderAbi
                                                    .GEOMETRY_WORLD_TRANSPARENT,
                                            texture.id(),
                                            Constants.VertexFormats.PBR_TRIANGLE.getValue(),
                                            Constants.DrawModes.QUADS.getValue(),
                                            Constants.IndexTypes.INT.getValue(),
                                            count,
                                            count / 4 * 6,
                                            MemoryUtil.memAddress(vertices),
                                            vertices.remaining(),
                                            0,
                                            0,
                                            com.radiance.render.contract.RenderAbi.ALPHA_COVERAGE,
                                            0,
                                            MemoryUtil.memAddress(shader),
                                            MemoryUtil.memAddress(material),
                                            0,
                                            0,
                                            1,
                                            1,
                                            1,
                                            1));
            if (status[0] == NATIVE_ACCEPTED) stage.acceptedCount++;
            return new Submission(
                    status[0] == NATIVE_ACCEPTED ? Status.ACCEPTED : Status.STALE,
                    "Source deformation");
        } finally {
            MemoryUtil.memFree(shader);
            MemoryUtil.memFree(material);
        }
    }

    private static Submission submit(
            RenderType renderType,
            MeshData mesh,
            Object sourceOwner,
            String contentName,
            Constants.RayTracingFlags visibility) {
        return submit(renderType, mesh, sourceOwner, contentName, visibility, null, null);
    }

    private static Submission submit(
            RenderType renderType,
            MeshData mesh,
            Object sourceOwner,
            String contentName,
            Constants.RayTracingFlags visibility,
            com.radiance.render.contract.LineContract.LineOrigin lineOrigin,
            com.radiance.render.contract.DrawStateSnapshot capturedState) {
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(mesh, "mesh");
        long auditId =
                RenderAuditBridge.beginIntent(
                        "WORLD_MESH",
                        Objects.requireNonNullElse(contentName, renderType.name),
                        renderType.name);
        StageToken stageToken = STAGES.get().peek();
        if (stageToken == null || stageToken.closed || stageToken.context == null) {
            return auditResult(auditId, Status.STALE, "draw has no live world stage token");
        }
        StageContext stage = stageToken.context;
        if (stage.targetKind() != TargetKind.DEFAULT_WORLD) {
            return auditResult(
                    auditId,
                    Status.UNSUPPORTED,
                    "stage target " + stage.targetKind() + " has no world TLAS consumer");
        }
        if (FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) != 0) {
            return auditResult(
                    auditId, Status.NOT_WORLD_TARGET, "an explicit framebuffer is bound");
        }
        if (!stageToken.nativeOpen
                || stage.frame().resourceGeneration() != currentResourceGeneration()) {
            return auditResult(
                    auditId, Status.STALE, "world/frame/resource generation is no longer current");
        }

        RenderTypeSnapshot snapshot =
                RenderTypeSnapshot.capture(
                        renderType,
                        contentName != null
                                && (contentName.startsWith("radiance/outliner/")
                                        || contentName.startsWith("radiance/ghost/")));
        if (!snapshot.worldTarget) {
            return auditResult(
                    auditId, Status.NOT_WORLD_TARGET, "RenderType declares a raster output target");
        }
        if (snapshot.unsupportedReason != null) {
            return auditResult(auditId, Status.UNSUPPORTED, snapshot.unsupportedReason);
        }
        MeshData.DrawState drawState = mesh.drawState();
        Integer vertexFormat = vertexFormatId(drawState.format());
        if (vertexFormat == null) {
            return auditResult(
                    auditId, Status.UNSUPPORTED, "unsupported vertex format " + drawState.format());
        }
        if (!isSurfaceMode(drawState.mode())) {
            return auditResult(
                    auditId,
                    Status.UNSUPPORTED,
                    "world sink does not reinterpret non-surface mode " + drawState.mode());
        }

        BufferProxy.BufferInfo vertex = BufferProxy.getBufferInfo(mesh.vertexBuffer());
        int expectedVertexBytes =
                Math.multiplyExact(drawState.vertexCount(), drawState.format().getVertexSize());
        if (vertex.size() != expectedVertexBytes) {
            return auditResult(
                    auditId,
                    Status.UNSUPPORTED,
                    "vertex byte count mismatch: expected "
                            + expectedVertexBytes
                            + ", got "
                            + vertex.size());
        }
        ByteBuffer indexBuffer = mesh.indexBuffer();
        long indexAddress = 0L;
        int indexBytes = 0;
        if (indexBuffer != null) {
            BufferProxy.BufferInfo index = BufferProxy.getBufferInfo(indexBuffer);
            indexAddress = index.addr();
            indexBytes = index.size();
            int expectedIndexBytes =
                    Math.multiplyExact(drawState.indexCount(), drawState.indexType().bytes);
            if (indexBytes != expectedIndexBytes) {
                return auditResult(
                        auditId,
                        Status.UNSUPPORTED,
                        "index byte count mismatch: expected "
                                + expectedIndexBytes
                                + ", got "
                                + indexBytes);
            }
        }

        var drawSnapshot =
                com.radiance.adapter.vanilla.VanillaMaterialAdapter.sourceColour(
                        renderType,
                        capturedState == null
                                ? MaterialFaces.captureSnapshot(renderType)
                                : capturedState);
        if (!drawSnapshot.finiteColour())
            return auditResult(auditId, Status.UNSUPPORTED, "non-finite draw colour");
        com.radiance.client.vertex.CapturedTextureUv.apply(mesh, drawSnapshot);
        ByteBuffer shaderKey = MemoryUtil.memUTF8(snapshot.shaderKey, true);
        ByteBuffer materialKey = MemoryUtil.memUTF8(snapshot.materialKey, true);
        ByteBuffer lineStyle = null;
        try {
            if (LineDrawAdapter.isLine(drawState.mode())) {
                lineStyle =
                        MemoryUtil.memCalloc(com.radiance.render.contract.LineContract.STYLE_BYTES);
                var source =
                        lineOrigin == null
                                ? LineSources.current(LineSources.NEOFORGE_EVENT)
                                : lineOrigin.source();
                var widthPolicy =
                        lineOrigin == null
                                ? (drawState.mode() == VertexFormat.Mode.DEBUG_LINES
                                                || drawState.mode()
                                                        == VertexFormat.Mode.DEBUG_LINE_STRIP
                                        ? com.radiance.render.contract.LineContract.WidthPolicy
                                                .DEBUG
                                        : com.radiance.render.contract.LineContract.WidthPolicy
                                                .ORDINARY)
                                : lineOrigin.widthPolicy();
                float lineEmission = lineOrigin == null ? snapshot.emission : lineOrigin.emission();
                if (drawState.format()
                        == com.radiance.client.vertex.CapturedLineVertexConsumer.FORMAT)
                    LineAudit.vertices(source, mesh.vertexBuffer(), drawState.vertexCount());
                var style =
                        LineDrawAdapter.capture(
                                renderType,
                                widthPolicy,
                                lineEmission,
                                visibility.getValue(),
                                drawSnapshot);
                var space =
                        new com.radiance.render.contract.LineContract.DrawSpace(
                                com.radiance.render.contract.LineContract.Coordinates.valueOf(
                                        stage.coordinateSpace().name()),
                                stage.coordinateOrigin().x,
                                stage.coordinateOrigin().y,
                                stage.coordinateOrigin().z,
                                com.radiance.render.contract.LineContract.Basis3.IDENTITY);
                var stamp =
                        new com.radiance.render.contract.LineContract.DrawStamp(
                                stage.frame().worldToken(),
                                stage.frame().frameToken(),
                                stage.frame().resourceGeneration(),
                                stage.stage().ordinal(),
                                source,
                                stageToken.acceptedCount + 1L);
                if (drawState.format()
                        == com.radiance.client.vertex.CapturedLineVertexConsumer.FORMAT) {
                    var request =
                            new com.radiance.render.contract.LineContract.LineRequest(
                                    stamp,
                                    space,
                                    style,
                                    mesh.vertexBuffer(),
                                    drawState.vertexCount());
                    if (!request.valid())
                        return auditResult(auditId, Status.UNSUPPORTED, "invalid line request");
                }
                if (!style.valid())
                    return auditResult(auditId, Status.UNSUPPORTED, "invalid line style");
                LineAudit.style(source, style, space.owner(), Float.NaN);
                com.radiance.render.contract.LineContract.writeStyle(
                        lineStyle, 0, source, style, space.owner());
            }
            int status =
                    EntityProxy.queueWorldMeshV3(
                            stage.frame().worldToken(),
                            stage.frame().frameToken(),
                            stage.frame().resourceGeneration(),
                            stage.stageToken(),
                            stage.stage().ordinal(),
                            stage.coordinateSpace().nativeCoordinate.getValue(),
                            stage.coordinateOrigin().x,
                            stage.coordinateOrigin().y,
                            stage.coordinateOrigin().z,
                            stableSourceId(stage.sourceId(), sourceOwner, renderType, contentName),
                            visibility.getValue(),
                            com.radiance.render.contract.SourceEmission.encode(
                                    MaterialFaces.encode(
                                            snapshot.geometryType,
                                            LineDrawAdapter.isLine(drawState.mode())
                                                    ? 0
                                                    : drawSnapshot.faces()),
                                    com.radiance.adapter.vanilla.VanillaEmissionAdapter.policy(
                                                            renderType,
                                                            com.radiance.render.contract
                                                                    .SourceEmission.Role.ORDINARY,
                                                            true)
                                                    .bits()
                                            & (snapshot.emission == 0
                                                    ? ~com.radiance.render.contract.RenderAbi
                                                            .SOURCE_EMISSION_FORCE
                                                    : -1)),
                            snapshot.textureId,
                            vertexFormat,
                            Constants.DrawModes.getValue(drawState.mode()),
                            Constants.IndexTypes.getValue(drawState.indexType()),
                            drawState.vertexCount(),
                            drawState.indexCount(),
                            vertex.addr(),
                            vertex.size(),
                            indexAddress,
                            indexBytes,
                            snapshot.alphaMode,
                            snapshot.emission,
                            MemoryUtil.memAddress(shaderKey),
                            MemoryUtil.memAddress(materialKey),
                            auditId,
                            lineStyle == null ? 0L : MemoryUtil.memAddress(lineStyle),
                            drawSnapshot.red(),
                            drawSnapshot.green(),
                            drawSnapshot.blue(),
                            drawSnapshot.alpha());
            if (status == NATIVE_ACCEPTED) {
                stageToken.acceptedCount++;
                if (auditId != 0L) {
                    PENDING_NATIVE_AUDITS.add(auditId);
                    RenderAuditBridge.transition(
                            auditId,
                            "NATIVE_QUEUED",
                            "MCVR_WORLD_MESH",
                            "queued for native BLAS/TLAS build",
                            false);
                }
                return new Submission(Status.ACCEPTED, "queued for native BLAS/TLAS build");
            }
            if (status == NATIVE_STALE) {
                return auditResult(
                        auditId, Status.STALE, "native world generation rejected the payload");
            }
            return auditResult(
                    auditId,
                    Status.UNSUPPORTED,
                    status == NATIVE_UNSUPPORTED
                            ? "native format/topology rejected the payload"
                            : "native world sink unavailable (status=" + status + ')');
        } finally {
            MemoryUtil.memFree(shaderKey);
            MemoryUtil.memFree(materialKey);
            if (lineStyle != null) MemoryUtil.memFree(lineStyle);
        }
    }

    private static Submission auditResult(long auditId, Status status, String reason) {
        RenderAuditBridge.transition(auditId, status.name(), "WORLD_MESH", reason, true);
        return new Submission(status, reason);
    }

    /** Polls asynchronous native build results without imposing work when no audit sink exists. */
    public static void flushNativeAuditOutcomes() {
        if (!RenderAuditBridge.enabled() || PENDING_NATIVE_AUDITS.isEmpty()) return;
        for (long auditId : PENDING_NATIVE_AUDITS.toArray(Long[]::new)) {
            int status = EntityProxy.pollWorldMeshAudit(auditId, true);
            String state;
            String detail;
            if (status == NATIVE_AUDIT_BUILT) {
                state = "NATIVE_BUILT";
                detail = "included in the native entity/BLAS build batch";
            } else if (status == NATIVE_AUDIT_ROLLED_BACK) {
                state = "NATIVE_ROLLED_BACK";
                detail = "removed when its stage or frame was not committed";
            } else if (status == NATIVE_AUDIT_STALE) {
                state = "NATIVE_STALE";
                detail = "removed by a world or resource generation change";
            } else {
                continue;
            }
            PENDING_NATIVE_AUDITS.remove(auditId);
            RenderAuditBridge.transition(auditId, state, "MCVR_WORLD_MESH", detail, true);
        }
    }

    public static int submitCaptured(
            StorageVertexConsumerProvider provider, Object owner, String contentPrefix) {
        return submitCaptured(provider, owner, contentPrefix, Constants.RayTracingFlags.WORLD);
    }

    public static int submitCaptured(
            StorageVertexConsumerProvider provider,
            Object owner,
            String contentPrefix,
            Constants.RayTracingFlags visibility) {
        int accepted = 0;
        try {
            provider.endBatch();
            for (Map.Entry<RenderType, VertexConsumer> entry : provider.getLayerBatches()) {
                MeshData mesh = null;
                VertexConsumer consumer = entry.getValue();
                if (consumer instanceof com.radiance.client.vertex.CapturedLineVertexConsumer lines)
                    mesh = lines.build();
                else if (consumer instanceof BufferBuilder builder) mesh = builder.build();
                else if (consumer instanceof PBRVertexConsumer pbr) mesh = pbr.endNullable();
                if (mesh == null) continue;
                try (MeshData ownedMesh = mesh) {
                    Submission result =
                            submit(
                                    entry.getKey(),
                                    ownedMesh,
                                    owner,
                                    contentPrefix + '/' + entry.getKey().name,
                                    visibility,
                                    consumer
                                                    instanceof
                                                    com.radiance.client.vertex
                                                                    .CapturedLineVertexConsumer
                                                            lines
                                            ? lines.origin()
                                            : null,
                                    provider.drawState(consumer));
                    if (result.status() == Status.ACCEPTED) accepted++;
                    else {
                        String reason =
                                result.status() == Status.NOT_WORLD_TARGET
                                        ? "captured provider cannot replay a raster-target RenderType: "
                                                + result.reason()
                                        : result.reason();
                        RenderCaptureContract.reportDiscard("captured world MeshData", reason);
                    }
                }
            }
        } finally {
            provider.close();
        }
        return accepted;
    }

    public static void invalidateResources() {
        com.radiance.api.render.PersistentInstances.invalidate();
        long generation;
        synchronized (LIFECYCLE_LOCK) {
            generation = ++resourceGeneration;
        }
        EntityProxy.invalidateWorldMeshGeneration(generation);
    }

    public static void invalidateWorld() {
        com.radiance.api.render.PersistentInstances.invalidate();
        com.radiance.api.render.RenderPlatform.worldClosed();
        RenderSpace.worldClosed();
        synchronized (LIFECYCLE_LOCK) {
            activeLevelIdentity = null;
            activeDimensionKey = "";
            worldSequence++;
        }
        EntityProxy.invalidateWorldMeshGeneration(currentResourceGeneration());
    }

    private static long currentResourceGeneration() {
        synchronized (LIFECYCLE_LOCK) {
            return resourceGeneration;
        }
    }

    private static boolean isSurfaceMode(VertexFormat.Mode mode) {
        return LineDrawAdapter.isLine(mode)
                || mode == VertexFormat.Mode.TRIANGLES
                || mode == VertexFormat.Mode.QUADS
                || mode == VertexFormat.Mode.TRIANGLE_STRIP
                || mode == VertexFormat.Mode.TRIANGLE_FAN;
    }

    private static Integer vertexFormatId(VertexFormat format) {
        for (Constants.VertexFormats candidate : Constants.VertexFormats.values()) {
            if (candidate.getVertexFormat().equals(format)) return candidate.getValue();
        }
        return null;
    }

    static int stableSourceId(
            String source, Object owner, RenderType renderType, String contentName) {
        int result =
                Objects.hash(
                        source,
                        renderType == null ? "" : renderType.name,
                        Objects.requireNonNullElse(contentName, ""));
        return 31 * result + (owner == null ? 0 : System.identityHashCode(owner));
    }

    static boolean requiresNewWorldToken(
            Object previousIdentity,
            String previousDimension,
            Object nextIdentity,
            String nextDimension) {
        return previousIdentity != nextIdentity
                || !Objects.equals(previousDimension, nextDimension);
    }

    private static final class RenderTypeSnapshot {
        private final int geometryType;
        private final int textureId;
        private final int alphaMode;
        private final float emission;
        private final String shaderKey;
        private final String materialKey;
        private final String unsupportedReason;
        private final boolean worldTarget;

        private RenderTypeSnapshot(
                int geometryType,
                int textureId,
                int alphaMode,
                float emission,
                String shaderKey,
                String materialKey,
                String unsupportedReason,
                boolean worldTarget) {
            this.geometryType = geometryType;
            this.textureId = textureId;
            this.alphaMode = alphaMode;
            this.emission = emission;
            this.shaderKey = shaderKey;
            this.materialKey = materialKey;
            this.unsupportedReason = unsupportedReason;
            this.worldTarget = worldTarget;
        }

        private static RenderTypeSnapshot capture(RenderType renderType, boolean litPreview) {
            if (!(renderType instanceof RenderType.CompositeRenderType composite)) {
                return unsupported("RenderType is not a CompositeRenderType: " + renderType.name);
            }
            RenderTypeCompositeStateAccessor state =
                    (RenderTypeCompositeStateAccessor) (Object) composite.state;
            if (!isMainFrameTarget(state.radiance$getOutputState())) {
                return new RenderTypeSnapshot(0, 0, 0, 0.0F, "", "", null, false);
            }
            Optional<Supplier<ShaderInstance>> shaderSupplier =
                    ((RenderTypeShaderStateAccessor) (Object) state.radiance$getShaderState())
                            .radiance$getShader();
            ShaderInstance shader = shaderSupplier.map(Supplier::get).orElse(null);
            if (shader == null || shader.getName() == null || shader.getName().isBlank()) {
                return unsupported(
                        "RenderType has no concrete shader identity: " + renderType.name);
            }
            String shaderKey = shader.getName();
            if (!isSupportedWorldShader(shaderKey)) {
                return unsupported(
                        "unsupported world shader " + shaderKey + " for " + renderType.name);
            }

            ResourceLocation texture = composite.state.textureState.cutoutTexture().orElse(null);
            int textureId = 0;
            if (texture != null) {
                textureId = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
                if (textureId == 0 || !TextureTracker.GLID2Texture.containsKey(textureId)) {
                    return unsupported("texture is not live in native generation: " + texture);
                }
            }
            int alphaMode =
                    com.radiance.api.render.SourceHooks.prioritySurface(renderType)
                            ? PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW
                            : PBRVertexConsumer.getAlphaMode(renderType);
            float emission =
                    !LineDrawAdapter.isLine(renderType.mode())
                                    && !litPreview
                                    && com.radiance.adapter.vanilla.VanillaEmissionAdapter
                                            .eventForce(renderType, shaderKey)
                            ? 1.0F
                            : 0.0F;
            int geometryType;
            try {
                geometryType = Constants.GeometryTypes.getGeometryType(renderType, true).getValue();
            } catch (RuntimeException exception) {
                return unsupported("unsupported RenderType material state: " + renderType.name);
            }
            // Ordered source surfaces and ghost previews retain their own overlay semantics.
            // The default hit
            // group either lights them, discards coverage alpha or treats "translucent" as
            // refractive glass, so route them through the shared transparent-only group
            // ("lightning") with the unlit modes consumed by world/transparent_only.rchit.
            // Captured previews use ordinary PBR with emission supplied by their vertices.
            boolean orderedOverlay =
                    !LineDrawAdapter.isLine(renderType.mode())
                            && !litPreview
                            && isOrderedOverlay(renderType);
            if (orderedOverlay) {
                alphaMode = orderedOverlayAlphaMode(renderType);
            }
            String materialKey =
                    (orderedOverlay ? "lightning|" : "")
                            + String.join(
                                    "|",
                                    renderType.name,
                                    shaderKey,
                                    texture == null ? "texture=none" : "texture=" + texture,
                                    "alpha=" + alphaMode,
                                    "transparency=" + state.radiance$getTransparencyState(),
                                    "depth=" + state.radiance$getDepthTestState(),
                                    "cull=" + state.radiance$getCullState(),
                                    "lightmap=" + state.radiance$getLightmapState(),
                                    "overlay=" + state.radiance$getOverlayState(),
                                    "layering=" + state.radiance$getLayeringState(),
                                    "output=" + state.radiance$getOutputState(),
                                    "texturing=" + state.radiance$getTexturingState(),
                                    "writeMask=" + state.radiance$getWriteMaskState(),
                                    "line=" + state.radiance$getLineState(),
                                    "colorLogic=" + state.radiance$getColorLogicState());
            return new RenderTypeSnapshot(
                    geometryType,
                    textureId,
                    alphaMode,
                    emission,
                    shaderKey,
                    materialKey,
                    null,
                    true);
        }

        private static RenderTypeSnapshot unsupported(String reason) {
            return new RenderTypeSnapshot(0, 0, 0, 0.0F, "", "", reason, true);
        }

        private static boolean isOrderedOverlay(RenderType renderType) {
            String name = renderType.name;
            return SourceHooks.orderedOverlay(renderType) != SourceIntegration.Geometry.Overlay.NONE
                    || name.equals("translucent")
                    || name.equals("translucent_moving_block")
                    // Radiance's own Outliner face layers reuse the vanilla entity translucent
                    // types.
                    || name.startsWith("entity_translucent");
        }

        private static int orderedOverlayAlphaMode(RenderType renderType) {
            // Outline strokes use the opaque layer type (NO_TRANSPARENCY); drawing them as
            // unlit opaque strokes matches the vanilla block-outline look. Faces and ghost
            // previews keep their texture/vertex alpha as source-over blending.
            return SourceHooks.orderedOverlay(renderType)
                            == SourceIntegration.Geometry.Overlay.OPAQUE
                    ? ALPHA_MODE_ORDERED_OPAQUE
                    : ALPHA_MODE_ORDERED_TRANSLUCENT;
        }

        private static boolean isMainFrameTarget(RenderStateShard.OutputStateShard output) {
            // Radiance resolves every vanilla main-frame output target into the path-traced
            // image; only custom render targets (for example Veil FBOs) stay outside the world
            // sink. Rejecting the dedicated vanilla targets silently dropped translucent ghost
            // blocks (TRANSLUCENT_TARGET) and glint (ITEM_ENTITY_TARGET).
            return output == RenderStateShard.MAIN_TARGET
                    || output == RenderStateShard.OUTLINE_TARGET
                    || output == RenderStateShard.TRANSLUCENT_TARGET
                    || output == RenderStateShard.PARTICLES_TARGET
                    || output == RenderStateShard.WEATHER_TARGET
                    || output == RenderStateShard.CLOUDS_TARGET
                    || output == RenderStateShard.ITEM_ENTITY_TARGET;
        }

        private static boolean isSupportedWorldShader(String key) {
            int namespace = key.indexOf(':');
            String path = namespace < 0 ? key : key.substring(namespace + 1);
            return path.startsWith("rendertype_")
                    || path.startsWith("position_")
                    || SourceHooks.supportsWorldProgram(key);
        }
    }

    public static final class FrameToken implements AutoCloseable {
        private final FrameContext context;
        private final Thread owner;
        private final boolean nativeOpen;
        private boolean committed;
        private boolean closed;

        private FrameToken(FrameContext context, Thread owner, boolean nativeOpen) {
            this.context = context;
            this.owner = owner;
            this.nativeOpen = nativeOpen;
        }

        public FrameContext context() {
            return context;
        }

        public void commit() {
            requireOwner();
            committed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            requireOwner();
            Deque<StageToken> stages = STAGES.get();
            if (!stages.isEmpty()) {
                throw new IllegalStateException("World mesh frame closed with an active stage");
            }
            if (CURRENT_FRAME.get() != this) {
                throw new IllegalStateException("World mesh frames must close in LIFO order");
            }
            try {
                if (nativeOpen && context.resourceGeneration() == currentResourceGeneration()) {
                    RenderSpace.flushUpdates();
                    EntityProxy.endWorldMeshFrame(
                            context.worldToken(),
                            context.frameToken(),
                            context.resourceGeneration(),
                            committed);
                }
                RenderAuditBridge.frameBoundary(
                        context.frameToken(),
                        committed ? "FRAME_COMMITTED" : "FRAME_ABORTED",
                        "world="
                                + context.worldToken()
                                + ", generation="
                                + context.resourceGeneration());
                flushNativeAuditOutcomes();
            } finally {
                CURRENT_FRAME.remove();
                closed = true;
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("World mesh frame closed from another thread");
            }
        }
    }

    public static final class StageToken implements AutoCloseable {
        private final StageContext context;
        private final Thread owner;
        private final boolean nativeOpen;
        private final String staleReason;
        private boolean committed;
        private boolean closed;
        private int acceptedCount;
        private java.util.Map<Integer, Integer> lineSequences;

        private StageToken(
                StageContext context, Thread owner, boolean nativeOpen, String staleReason) {
            this.context = context;
            this.owner = owner;
            this.nativeOpen = nativeOpen;
            this.staleReason = staleReason;
        }

        private static StageToken stale(String reason) {
            return new StageToken(null, Thread.currentThread(), false, reason);
        }

        public int acceptedCount() {
            return acceptedCount;
        }

        public String staleReason() {
            return staleReason;
        }

        public void commit() {
            requireOwner();
            committed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            requireOwner();
            if (context == null) {
                closed = true;
                return;
            }
            Deque<StageToken> stages = STAGES.get();
            if (stages.peek() != this) {
                throw new IllegalStateException("World mesh stages must close in LIFO order");
            }
            try {
                try {
                    WorldLines.finishStage(context, committed);
                } catch (RuntimeException | Error failure) {
                    committed = false;
                    try {
                        if (nativeOpen)
                            EntityProxy.endWorldMeshStage(
                                    context.frame().worldToken(),
                                    context.frame().frameToken(),
                                    context.frame().resourceGeneration(),
                                    context.stageToken(),
                                    false);
                    } catch (RuntimeException | Error cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                if (nativeOpen
                        && context.frame().resourceGeneration() == currentResourceGeneration()) {
                    EntityProxy.endWorldMeshStage(
                            context.frame().worldToken(),
                            context.frame().frameToken(),
                            context.frame().resourceGeneration(),
                            context.stageToken(),
                            committed);
                }
            } finally {
                stages.pop();
                if (stages.isEmpty()) STAGES.remove();
                closed = true;
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("World mesh stage closed from another thread");
            }
        }
    }
}
