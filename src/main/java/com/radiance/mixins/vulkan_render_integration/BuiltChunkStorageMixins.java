package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public class BuiltChunkStorageMixins {

    @Shadow
    protected int sectionGridSizeY;

    @Shadow
    protected int sectionGridSizeX;

    @Shadow
    protected int sectionGridSizeZ;

    @Shadow
    protected Level level;

    @Inject(method = "releaseAllBuffers()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "createSections(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.init(i, sectionGridSizeX, sectionGridSizeY, sectionGridSizeZ,
            level.getMinSection());
        return i;
    }

    @Inject(method = "createSections(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;)V",
        at = @At("TAIL"))
    private void queueInitialChunkBuilds(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        ViewArea self = (ViewArea) (Object) this;
        ChunkProxy.storageCreated(self);
    }

    @Inject(method = "repositionCamera(DD)V",
        at = @At(value = "HEAD"))
    private void updateChunkStorageSectionPos(double cameraX, double cameraZ, CallbackInfo ci) {
        ChunkProxy.updateSectionPosNative(
            Mth.floor(cameraX) >> 4,
            level.getMinSection(),
            Mth.floor(cameraZ) >> 4);
    }

    @Inject(method = "repositionCamera(DD)V", at = @At("TAIL"))
    private void seedLoadedChunkColumns(double cameraX, double cameraZ, CallbackInfo ci) {
        ChunkProxy.storageRepositioned((ViewArea) (Object) this, cameraX, cameraZ);
    }
}
