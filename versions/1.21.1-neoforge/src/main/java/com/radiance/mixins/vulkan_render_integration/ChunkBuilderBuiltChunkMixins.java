package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.api.render.SourceHooks;
import com.radiance.client.proxy.world.ChunkProxy;
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

    @Shadow @Final SectionRenderDispatcher this$0;
    @Shadow private boolean playerChanged;

    @Unique
    public SectionRenderDispatcher radiance$getChunkBuilder() {
        return this$0;
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object cancelCollect(
            Stream<?> stream,
            Collector<?, ?, ?> collector,
            SectionRenderDispatcher dispatcher,
            int index,
            int originX,
            int originY,
            int originZ) {
        return stream.collect((Collector) collector);
    }

    @Inject(method = "reset()V", at = @At("TAIL"))
    private void markExternalSectionDirtyAfterReset(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
                (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.removeBlockEntitySection(self);
        SourceHooks.sectionChanged(self);
    }

    @WrapMethod(method = "setDirty(Z)V")
    private void addToRebuildGridScheduleRebuild(boolean playerChanged, Operation<Void> original) {
        SectionRenderDispatcher.RenderSection self =
                (SectionRenderDispatcher.RenderSection) (Object) this;
        boolean wasDirty = self.isDirty();
        ChunkProxy.sectionChanged(self);
        original.call(playerChanged);
        if (SourceHooks.sectionChanged(self)) {
            return;
        }
        boolean interactive =
                playerChanged
                        || com.radiance.client.proxy.world.PlayerSectionUpdates.request(
                                        self.getOrigin())
                                != 0;
        if (wasDirty) {
            if (interactive) ChunkProxy.promoteRebuild(self);
            return;
        }
        ChunkProxy.enqueueRebuild(self, interactive);
    }

    @Inject(method = "setOrigin(III)V", at = @At(value = "TAIL"))
    private void syncNativeChunkSlot(int x, int y, int z, CallbackInfo ci) {
        // This flag belongs to the old coordinate, not the reusable RenderSection slot.
        playerChanged = false;
        SectionRenderDispatcher.RenderSection self =
                (SectionRenderDispatcher.RenderSection) (Object) this;
        if (SourceHooks.isExternal(self)) {
            return;
        }
        ChunkProxy.relocateSection(
                self, self.getOrigin().getX(), self.getOrigin().getY(), self.getOrigin().getZ());
    }

    @Inject(
            method = "releaseBuffers()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;reset()V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
                (SectionRenderDispatcher.RenderSection) (Object) this;
        com.radiance.client.render.SectionRasterStorage.discard(self);
    }
}
