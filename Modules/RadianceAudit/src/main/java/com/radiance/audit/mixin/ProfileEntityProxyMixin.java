package com.radiance.audit.mixin;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;
import com.radiance.client.constant.Constants;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.EntityProxy.EntityRenderDataList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.RenderType;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=EntityProxy.class,remap=false)
public abstract class ProfileEntityProxyMixin {
    @WrapMethod(method="processEntityRenderData")
    private static void audit$produced(StorageVertexConsumerProvider provider,int hash,double x,double y,double z,
        int visibility,int postFlag,int prebuilt,boolean reflect,java.util.function.Function<RenderType,String> names,
        boolean post,EntityRenderDataList data,Operation<Void> original) {
        int first=data.size();
        original.call(provider,hash,x,y,z,visibility,postFlag,prebuilt,reflect,names,post,data);
        com.radiance.audit.ProducerCensus.produced(provider,data,first);
    }
    @WrapOperation(method="queueBuildInternal", at=@At(value="INVOKE", target="Lcom/radiance/client/render/MaterialFaces;capture(Lnet/minecraft/client/renderer/RenderType;)I"))
    private static int audit$faces(RenderType layer, Operation<Integer> original) {
        try(var census=com.radiance.audit.ProducerCensus.face(layer);
            var ignored=FrameProfiler.span(Stage.GEOMETRY_FACE_STATE)) { return original.call(layer); }
    }
    @WrapOperation(method="queueBuildInternal", at=@At(value="INVOKE", target="Lorg/lwjgl/system/MemoryUtil;memAlloc(I)Ljava/nio/ByteBuffer;"))
    private static ByteBuffer audit$allocate(int bytes, Operation<ByteBuffer> original) {
        try(var ignored=FrameProfiler.span(Stage.GEOMETRY_ALLOCATE)) { return original.call(bytes); }
    }
    @WrapMethod(method="freeDirectBuffer")
    private static void audit$free(ByteBuffer buffer, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.GEOMETRY_FREE)) { original.call(buffer); }
    }
    @WrapMethod(method="closeBuiltBuffers")
    private static void audit$closeBuffers(EntityRenderDataList data, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.GEOMETRY_CLOSE)) { original.call(data); }
    }
    @WrapMethod(method="closeStorageVertexConsumerProviders")
    private static void audit$closeProviders(List<StorageVertexConsumerProvider> providers, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.GEOMETRY_CLOSE)) { original.call(providers); }
    }
    @WrapMethod(method="queueEntitiesBuild")
    private static void audit$queueEntitiesBuild(Camera camera, List<Entity> renderedEntities, EntityRenderDispatcher entityRenderDispatcher, DeltaTracker tickCounter, boolean canDrawEntityOutlines, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.ENTITIES)) { original.call(camera,renderedEntities,entityRenderDispatcher,tickCounter,canDrawEntityOutlines); }
    }
    @WrapMethod(method="queueBlockEntitiesRebuild")
    private static Tuple<List<StorageVertexConsumerProvider>, EntityRenderDataList> audit$queueBlockEntitiesRebuild(ViewArea chunks, Set<BlockEntity> noCullingBlockEntities, Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, BlockEntityRenderDispatcher blockEntityRenderDispatcher, float tickDelta, Operation<Tuple<List<StorageVertexConsumerProvider>, EntityRenderDataList>> original) {
        try(var ignored=FrameProfiler.span(Stage.BLOCK_ENTITIES)) { return original.call(chunks,noCullingBlockEntities,blockBreakingProgressions,blockEntityRenderDispatcher,tickDelta); }
    }
    @WrapMethod(method="queueParticleRebuild")
    private static void audit$queueParticleRebuild(Camera camera, float tickDelta, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.PARTICLES)) { original.call(camera,tickDelta); }
    }
    @WrapMethod(method="queueHandRebuild")
    private static void audit$queueHandRebuild(RenderBuffers buffers, float tickDelta, ItemInHandRenderer firstPersonRenderer, float handProjectionScale, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.HAND)) { original.call(buffers,tickDelta,firstPersonRenderer,handProjectionScale); }
    }
    @WrapMethod(method="queueBuildInternal")
    private static void audit$queueBuildInternal(List<StorageVertexConsumerProvider> storageVertexConsumerProviders, EntityRenderDataList entityRenderDataList, float lineWidth, Constants.Coordinates coordinate, boolean normalOffset, boolean closeAfterBuild, Operation<Void> original) {
        try(var census=com.radiance.audit.ProducerCensus.submit(entityRenderDataList);
            var ignored=FrameProfiler.span(Stage.GEOMETRY_MARSHAL)) { original.call(storageVertexConsumerProviders,entityRenderDataList,lineWidth,coordinate,normalOffset,closeAfterBuild); }
    }
    @WrapMethod(method="queueWeatherBuild")
    private static void audit$queueWeatherBuild(ClientLevel world, Camera camera, int ticks, float tickDelta, boolean renderVanillaPrecipitation, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.WEATHER)) { original.call(world,camera,ticks,tickDelta,renderVanillaPrecipitation); }
    }
    @WrapMethod(method="queueDebugGeometry")
    private static void audit$queueDebugGeometry(DebugRenderer debugRenderer, Camera camera, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.DEBUG_GEOMETRY)) { original.call(debugRenderer,camera); }
    }
}
