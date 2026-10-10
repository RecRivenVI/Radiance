package com.radiance.compatibility.veil;

/** Adds CPU-backed uniforms introduced by Veil's vanilla shader preprocessors. */
public interface VeilVanillaShaderMetadataAccess {

    void radiance$ensureProcessedUniforms(String vertexSource, String fragmentSource);
}
