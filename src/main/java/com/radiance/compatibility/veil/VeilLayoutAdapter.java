package com.radiance.compatibility.veil;

import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.program.ShaderUniformCache;

/** Serves Veil uniform-block reflection from the translated Vulkan shader model. */
public final class VeilLayoutAdapter {
    private static final int GL_UNIFORM_OFFSET = 0x8A3B;
    private static final ThreadLocal<ShaderUniformCache.UniformBlock> BLOCK = new ThreadLocal<>();
    private static final ThreadLocal<String> BLOCK_NAME = new ThreadLocal<>();

    private VeilLayoutAdapter() {
    }

    public static void begin(ShaderProgram shader, String name) {
        BLOCK.set(VeilShaderBridge.block(shader, name));
        BLOCK_NAME.set(name);
    }

    public static void end() {
        BLOCK.remove();
        BLOCK_NAME.remove();
    }

    public static int uniformIndex(CharSequence fieldName) {
        ShaderUniformCache.UniformBlock block = BLOCK.get();
        String prefix = BLOCK_NAME.get();
        if (block == null || prefix == null) {
            return -1;
        }
        String full = fieldName.toString();
        String field = full.startsWith(prefix + ".")
            ? full.substring(prefix.length() + 1) : full;
        ShaderUniformCache.Uniform[] fields = block.fields();
        for (int index = 0; index < fields.length; index++) {
            if (fields[index].name().equals(field)) {
                return index;
            }
        }
        return -1;
    }

    public static int uniformOffset(int index, int property) {
        ShaderUniformCache.UniformBlock block = BLOCK.get();
        if (block == null || property != GL_UNIFORM_OFFSET) {
            return -1;
        }
        ShaderUniformCache.Uniform[] fields = block.fields();
        return index < 0 || index >= fields.length ? -1 : fields[index].offset();
    }

    public static int blockSize() {
        ShaderUniformCache.UniformBlock block = BLOCK.get();
        return block == null ? 0 : block.size();
    }
}
