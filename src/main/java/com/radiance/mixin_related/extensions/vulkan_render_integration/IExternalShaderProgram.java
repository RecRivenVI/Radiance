package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.ShaderDefinition;
import java.nio.ByteBuffer;

/** Supplies translated metadata and live CPU uniform values for an external shader wrapper. */
public interface IExternalShaderProgram {

    ExternalShaderMetadata radiance$getExternalShader(VertexFormat.Mode drawMode);

    void radiance$writeExternalUniforms(ShaderDefinition definition, ByteBuffer destination);
}
