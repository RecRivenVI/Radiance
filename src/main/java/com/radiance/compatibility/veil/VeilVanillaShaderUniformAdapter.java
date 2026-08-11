package com.radiance.compatibility.veil;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.shaders.Uniform;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Reconciles uniforms injected by Veil's vanilla-shader source processors. */
public final class VeilVanillaShaderUniformAdapter {
    private VeilVanillaShaderUniformAdapter() {
    }

    public static void reconcile(Shader owner, List<Uniform> uniforms,
        Map<String, Uniform> uniformMap, Map<String, Uniform> processedUniforms,
        String vertexSource, String fragmentSource) {
        HashSet<String> originalNames = new HashSet<>(uniformMap.keySet());
        originalNames.removeAll(processedUniforms.keySet());
        List<VeilVanillaShaderReloadBridge.UniformSpec> specs =
            VeilVanillaShaderReloadBridge.collectUniforms(vertexSource, fragmentSource,
                originalNames);
        HashSet<String> requested = new HashSet<>();
        for (VeilVanillaShaderReloadBridge.UniformSpec spec : specs) {
            requested.add(spec.name());
        }

        processedUniforms.entrySet().removeIf(entry -> {
            if (requested.contains(entry.getKey())) {
                return false;
            }
            Uniform uniform = entry.getValue();
            uniforms.remove(uniform);
            uniformMap.remove(entry.getKey(), uniform);
            uniform.close();
            return true;
        });

        for (VeilVanillaShaderReloadBridge.UniformSpec spec : specs) {
            Uniform uniform = processedUniforms.get(spec.name());
            if (uniform != null && uniform.getType() == spec.type()) {
                continue;
            }
            if (uniform != null) {
                uniforms.remove(uniform);
                uniformMap.remove(spec.name(), uniform);
                uniform.close();
            }
            Uniform replacement = new Uniform(spec.name(), spec.type(), spec.count(), owner);
            if (replacement.getIntBuffer() != null) {
                for (int i = 0; i < replacement.getIntBuffer().capacity(); i++) {
                    replacement.getIntBuffer().put(i, 0);
                }
            }
            if (replacement.getFloatBuffer() != null) {
                for (int i = 0; i < replacement.getFloatBuffer().capacity(); i++) {
                    replacement.getFloatBuffer().put(i, 0.0F);
                }
            }
            processedUniforms.put(spec.name(), replacement);
            uniforms.add(replacement);
            uniformMap.put(spec.name(), replacement);
        }
    }
}
