package com.radiance.mixins.vulkan_render_integration.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface RenderTypeCompositeStateAccessor {

    @Accessor("shaderState")
    RenderStateShard.ShaderStateShard radiance$getShaderState();

    @Accessor("transparencyState")
    RenderStateShard.TransparencyStateShard radiance$getTransparencyState();

    @Accessor("depthTestState")
    RenderStateShard.DepthTestStateShard radiance$getDepthTestState();

    @Accessor("cullState")
    RenderStateShard.CullStateShard radiance$getCullState();

    @Accessor("lightmapState")
    RenderStateShard.LightmapStateShard radiance$getLightmapState();

    @Accessor("overlayState")
    RenderStateShard.OverlayStateShard radiance$getOverlayState();

    @Accessor("layeringState")
    RenderStateShard.LayeringStateShard radiance$getLayeringState();

    @Accessor("outputState")
    RenderStateShard.OutputStateShard radiance$getOutputState();

    @Accessor("texturingState")
    RenderStateShard.TexturingStateShard radiance$getTexturingState();

    @Accessor("writeMaskState")
    RenderStateShard.WriteMaskStateShard radiance$getWriteMaskState();

    @Accessor("lineState")
    RenderStateShard.LineStateShard radiance$getLineState();

    @Accessor("colorLogicState")
    RenderStateShard.ColorLogicStateShard radiance$getColorLogicState();
}
