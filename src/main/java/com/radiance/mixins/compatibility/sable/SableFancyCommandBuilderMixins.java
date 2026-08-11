package com.radiance.mixins.compatibility.sable;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.compatibility.sable.SableBufferBridge;
import com.radiance.compatibility.veil.VeilAdapter;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelCommandBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = FancySubLevelCommandBuilder.class, remap = false)
public abstract class SableFancyCommandBuilderMixins {

    @Shadow @Final private int commandBuffer;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"), remap = false)
    private int radiance$allocate() {
        return SableBufferBridge.allocate();
    }

    @Redirect(method = {"<init>", "setup", "clear"}, at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL15C;glBindBuffer(II)V"), remap = false)
    private void radiance$skipBind(int target, int id) {
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL15C;glBufferData(IJI)V"), remap = false)
    private void radiance$allocateStorage(int target, long size, int usage) {
        SableBufferBridge.initialize(this.commandBuffer, Math.toIntExact(size),
            VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT.getValue());
    }

    @Redirect(method = "flush", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/opengl/GL43C;glMultiDrawElementsIndirect(IIJII)V"), remap = false)
    private void radiance$drawIndirect(int mode, int type, long offset, int drawCount,
        int stride) {
        if (mode != 4 || type != 0x1401) {
            throw new IllegalStateException("Unexpected Sable indirect draw format");
        }
        VeilAdapter.drawIndirect(this.commandBuffer, offset, drawCount, stride);
    }

    @Inject(method = "free()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$free(CallbackInfo ci) {
        SableBufferBridge.release(this.commandBuffer);
        ci.cancel();
    }
}
