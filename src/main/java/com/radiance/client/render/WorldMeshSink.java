package com.radiance.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.world.EntityProxy;
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
public final class WorldMeshSink {

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
            String normalized = name.toUpperCase()
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

    public record FrameContext(Object levelIdentity, String dimensionKey, long worldToken,
                               long frameToken, long resourceGeneration, Vec3 cameraOrigin) {
    }

    public record StageContext(FrameContext frame, long stageToken, StageKey stage,
                               CoordinateSpace coordinateSpace, Vec3 coordinateOrigin,
                               TargetKind targetKind, String sourceId) {
    }

    private static final int NATIVE_ACCEPTED = 1;
    private static final int NATIVE_STALE = 2;
    private static final int NATIVE_UNSUPPORTED = 3;
    private static final int NATIVE_AUDIT_BUILT = 3;
    private static final int NATIVE_AUDIT_ROLLED_BACK = 4;
    private static final int NATIVE_AUDIT_STALE = 5;
    // Match the transparent_only.rchit modes used with the "lightning" hit group:
    // 23 blends source-over (translucent overlays), 24 writes unlit opaque color (outline strokes).
    private static final int ALPHA_MODE_ORDERED_TRANSLUCENT = 23;
    private static final int ALPHA_MODE_ORDERED_OPAQUE = 24;
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

    private WorldMeshSink() {
    }

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
            if (requiresNewWorldToken(activeLevelIdentity, activeDimensionKey, level,
                dimensionKey)) {
                activeLevelIdentity = level;
                activeDimensionKey = dimensionKey;
                worldSequence++;
            }
            context = new FrameContext(level, dimensionKey, worldSequence, ++frameSequence,
                resourceGeneration, cameraOrigin);
        }
        int nativeStatus = EntityProxy.beginWorldMeshFrame(context.worldToken(),
            context.frameToken(), context.resourceGeneration());
        FrameToken token = new FrameToken(context, Thread.currentThread(),
            nativeStatus == NATIVE_ACCEPTED);
        CURRENT_FRAME.set(token);
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

    public static StageToken enterStage(StageKey stage, CoordinateSpace coordinateSpace,
        Vec3 origin, TargetKind targetKind, String sourceId) {
        FrameToken frameToken = CURRENT_FRAME.get();
        if (frameToken == null || frameToken.closed) {
            return StageToken.stale("no active world frame");
        }
        if (frameToken.context.resourceGeneration() != currentResourceGeneration()) {
            return StageToken.stale("resource generation changed during the frame");
        }
        StageContext context = new StageContext(frameToken.context, ++stageSequence,
            Objects.requireNonNullElse(stage, StageKey.UNKNOWN),
            Objects.requireNonNull(coordinateSpace, "coordinateSpace"),
            Objects.requireNonNull(origin, "origin"), Objects.requireNonNull(targetKind, "targetKind"),
            Objects.requireNonNullElse(sourceId, "unknown"));
        boolean nativeOpen = frameToken.nativeOpen && targetKind == TargetKind.DEFAULT_WORLD
            && EntityProxy.beginWorldMeshStage(context.frame().worldToken(),
            context.frame().frameToken(), context.frame().resourceGeneration(),
            context.stageToken(), context.stage().ordinal()) == NATIVE_ACCEPTED;
        StageToken token = new StageToken(context, Thread.currentThread(), nativeOpen, null);
        STAGES.get().push(token);
        return token;
    }

    public static StageContext currentStage() {
        StageToken token = STAGES.get().peek();
        return token == null || token.closed ? null : token.context;
    }

    public static Submission submit(RenderType renderType, MeshData mesh, Object sourceOwner,
        String contentName) {
        return submit(renderType, mesh, sourceOwner, contentName, Constants.RayTracingFlags.WORLD);
    }

    private static Submission submit(RenderType renderType, MeshData mesh, Object sourceOwner,
        String contentName, Constants.RayTracingFlags visibility) {
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(mesh, "mesh");
        long auditId = RenderAuditBridge.beginIntent("WORLD_MESH",
            Objects.requireNonNullElse(contentName, renderType.name), renderType.name);
        StageToken stageToken = STAGES.get().peek();
        if (stageToken == null || stageToken.closed || stageToken.context == null) {
            return auditResult(auditId, Status.STALE, "draw has no live world stage token");
        }
        StageContext stage = stageToken.context;
        if (stage.targetKind() != TargetKind.DEFAULT_WORLD) {
            return auditResult(auditId, Status.UNSUPPORTED,
                "stage target " + stage.targetKind() + " has no world TLAS consumer");
        }
        if (FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) != 0) {
            return auditResult(auditId, Status.NOT_WORLD_TARGET, "an explicit framebuffer is bound");
        }
        if (!stageToken.nativeOpen || stage.frame().resourceGeneration() != currentResourceGeneration()) {
            return auditResult(auditId, Status.STALE,
                "world/frame/resource generation is no longer current");
        }

        RenderTypeSnapshot snapshot = RenderTypeSnapshot.capture(renderType,
            contentName != null && (contentName.startsWith("radiance/outliner/")
                || contentName.startsWith("radiance/ghost/")));
        if (!snapshot.worldTarget) {
            return auditResult(auditId, Status.NOT_WORLD_TARGET,
                "RenderType declares a raster output target");
        }
        if (snapshot.unsupportedReason != null) {
            return auditResult(auditId, Status.UNSUPPORTED, snapshot.unsupportedReason);
        }
        MeshData.DrawState drawState = mesh.drawState();
        Integer vertexFormat = vertexFormatId(drawState.format());
        if (vertexFormat == null) {
            return auditResult(auditId, Status.UNSUPPORTED,
                "unsupported vertex format " + drawState.format());
        }
        if (!isSurfaceMode(drawState.mode())) {
            return auditResult(auditId, Status.UNSUPPORTED,
                "world sink does not reinterpret non-surface mode " + drawState.mode());
        }

        BufferProxy.BufferInfo vertex = BufferProxy.getBufferInfo(mesh.vertexBuffer());
        int expectedVertexBytes = Math.multiplyExact(drawState.vertexCount(),
            drawState.format().getVertexSize());
        if (vertex.size() != expectedVertexBytes) {
            return auditResult(auditId, Status.UNSUPPORTED,
                "vertex byte count mismatch: expected " + expectedVertexBytes + ", got " + vertex.size());
        }
        ByteBuffer indexBuffer = mesh.indexBuffer();
        long indexAddress = 0L;
        int indexBytes = 0;
        if (indexBuffer != null) {
            BufferProxy.BufferInfo index = BufferProxy.getBufferInfo(indexBuffer);
            indexAddress = index.addr();
            indexBytes = index.size();
            int expectedIndexBytes = Math.multiplyExact(drawState.indexCount(),
                drawState.indexType().bytes);
            if (indexBytes != expectedIndexBytes) {
                return auditResult(auditId, Status.UNSUPPORTED,
                    "index byte count mismatch: expected " + expectedIndexBytes + ", got " + indexBytes);
            }
        }

        ByteBuffer shaderKey = MemoryUtil.memUTF8(snapshot.shaderKey, true);
        ByteBuffer materialKey = MemoryUtil.memUTF8(snapshot.materialKey, true);
        try {
            int status = EntityProxy.queueWorldMesh(stage.frame().worldToken(),
                stage.frame().frameToken(), stage.frame().resourceGeneration(), stage.stageToken(),
                stage.stage().ordinal(), stage.coordinateSpace().nativeCoordinate.getValue(),
                stage.coordinateOrigin().x, stage.coordinateOrigin().y, stage.coordinateOrigin().z,
                stableSourceId(stage.sourceId(), sourceOwner, renderType, contentName),
                visibility.getValue(), MaterialFaces.encode(snapshot.geometryType, MaterialFaces.capture(renderType)),
                snapshot.textureId, vertexFormat, Constants.DrawModes.getValue(drawState.mode()),
                Constants.IndexTypes.getValue(drawState.indexType()), drawState.vertexCount(),
                drawState.indexCount(), vertex.addr(), vertex.size(), indexAddress, indexBytes,
                snapshot.alphaMode, snapshot.emission, MemoryUtil.memAddress(shaderKey),
                MemoryUtil.memAddress(materialKey), auditId);
            if (status == NATIVE_ACCEPTED) {
                stageToken.acceptedCount++;
                if (auditId != 0L) {
                    PENDING_NATIVE_AUDITS.add(auditId);
                    RenderAuditBridge.transition(auditId, "NATIVE_QUEUED", "MCVR_WORLD_MESH",
                        "queued for native BLAS/TLAS build", false);
                }
                return new Submission(Status.ACCEPTED, "queued for native BLAS/TLAS build");
            }
            if (status == NATIVE_STALE) {
                return auditResult(auditId, Status.STALE,
                    "native world generation rejected the payload");
            }
            return auditResult(auditId, Status.UNSUPPORTED,
                status == NATIVE_UNSUPPORTED ? "native format/topology rejected the payload"
                    : "native world sink unavailable (status=" + status + ')');
        } finally {
            MemoryUtil.memFree(shaderKey);
            MemoryUtil.memFree(materialKey);
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

    public static int submitCaptured(StorageVertexConsumerProvider provider, Object owner,
        String contentPrefix) {
        return submitCaptured(provider, owner, contentPrefix, Constants.RayTracingFlags.WORLD);
    }

    public static int submitCaptured(StorageVertexConsumerProvider provider, Object owner,
        String contentPrefix, Constants.RayTracingFlags visibility) {
        int accepted = 0;
        try {
            for (Map.Entry<RenderType, VertexConsumer> entry : provider.getLayers().entrySet()) {
                MeshData mesh = null;
                VertexConsumer consumer = entry.getValue();
                if (consumer instanceof BufferBuilder builder) mesh = builder.build();
                else if (consumer instanceof PBRVertexConsumer pbr) mesh = pbr.endNullable();
                if (mesh == null) continue;
                try (MeshData ownedMesh = mesh) {
                    Submission result = submit(entry.getKey(), ownedMesh, owner,
                        contentPrefix + '/' + entry.getKey().name, visibility);
                    if (result.status() == Status.ACCEPTED) accepted++;
                    else {
                        String reason = result.status() == Status.NOT_WORLD_TARGET
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
        long generation;
        synchronized (LIFECYCLE_LOCK) {
            generation = ++resourceGeneration;
        }
        EntityProxy.invalidateWorldMeshGeneration(generation);
    }

    public static void invalidateWorld() {
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
        return mode == VertexFormat.Mode.TRIANGLES || mode == VertexFormat.Mode.QUADS
            || mode == VertexFormat.Mode.TRIANGLE_STRIP || mode == VertexFormat.Mode.TRIANGLE_FAN;
    }

    private static Integer vertexFormatId(VertexFormat format) {
        for (Constants.VertexFormats candidate : Constants.VertexFormats.values()) {
            if (candidate.getVertexFormat().equals(format)) return candidate.getValue();
        }
        return null;
    }

    static int stableSourceId(String source, Object owner, RenderType renderType, String contentName) {
        int result = Objects.hash(source, renderType == null ? "" : renderType.name,
            Objects.requireNonNullElse(contentName, ""));
        return 31 * result + (owner == null ? 0 : System.identityHashCode(owner));
    }

    static boolean requiresNewWorldToken(Object previousIdentity, String previousDimension,
        Object nextIdentity, String nextDimension) {
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

        private RenderTypeSnapshot(int geometryType, int textureId, int alphaMode, float emission,
            String shaderKey, String materialKey, String unsupportedReason, boolean worldTarget) {
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
                return unsupported("RenderType has no concrete shader identity: " + renderType.name);
            }
            String shaderKey = shader.getName();
            if (!isSupportedWorldShader(shaderKey)) {
                return unsupported("unsupported world shader " + shaderKey + " for " + renderType.name);
            }

            ResourceLocation texture = composite.state.textureState.cutoutTexture().orElse(null);
            int textureId = 0;
            if (texture != null) {
                textureId = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
                if (textureId == 0 || !TextureTracker.GLID2Texture.containsKey(textureId)) {
                    return unsupported("texture is not live in native generation: " + texture);
                }
            }
            int alphaMode = com.radiance.compatibility.simulated.SimulatedVertexCompatibility.isLockLayer(renderType)
                ? PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW : PBRVertexConsumer.getAlphaMode(renderType);
            float emission = !litPreview && isEmissiveShader(shaderKey, renderType.name) ? 1.0F : 0.0F;
            int geometryType;
            try {
                geometryType = Constants.GeometryTypes.getGeometryType(renderType, true).getValue();
            } catch (RuntimeException exception) {
                return unsupported("unsupported RenderType material state: " + renderType.name);
            }
            // Catnip outline strokes/faces (physics staff checkerboard and friends) and
            // ghost-block previews are unlit overlays, not physical surfaces. The default hit
            // group either lights them, discards coverage alpha or treats "translucent" as
            // refractive glass, so route them through the shared transparent-only group
            // ("lightning") with the unlit modes consumed by world/transparent_only.rchit.
            // Captured previews use ordinary PBR with emission supplied by their vertices.
            boolean orderedOverlay = !litPreview && isOrderedOverlay(renderType);
            if (orderedOverlay) {
                alphaMode = orderedOverlayAlphaMode(renderType);
            }
            String materialKey = (orderedOverlay ? "lightning|" : "") + String.join("|", renderType.name, shaderKey,
                texture == null ? "texture=none" : "texture=" + texture,
                "alpha=" + alphaMode, "transparency=" + state.radiance$getTransparencyState(),
                "depth=" + state.radiance$getDepthTestState(), "cull=" + state.radiance$getCullState(),
                "lightmap=" + state.radiance$getLightmapState(),
                "overlay=" + state.radiance$getOverlayState(),
                "layering=" + state.radiance$getLayeringState(),
                "output=" + state.radiance$getOutputState(),
                "texturing=" + state.radiance$getTexturingState(),
                "writeMask=" + state.radiance$getWriteMaskState(),
                "line=" + state.radiance$getLineState(),
                "colorLogic=" + state.radiance$getColorLogicState());
            return new RenderTypeSnapshot(geometryType, textureId, alphaMode, emission, shaderKey,
                materialKey, null, true);
        }

        private static RenderTypeSnapshot unsupported(String reason) {
            return new RenderTypeSnapshot(0, 0, 0, 0.0F, "", "", reason, true);
        }

        private static boolean isOrderedOverlay(RenderType renderType) {
            String name = renderType.name;
            return name.startsWith("ponder:outline_solid")
                || name.startsWith("ponder:outline_translucent")
                || name.equals("translucent")
                || name.equals("translucent_moving_block")
                // Radiance's own Outliner face layers reuse the vanilla entity translucent types.
                || name.startsWith("entity_translucent");
        }

        private static int orderedOverlayAlphaMode(RenderType renderType) {
            // Outline strokes use the opaque layer type (NO_TRANSPARENCY); drawing them as
            // unlit opaque strokes matches the vanilla block-outline look. Faces and ghost
            // previews keep their texture/vertex alpha as source-over blending.
            return renderType.name.startsWith("ponder:outline_solid")
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
            return path.startsWith("rendertype_") || path.startsWith("position_")
                || key.equals("create:glowing_shader") || key.equals("create:rendertype/glowing_shader")
                || key.endsWith(":glowing_shader");
        }

        private static boolean isEmissiveShader(String shaderKey, String layerName) {
            return shaderKey.endsWith(":glowing_shader") || shaderKey.contains("emissive")
                || layerName.contains("glowing") || layerName.equals("eyes")
                || layerName.equals("beacon_beam") || layerName.equals("lightning")
                || layerName.equals("dragon_rays") || layerName.equals("energy_swirl");
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
                    EntityProxy.endWorldMeshFrame(context.worldToken(), context.frameToken(),
                        context.resourceGeneration(), committed);
                }
                RenderAuditBridge.frameBoundary(context.frameToken(),
                    committed ? "FRAME_COMMITTED" : "FRAME_ABORTED",
                    "world=" + context.worldToken() + ", generation="
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

        private StageToken(StageContext context, Thread owner, boolean nativeOpen, String staleReason) {
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
                if (nativeOpen
                    && context.frame().resourceGeneration() == currentResourceGeneration()) {
                    EntityProxy.endWorldMeshStage(context.frame().worldToken(),
                        context.frame().frameToken(), context.frame().resourceGeneration(),
                        context.stageToken(), committed);
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
