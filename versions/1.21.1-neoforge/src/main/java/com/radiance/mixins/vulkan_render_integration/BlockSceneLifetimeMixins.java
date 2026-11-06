package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockSceneLifetimeMixins
        implements com.radiance.client.vertex.SourceBindings.Owner {
    @org.spongepowered.asm.mixin.Unique
    private com.radiance.client.vertex.SourceBindings radiance$bindings;

    public com.radiance.client.vertex.SourceBindings radiance$bindings() {
        if (radiance$bindings == null)
            radiance$bindings = new com.radiance.client.vertex.SourceBindings();
        return radiance$bindings;
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void radiance$removeSceneOwner(CallbackInfo ci) {
        var source = (BlockEntity) (Object) this;
        if (!(source.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)) return;
        com.radiance.api.render.SourceHooks.blockEntityChanged(source);
        if (com.radiance.api.render.BlockEntityPublications.removed(source)) return;
        com.radiance.api.render.PersistentInstances.removeOwner(this);
        if (radiance$bindings != null) radiance$bindings.retire();
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void radiance$addSceneOwner(net.minecraft.world.level.Level level, CallbackInfo ci) {
        com.radiance.api.render.SourceHooks.blockEntityChanged((BlockEntity) (Object) this);
    }

    @Inject(method = "clearRemoved", at = @At("RETURN"))
    private void radiance$sourceResumed(CallbackInfo ci) {
        if (radiance$bindings != null) radiance$bindings.resume();
    }

    @org.spongepowered.asm.mixin.Unique
    private void radiance$wake() {
        if (!com.radiance.api.render.BlockEntityPublications.resting()) return;
        var source = (BlockEntity) (Object) this;
        var level = source.getLevel();
        if (level != null && level.isClientSide)
            com.radiance.api.render.BlockEntityPublications.wake(source.getBlockPos());
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void radiance$stateChanged(
            net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
        radiance$wake();
    }

    @Inject(method = "loadWithComponents", at = @At("RETURN"))
    private void radiance$dataLoaded(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo ci) {
        radiance$wake();
    }

    @Inject(method = "setChanged()V", at = @At("RETURN"))
    private void radiance$contentChanged(CallbackInfo ci) {
        radiance$wake();
    }
}
