package com.radiance.compatibility.veil;

import net.minecraft.client.renderer.ShaderInstance;

/** Render-thread access used to retire Veil's OpenGL upload queue entries. */
public interface VeilDynamicBufferReloadAccess {

    void radiance$finishVanillaShaderReload(ShaderInstance shader);
}
