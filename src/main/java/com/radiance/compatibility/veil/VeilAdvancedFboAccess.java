package com.radiance.compatibility.veil;

import foundry.veil.api.client.render.framebuffer.AdvancedFboAttachment;

/** Internal state exposed by the Veil 4.3.2 AdvancedFbo implementation mixin. */
public interface VeilAdvancedFboAccess {
    int radiance$getFramebufferId();

    void radiance$setFramebufferId(int id);

    AdvancedFboAttachment[] radiance$getColorAttachments();

    AdvancedFboAttachment radiance$getDepthAttachmentOrNull();

    void radiance$setCurrentDrawBuffers(int[] buffers);
}
