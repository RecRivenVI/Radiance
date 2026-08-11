package com.radiance.mixins.compatibility.sable;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.compatibility.sable.SableBufferBridge;
import dev.ryanhcode.sable.sublevel.render.fancy.BucketRenderBuffer;
import java.util.BitSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = BucketRenderBuffer.class, remap = false)
public abstract class SableBucketRenderBufferMixins {

    @Shadow private int buffer;
    @Shadow private boolean dirty;
    @Shadow private int maxSize;
    @Shadow private BitSet closedBuckets;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"), remap = false)
    private int radiance$allocate() {
        return SableBufferBridge.allocate();
    }

    @Redirect(method = {"<init>", "clear"}, at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;glBindBuffer(II)V"), remap = false)
    private void radiance$skipBind(int target, int id) {
    }

    @Redirect(method = {"<init>", "clear"}, at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL15C;glBufferData(IJI)V"), remap = false)
    private void radiance$allocateStorage(int target, long size, int usage) {
        SableBufferBridge.initialize(this.buffer, Math.toIntExact(size),
            VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
    }

    @Inject(method = "resize(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$resize(int size, CallbackInfo ci) {
        SableBufferBridge.initialize(this.buffer, Math.multiplyExact(size, 8),
            VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        BitSet previous = this.closedBuckets;
        this.maxSize = size;
        this.closedBuckets = new BitSet(size);
        this.closedBuckets.or(previous);
        this.dirty = true;
        ci.cancel();
    }

    @Inject(method = "free()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$free(CallbackInfo ci) {
        SableBufferBridge.release(this.buffer);
        ci.cancel();
    }
}
