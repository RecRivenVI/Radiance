package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.compatibility.sable.SableRenderSectionCompatibility;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    @Shadow
    @Final
    SectionRenderDispatcher this$0;

    @Unique
    public SectionRenderDispatcher radiance$getChunkBuilder() {
        return this$0;
    }

    @Unique
    private boolean radiance$wasDirtyBeforeSet;

    @Redirect(method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object cancelCollect(Stream<?> stream, Collector<?, ?, ?> collector,
        SectionRenderDispatcher dispatcher, int index, int originX, int originY, int originZ) {
        return SableRenderSectionCompatibility.collectConstructorStream(index, stream, collector);
    }

    @Inject(method = "reset()V", at = @At("TAIL"))
    private void markExternalSectionDirtyAfterReset(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.removeBlockEntitySection(self);
        SableRenderSectionCompatibility.handleDirty(self);
    }

    @Inject(method = "setDirty(Z)V", at = @At("HEAD"))
    private void captureDirtyState(boolean playerChanged, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        radiance$wasDirtyBeforeSet = self.isDirty();
    }

    @Inject(method = "setDirty(Z)V", at = @At("TAIL"))
    private void addToRebuildGridScheduleRebuild(boolean playerChanged, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        if (SableRenderSectionCompatibility.handleDirty(self)) {
            return;
        }
        boolean interactive = playerChanged || com.radiance.client.proxy.world.PlayerSectionUpdates.request(self.getOrigin()) != 0;
        if (radiance$wasDirtyBeforeSet) {
            if (interactive) ChunkProxy.promoteRebuild(self);
            return;
        }
        ChunkProxy.enqueueRebuild(self, interactive);
    }

    @Inject(method = "setOrigin(III)V", at = @At(value = "TAIL"))
    private void syncNativeChunkSlot(int x, int y, int z, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        if (SableRenderSectionCompatibility.isExternal(self)) {
            return;
        }
        ChunkProxy.relocateSection(self, self.getOrigin().getX(), self.getOrigin().getY(),
            self.getOrigin().getZ());
    }

    @Inject(method = "releaseBuffers()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;reset()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        com.radiance.client.render.SectionRasterStorage.discard(self);
    }
}
