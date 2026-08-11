package com.radiance.mixins.compatibility.aeronautics;

import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import dev.eriksonn.aeronautics.mixin.levitite.ChunkRenderTypeSetAccessor;
import com.radiance.compatibility.veil.VeilAdapter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores Aeronautics' custom chunk layers before its NeoForge setup creates the layer bitset.
 */
@Pseudo
@Mixin(targets =
    "dev.eriksonn.aeronautics.neoforge.events.AeroNeoForgeClientEvents$ModBusEvents",
    remap = false)
public abstract class AeronauticsNeoForgeClientSetupMixins {

    @Inject(method = "clientSetup", at = @At("HEAD"), remap = false)
    private static void radiance$registerCustomChunkLayers(FMLClientSetupEvent event,
        CallbackInfo ci) {
        Set<RenderType> layers = new LinkedHashSet<>();
        layers.add(AeroRenderTypes.levitite());
        layers.add(AeroRenderTypes.levititeGhosts());
        VeilAdapter.setBlockLayers(layers);

        List<RenderType> chunkLayers = new ArrayList<>(RenderType.chunkBufferLayers());
        for (RenderType layer : layers) {
            if (!chunkLayers.contains(layer)) {
                chunkLayers.add(layer);
            }
        }
        for (int index = 0; index < chunkLayers.size(); index++) {
            chunkLayers.get(index).chunkLayerId = index;
        }

        // ChunkRenderTypeSet initializes before this event and therefore caches the old vanilla
        // list. Synchronize the same caches Aeronautics refreshes later, but before its first
        // ChunkRenderTypeSet.of call validates the custom layer ids.
        ChunkRenderTypeSetAccessor.setChunkRenderTypesList(chunkLayers);
        ChunkRenderTypeSetAccessor.setChunkRenderTypes(chunkLayers.toArray(RenderType[]::new));
        ((ChunkRenderTypeSetAccessor) (Object) ChunkRenderTypeSet.all()).getBits()
            .set(0, chunkLayers.size());
    }
}
