package com.radiance.client.proxy.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.client.shader.ShaderField;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderProxyUniformArrayTest {

    private static final ShaderField BRIGHTNESS = new ShaderField("VeilBlockFaceBrightness",
        "VeilBlockFaceBrightness", ShaderField.Kind.FLOAT, 1, 16, 96, -1, 6);

    @Test
    void namedPackingPreservesAllSixValuesWhenTheCpuListOrderChanges() {
        List<Uniform> uniforms = brightnessUniforms(6);
        CpuUniform enabled = new CpuUniform("SableEnableNormalLighting", 0.75F);
        uniforms.add(enabled);
        Collections.reverse(uniforms);
        ByteBuffer bytes = ByteBuffer.allocate(112).order(ByteOrder.nativeOrder());
        try {
            ShaderProxy.writeUniformValues(bytes, List.of(
                new ShaderField("SableEnableNormalLighting", "SableEnableNormalLighting",
                    ShaderField.Kind.FLOAT, 1, 0, 4, -1), BRIGHTNESS), uniforms);

            assertEquals(0.75F, bytes.getFloat(0));
            for (int i = 0; i < 6; i++) {
                assertEquals(0.125F * (i + 1), bytes.getFloat(16 + i * 16));
            }
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    @Test
    void oneElementArrayPacksTheElementUniform() {
        List<Uniform> uniforms = brightnessUniforms(1);
        try {
            ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
            ShaderProxy.writeUniformValues(bytes, List.of(new ShaderField(
                "VeilBlockFaceBrightness", "VeilBlockFaceBrightness", ShaderField.Kind.FLOAT,
                1, 0, 16, -1, 1, true)), uniforms);
            assertEquals(0.125F, bytes.getFloat(0));
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    @Test
    void missingArrayDataRaisesTheExactElementName() {
        List<Uniform> uniforms = brightnessUniforms(5);
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ShaderProxy.writeUniformValues(ByteBuffer.allocate(112),
                    List.of(BRIGHTNESS), uniforms));
            assertTrue(error.getMessage().contains("VeilBlockFaceBrightness[5]"));
        } finally {
            uniforms.forEach(Uniform::close);
        }
    }

    private static List<Uniform> brightnessUniforms(int length) {
        List<Uniform> uniforms = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            uniforms.add(new CpuUniform("VeilBlockFaceBrightness[" + i + ']',
                0.125F * (i + 1)));
        }
        return uniforms;
    }

    private static final class CpuUniform extends Uniform implements IGlUniformExt {
        CpuUniform(String name, float value) {
            super(name, Uniform.UT_FLOAT1, 1, null);
            this.set(value);
        }

        public int radiance$getDataTypeValue() { return this.getType(); }
        public int radiance$getCountValue() { return this.getCount(); }
        public IntBuffer radiance$getIntDataValue() { return this.getIntBuffer(); }
        public FloatBuffer radiance$getFloatDataValue() { return this.getFloatBuffer(); }
    }
}
