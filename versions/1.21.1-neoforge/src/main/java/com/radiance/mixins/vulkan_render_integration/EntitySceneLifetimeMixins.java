package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySceneLifetimeMixins
        implements com.radiance.client.vertex.SourceBindings.Owner {
    @org.spongepowered.asm.mixin.Unique
    private com.radiance.client.vertex.SourceBindings radiance$bindings;

    public com.radiance.client.vertex.SourceBindings radiance$bindings() {
        if (radiance$bindings == null)
            radiance$bindings = new com.radiance.client.vertex.SourceBindings();
        return radiance$bindings;
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void radiance$removeSceneOwner(Entity.RemovalReason reason, CallbackInfo ci) {
        com.radiance.api.render.PersistentInstances.removeOwner(this);
        if (radiance$bindings != null) radiance$bindings.retire();
    }

    @Inject(method = "unsetRemoved", at = @At("RETURN"))
    private void radiance$sourceResumed(CallbackInfo ci) {
        if (radiance$bindings != null) radiance$bindings.resume();
    }
}
