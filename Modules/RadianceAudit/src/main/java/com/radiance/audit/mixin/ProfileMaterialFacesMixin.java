package com.radiance.audit.mixin;

import com.radiance.client.render.MaterialFaces;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.GlStateBackup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=MaterialFaces.class, remap=false)
public abstract class ProfileMaterialFacesMixin {
    @WrapOperation(method="capture", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/RenderType;setupRenderState()V"))
    private static void audit$setup(RenderType layer, Operation<Void> original) {
        try(var census=com.radiance.audit.ProducerCensus.phase(0); var ignored=FrameProfiler.span(Stage.FACE_SETUP)) { original.call(layer); }
    }
    @WrapOperation(method="capture", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"))
    private static void audit$clear(RenderType layer, Operation<Void> original) {
        try(var census=com.radiance.audit.ProducerCensus.phase(1); var ignored=FrameProfiler.span(Stage.FACE_CLEAR)) { original.call(layer); }
    }
    @WrapOperation(method="capture", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/RenderSystem;backupGlState(Lnet/neoforged/neoforge/client/GlStateBackup;)V"))
    private static void audit$backup(GlStateBackup backup, Operation<Void> original) {
        try(var census=com.radiance.audit.ProducerCensus.phase(2); var ignored=FrameProfiler.span(Stage.FACE_BACKUP)) { original.call(backup); }
    }
    @WrapOperation(method="capture", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/RenderSystem;restoreGlState(Lnet/neoforged/neoforge/client/GlStateBackup;)V"))
    private static void audit$restore(GlStateBackup backup, Operation<Void> original) {
        try(var census=com.radiance.audit.ProducerCensus.phase(3); var ignored=FrameProfiler.span(Stage.FACE_RESTORE)) { original.call(backup); }
    }
}
