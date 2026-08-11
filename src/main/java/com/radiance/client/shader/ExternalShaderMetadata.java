package com.radiance.client.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;

/** Fully preprocessed shader metadata supplied by a non-vanilla shader manager. */
public record ExternalShaderMetadata(String name, VertexFormat vertexFormat,
                                     CustomVertexLayout customVertexLayout,
                                     String vertexSource, String tessellationControlSource,
                                     String tessellationEvaluationSource, String fragmentSource,
                                     int patchControlPoints, boolean usesFragmentCoordinateHeight,
                                     int uniformBufferSize, List<ShaderField> fields) {

    public ExternalShaderMetadata(String name, VertexFormat vertexFormat,
        String vertexSource, String fragmentSource, int uniformBufferSize,
        List<ShaderField> fields) {
        this(name, vertexFormat, null, vertexSource, null, null, fragmentSource, 0, false,
            uniformBufferSize, fields);
    }
}
