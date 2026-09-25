package com.radiance.audit.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.core.SectionPos;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=SectionCompiler.class, priority=2100)
public abstract class ProfileSectionCompilerMixin {
    @WrapMethod(method="compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
    private SectionCompiler.Results audit$compile(SectionPos section,RenderChunkRegion region,VertexSorting sorting,
        SectionBufferBuilderPack pack,List<AddSectionGeometryEvent.AdditionalSectionRenderer> extra,Operation<SectionCompiler.Results> original) {
        var renderSpan=FrameProfiler.span(Stage.CHUNK_COMPILE);
        var worker=renderSpan==null ? FrameProfiler.workerStart() : null;
        try(renderSpan;worker) { return original.call(section,region,sorting,pack,extra); }
    }
}
