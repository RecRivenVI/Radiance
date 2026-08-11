package com.radiance.compatibility.veil;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.impl.client.render.dynamicbuffer.VanillaShaderCompiler;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Rebuilds Sable's dynamic program from Radiance's captured vanilla sources. */
public final class VeilSableShaderAdapter {

    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final Map<ShaderInstance, SourceIdentity> SOURCE_IDENTITIES = new WeakHashMap<>();

    private VeilSableShaderAdapter() {
    }

    public static ShaderProgram getDynamicProgram(
        Map<String, CompletableFuture<ShaderProgram>> programs, ShaderInstance shader) {
        int activeBuffers = VanillaShaderCompiler.getActiveDynamicBuffers(shader);
        IShaderProgramExt source = (IShaderProgramExt) (Object) shader;
        if (source.radiance$getVertexSource() == null
            || source.radiance$getFragmentSource() == null) {
            throw new IllegalStateException("Sable source shader was not captured: "
                + shader.getName());
        }
        String vertex = source.radiance$getVertexSource();
        String fragment = source.radiance$getFragmentSource();
        String prefix = activeBuffers + "|" + shader.getName() + "|";
        String key = prefix + sourceIdentity(shader, vertex, fragment);
        CompletableFuture<ShaderProgram> existing = programs.get(key);
        if (existing != null) return existing.getNow(null);
        // A vanilla dynamic-buffer reload need not run Sable's resource listener.
        // Retire the old same-mask program after its outstanding compilation completes.
        programs.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) return false;
            entry.getValue().thenAcceptAsync(ShaderProgram::free, Minecraft.getInstance());
            return true;
        });
        Int2ObjectArrayMap<String> stages = new Int2ObjectArrayMap<>(2);
        stages.put(GL_VERTEX_SHADER, vertex);
        stages.put(GL_FRAGMENT_SHADER, fragment);
        String safeName = key.replaceAll("[^a-z0-9_./-]", "_");
        CompletableFuture<ShaderProgram> created = VeilRenderSystem.renderer()
            .getShaderManager()
            .createDynamicProgram(Sable.sablePath("dynamic_sublevel/" + safeName), stages)
            .thenApplyAsync(VeilSableShaderAdapter::initializeProgram, Minecraft.getInstance());
        programs.put(key, created);
        return null;
    }

    private static String sourceIdentity(ShaderInstance shader, String vertex, String fragment) {
        SourceIdentity current = SOURCE_IDENTITIES.get(shader);
        if (current != null && current.vertex == vertex && current.fragment == fragment) {
            return current.digest;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(vertex.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String hash = HexFormat.of().formatHex(digest.digest(fragment.getBytes(StandardCharsets.UTF_8)));
            SOURCE_IDENTITIES.put(shader, new SourceIdentity(vertex, fragment, hash));
            return hash;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private record SourceIdentity(String vertex, String fragment, String digest) {}

    private static ShaderProgram initializeProgram(ShaderProgram program) {
        ShaderUniform normalLighting = program.getUniform("SableEnableNormalLighting");
        if (normalLighting != null) normalLighting.setFloat(1.0F);
        ShaderUniform shadows = program.getUniform("SableShadowsEnabled");
        if (shadows != null) shadows.setFloat(SableSkyLightShadows.isEnabled() ? 1.0F : 0.0F);
        return program;
    }
}
