package com.radiance.audit.mixin;

import com.radiance.api.render.LocalModelBatch;
import com.radiance.api.render.PersistentInstances;
import com.radiance.api.render.RenderSpace;
import com.radiance.client.texture.TextureTasks;
import com.radiance.render.contract.DrawStateSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retire a real source between unchanged selection and the same batch's native submission. */
@Mixin(value = LocalModelBatch.class, remap = false)
public abstract class PersistentSelectionRegressionMixin {
    @Shadow @Final private ByteBuffer records;
    @Shadow private int count;
    @Unique private static boolean radianceAudit$exercised;
    @Unique private long radianceAudit$retired;

    @Inject(
            method =
                    "selectUnchanged(JLjava/lang/Object;JILcom/radiance/client/texture/TextureTasks$Owner;IDDDILorg/joml/Matrix4fc;Ljava/lang/String;Lcom/radiance/render/contract/DrawStateSnapshot;Lcom/radiance/api/render/RenderSpace;Lcom/radiance/api/render/LocalSurface;I)Z",
            at = @At("RETURN"))
    private void radianceAudit$retireSelected(
            long id,
            Object owner,
            long model,
            int type,
            TextureTasks.Owner texture,
            int vertices,
            double x,
            double y,
            double z,
            int mask,
            Matrix4fc transform,
            String group,
            DrawStateSnapshot colour,
            RenderSpace space,
            com.radiance.api.render.LocalSurface surface,
            int coordinates,
            CallbackInfoReturnable<Boolean> ci) {
        if (radianceAudit$exercised || !ci.getReturnValueZ()) return;
        radianceAudit$exercised = true;
        radianceAudit$retired = id;
        PersistentInstances.removeOwner(owner);
        if (PersistentInstances.known(id))
            throw new IllegalStateException("Selected source was not retired");
    }

    @Inject(method = "submitChanges", at = @At("HEAD"))
    private void radianceAudit$unchangedVersion(CallbackInfo ci) {
        if (radianceAudit$retired == 0) return;
        for (int i = 0; i < count; i++)
            if (records.getLong(i * com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES + 8)
                    == radianceAudit$retired)
                throw new IllegalStateException("Retirement regression overlapped a replacement");
    }

    @Inject(method = "submitChanges", at = @At("RETURN"))
    private void radianceAudit$accepted(CallbackInfo ci) {
        if (radianceAudit$retired == 0) return;
        try {
            Files.writeString(
                    Path.of(System.getProperty("validation.result"))
                            .resolve("persistent-selection-regression.json"),
                    "{\"passed\":true,\"selectedAndRemoved\":"
                            + radianceAudit$retired
                            + ",\"scope\":\"real Java batch and native submission, not visual acceptance\"}\n");
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "PERSISTENT_SELECTION_REGRESSION PASS selected-and-removed={}",
                            radianceAudit$retired);
            radianceAudit$retired = 0;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
