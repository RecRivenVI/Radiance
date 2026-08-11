package com.radiance.compatibility.voxy;

import com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar;

/** Replace only the GL renderer owner; ingestion and storage mixins remain installed. */
public final class VoxyMixinBootstrap {
    private VoxyMixinBootstrap() {}

    public static void register() {
        MixinCancellerRegistrar.register(
                (targets, mixin) ->
                        mixin.equals("me.cortex.voxy.client.mixin.minecraft.MixinLevelRenderer"));
    }
}
