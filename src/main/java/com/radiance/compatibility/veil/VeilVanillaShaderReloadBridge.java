package com.radiance.compatibility.veil;

import com.radiance.client.shader.ShaderRegistry;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import foundry.veil.Veil;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Collects Veil's processed vanilla stages and installs them as the source of the next Vulkan
 * draw-mode variant.
 */
public final class VeilVanillaShaderReloadBridge {

    private static final AtomicLong NEXT_TICKET = new AtomicLong();
    private static final Pattern UNIFORM = Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+([A-Za-z0-9_]+)"
            + "\\s*(\\[[^]\\r\\n]+])?\\s+([A-Za-z_][A-Za-z0-9_]*)"
            + "\\s*(\\[[^]\\r\\n]+])?\\s*;");
    private static final ThreadLocal<Long> COMPILING_TICKET = new ThreadLocal<>();
    private static final ThreadLocal<Long> EXECUTING_TICKET = new ThreadLocal<>();
    private static final Map<ShaderInstance, ShaderReloads> PENDING = new WeakHashMap<>();
    private static final Map<ShaderInstance, Integer> ACTIVE_BUFFERS = new WeakHashMap<>();

    private VeilVanillaShaderReloadBridge() {
    }

    public static void begin(ShaderInstance shader, int activeBuffers) {
        long ticket = NEXT_TICKET.incrementAndGet();
        COMPILING_TICKET.set(ticket);
        synchronized (PENDING) {
            PENDING.computeIfAbsent(shader, ignored -> new ShaderReloads())
                .begin(ticket, activeBuffers);
        }
    }

    public static Runnable wrapStageCallback(Runnable callback) {
        long ticket = currentCompilingTicket();
        return () -> {
            EXECUTING_TICKET.set(ticket);
            try {
                callback.run();
            } finally {
                EXECUTING_TICKET.remove();
            }
        };
    }

    public static void finishScheduling() {
        COMPILING_TICKET.remove();
    }

    public static void capture(ShaderInstance shader, boolean vertex, String source,
        int activeBuffers) {
        ProcessedSources completed;
        boolean publish;
        long ticket = currentExecutingTicket();
        synchronized (PENDING) {
            ShaderReloads reloads = PENDING.get(shader);
            completed = reloads == null ? null
                : reloads.capture(ticket, vertex, source, activeBuffers);
            publish = reloads != null && reloads.isLatest(ticket);
        }
        if (completed == null || !publish) {
            return;
        }

        synchronized (PENDING) {
            ShaderReloads reloads = PENDING.get(shader);
            if (reloads == null || !reloads.isLatest(ticket)) {
                return;
            }
            IShaderProgramExt ext = (IShaderProgramExt) (Object) shader;
            ((VeilVanillaShaderMetadataAccess) (Object) shader).radiance$ensureProcessedUniforms(
                completed.vertexSource(), completed.fragmentSource());
            // Removing this instance first clears every cached draw-mode variant. Native-side
            // ownership is intentionally retained for commands already in flight.
            ShaderRegistry.unregisterLiveShader(shader);
            ext.radiance$setVertexSource(completed.vertexSource());
            ext.radiance$setFragmentSource(completed.fragmentSource());
            ShaderRegistry.registerLiveShader(shader);
            ACTIVE_BUFFERS.put(shader, completed.activeBuffers());
            reloads.markInstalled(ticket);
        }
    }

    public static int activeBuffers(ShaderInstance shader) {
        synchronized (PENDING) {
            return ACTIVE_BUFFERS.getOrDefault(shader, 0);
        }
    }

    public static void finishStage(DynamicBufferManager manager, ShaderInstance shader) {
        boolean finished;
        long ticket = currentExecutingTicket();
        synchronized (PENDING) {
            ShaderReloads reloads = PENDING.get(shader);
            finished = reloads != null && reloads.installed(ticket)
                && reloads.only(ticket);
        }
        if (finished) {
            ((VeilDynamicBufferReloadAccess) manager)
                .radiance$finishVanillaShaderReload(shader);
        }
    }

    public static void finishShader(DynamicBufferManager manager, ShaderInstance shader) {
        PendingSources pending;
        boolean allFinished;
        boolean latest;
        long ticket = currentExecutingTicket();
        synchronized (PENDING) {
            ShaderReloads reloads = PENDING.get(shader);
            latest = reloads != null && reloads.isLatest(ticket);
            pending = reloads == null ? null : reloads.finish(ticket);
            allFinished = reloads == null || reloads.isEmpty();
            if (allFinished) {
                PENDING.remove(shader);
            }
        }
        if (allFinished) {
            ((VeilDynamicBufferReloadAccess) manager).radiance$finishVanillaShaderReload(shader);
        }
        if (latest && pending != null && !pending.installed) {
            Veil.LOGGER.error(
                "Vanilla shader {} did not install a complete processed stage pair for Vulkan reload",
                shader.getName());
        }
    }

    static ProcessedSources collectForTest(PendingSources pending, boolean vertex, String source,
        int activeBuffers) {
        return pending.accept(vertex, source, activeBuffers);
    }

    public static java.util.List<UniformSpec> collectUniforms(String vertexSource,
        String fragmentSource, Set<String> existingNames) {
        LinkedHashMap<String, UniformSpec> uniforms = new LinkedHashMap<>();
        collectUniforms(vertexSource, existingNames, uniforms);
        collectUniforms(fragmentSource, existingNames, uniforms);
        return java.util.List.copyOf(uniforms.values());
    }

    private static void collectUniforms(String source, Set<String> existingNames,
        Map<String, UniformSpec> uniforms) {
        Matcher matcher = UNIFORM.matcher(source);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(3);
            if (type.startsWith("sampler")) {
                continue;
            }
            String typeArray = matcher.group(2);
            String nameArray = matcher.group(4);
            if (typeArray != null && nameArray != null) {
                throw new IllegalArgumentException(
                    "Processed vanilla uniform has two array dimensions: " + name);
            }
            String arrayExpression = typeArray != null ? typeArray : nameArray;
            int arrayLength = parseArrayLength(arrayExpression);
            for (int i = 0; i < arrayLength; i++) {
                String elementName = arrayExpression == null ? name : name + '[' + i + ']';
                if (!existingNames.contains(elementName) && !uniforms.containsKey(elementName)) {
                    uniforms.put(elementName, uniformSpec(type, elementName));
                }
            }
        }
    }

    private static int parseArrayLength(String expression) {
        if (expression == null) {
            return 1;
        }
        String value = expression.substring(1, expression.length() - 1);
        int length = 1;
        for (String factor : value.split("\\*")) {
            try {
                length = Math.multiplyExact(length, Integer.parseInt(factor.trim()));
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(
                    "Unsupported processed vanilla uniform array length: " + expression,
                    exception);
            }
        }
        if (length < 1) {
            throw new IllegalArgumentException(
                "Processed vanilla uniform array length must be positive: " + expression);
        }
        return length;
    }

    private static UniformSpec uniformSpec(String type, String name) {
        return switch (type) {
            case "int", "uint", "bool" -> new UniformSpec(name, 0, 1);
            case "ivec2", "uvec2", "bvec2" -> new UniformSpec(name, 1, 2);
            case "ivec3", "uvec3", "bvec3" -> new UniformSpec(name, 2, 3);
            case "ivec4", "uvec4", "bvec4" -> new UniformSpec(name, 3, 4);
            case "float" -> new UniformSpec(name, 4, 1);
            case "vec2" -> new UniformSpec(name, 5, 2);
            case "vec3" -> new UniformSpec(name, 6, 3);
            case "vec4" -> new UniformSpec(name, 7, 4);
            case "mat2" -> new UniformSpec(name, 8, 4);
            case "mat3" -> new UniformSpec(name, 9, 9);
            case "mat4" -> new UniformSpec(name, 10, 16);
            default -> throw new IllegalArgumentException(
                "Unsupported processed vanilla uniform type " + type + " for " + name);
        };
    }

    private static long currentCompilingTicket() {
        Long ticket = COMPILING_TICKET.get();
        if (ticket == null) {
            throw new IllegalStateException("No active Veil vanilla shader compile ticket");
        }
        return ticket;
    }

    private static long currentExecutingTicket() {
        Long ticket = EXECUTING_TICKET.get();
        if (ticket == null) {
            throw new IllegalStateException("No executing Veil vanilla shader stage ticket");
        }
        return ticket;
    }

    static final class ShaderReloads {
        private final Map<Long, PendingSources> sources = new java.util.LinkedHashMap<>();
        private long latestTicket;

        void begin(long ticket, int activeBuffers) {
            this.latestTicket = ticket;
            this.sources.put(ticket, new PendingSources(activeBuffers));
        }

        ProcessedSources capture(long ticket, boolean vertex, String source, int activeBuffers) {
            PendingSources pending = this.sources.get(ticket);
            return pending == null ? null : pending.accept(vertex, source, activeBuffers);
        }

        boolean isLatest(long ticket) {
            return this.latestTicket == ticket;
        }

        boolean installed(long ticket) {
            PendingSources pending = this.sources.get(ticket);
            return pending != null && pending.installed;
        }

        void markInstalled(long ticket) {
            PendingSources pending = this.sources.get(ticket);
            if (pending != null) {
                pending.installed = true;
            }
        }

        boolean only(long ticket) {
            return this.sources.size() == 1 && this.sources.containsKey(ticket);
        }

        PendingSources finish(long ticket) {
            return this.sources.remove(ticket);
        }

        boolean isEmpty() {
            return this.sources.isEmpty();
        }
    }

    static final class PendingSources {
        private int activeBuffers;
        private String vertexSource;
        private String fragmentSource;
        private boolean installed;

        PendingSources(int activeBuffers) {
            this.activeBuffers = activeBuffers;
        }

        ProcessedSources accept(boolean vertex, String source, int activeBuffers) {
            if (this.activeBuffers != activeBuffers) {
                this.activeBuffers = activeBuffers;
                this.vertexSource = null;
                this.fragmentSource = null;
                this.installed = false;
            }
            if (vertex) {
                this.vertexSource = source;
            } else {
                this.fragmentSource = source;
            }
            return this.vertexSource != null && this.fragmentSource != null
                ? new ProcessedSources(this.vertexSource, this.fragmentSource, activeBuffers)
                : null;
        }

        boolean complete() {
            return this.vertexSource != null && this.fragmentSource != null;
        }
    }

    record ProcessedSources(String vertexSource, String fragmentSource, int activeBuffers) {
    }

    public record UniformSpec(String name, int type, int count) {
    }
}
