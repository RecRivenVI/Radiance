package com.radiance.compatibility.voxy;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single render-thread owner, independent of Voxy's concrete OpenGL renderer type. */
public final class VoxyPtBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Radiance/Voxy");
    private static final AtomicLong EPOCHS = new AtomicLong();
    private static boolean initialized;
    private static FileChannel lockChannel;
    private static FileLock exclusiveLock;
    private static Level level;
    private static IVoxyRenderSystemHolder holder;
    private static VoxyPtSession session;
    private static Throwable failure;
    private static boolean unsupportedPackReported;

    private VoxyPtBridge() {}

    public static synchronized void initialize() {
        if (initialized) return;
        if (Boolean.getBoolean("voxy.exclusiveLock")) {
            try {
                var directory = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy");
                Files.createDirectories(directory);
                lockChannel =
                        FileChannel.open(
                                directory.resolve("voxy.lock"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                exclusiveLock = lockChannel.tryLock();
                if (exclusiveLock == null) throw new IOException("Voxy storage is already locked");
            } catch (IOException | RuntimeException e) {
                try {
                    if (lockChannel != null) lockChannel.close();
                } catch (IOException close) {
                    e.addSuppressed(close);
                }
                throw new IllegalStateException("Cannot acquire Voxy storage lock", e);
            }
        }
        VoxyCommon.setInstanceFactory(VoxyClientInstance::new);
        initialized = true;
        LOGGER.info("Voxy Vulkan/PT backend initialized; OpenGL renderer and ModelStore disabled");
    }

    public static void setWorld(Level next, IVoxyRenderSystemHolder owner) {
        if (level == next && holder == owner) return;
        close();
        level = next;
        holder = owner;
        failure = null;
    }

    public static void close() {
        var previous = session;
        session = null;
        if (previous != null) previous.close();
    }

    public static void reload() {
        close();
        failure = null;
    }

    public static void frame() {
        if (!VoxyPresence.PRESENT) return;
        initialize();
        if (!com.radiance.client.pipeline.Pipeline.supportsVoxyGeometry()) {
            close();
            if (!unsupportedPackReported && level != null) {
                LOGGER.warn(
                        "Voxy PT suspended: selected pipeline has no supported compact proxy material decoder");
                unsupportedPackReported = true;
            }
            return;
        }
        unsupportedPackReported = false;
        if (level == null
                || holder == null
                || failure != null
                || !VoxyConfig.CONFIG.isRenderingEnabled()) {
            if (session != null && !VoxyConfig.CONFIG.isRenderingEnabled()) close();
            return;
        }
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            VoxyCommon.createInstance();
            instance = VoxyCommon.getInstance();
        }
        if (!(instance instanceof VoxyClientInstance client)) return;
        try {
            if (session == null) {
                var request = client.requestWorld(WorldIdentifier.of(level));
                if (request.state() != VoxyInstance.WorldLoadState.READY) {
                    request.close();
                    return;
                }
                // Transfer the READY lease, rather than retaining the readiness future.
                session =
                        new VoxyPtSession(
                                EPOCHS.incrementAndGet(), level, client, holder, request.lease());
                client.registerRenderer(holder);
            }
            session.frame();
        } catch (RuntimeException | Error e) {
            failure = e;
            try {
                close();
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            LOGGER.error(
                    "Voxy PT backend failed; no further Voxy work will be submitted in this session",
                    e);
            throw e;
        }
    }

    public static long[] counters() {
        return session == null ? new long[0] : session.counters();
    }

    public static boolean active() {
        return session != null && failure == null;
    }

    public static long epoch() {
        return session == null ? 0 : session.epoch();
    }
}
