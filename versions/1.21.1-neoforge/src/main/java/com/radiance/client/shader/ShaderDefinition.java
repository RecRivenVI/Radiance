package com.radiance.client.shader;

import java.util.List;

public record ShaderDefinition(
        String key, String name, int nativeId, int uniformBufferSize, List<ShaderField> fields) {}
