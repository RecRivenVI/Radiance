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
        com.radiance.api.render.SourceHooks.blockEntityChanged((BlockEntity) (Object) this);
        if (com.radiance.api.render.BlockEntityPublications.removed((BlockEntity) (Object) this))
            return;
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
}
