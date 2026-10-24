package com.radiance.compatibility.sable;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/**
 * Exercise the production boundary with a loader that counts genuinely missing optional classes.
 */
class SableOptionalLookupTest {
    @Test
    void absentSourceDoesNotProbeClassesPerEntityOrBlock() throws Exception {
        try (var mods = new Mods(false)) {
            var loader = new MissingSable();
            Class<?> bridge = loader.loadClass(SableSubLevelBridge.class.getName());
            var entity =
                    bridge.getMethod(
                            "entityRenderTransform",
                            Entity.class,
                            float.class,
                            double.class,
                            double.class,
                            double.class);
            var block =
                    bridge.getDeclaredMethod("blockSubLevel", ClientLevel.class, BlockPos.class);
            var levels = bridge.getDeclaredMethod("getSubLevels", ClientLevel.class);
            block.setAccessible(true);
            levels.setAccessible(true);
            for (int i = 0; i < 1000; ++i) {
                assertNull(entity.invoke(null, null, .5F, 0D, 0D, 0D));
                assertNull(block.invoke(null, null, null));
                assertNull(levels.invoke(null, new Object[] {null}));
            }
            assertEquals(0, loader.attempts);
        }
    }

    @Test
    void installedButBrokenSourceStillFailsExplicitly() throws Exception {
        try (var mods = new Mods(true)) {
            var loader = new MissingSable();
            Class<?> bridge = loader.loadClass(SableSubLevelBridge.class.getName());
            var entity =
                    bridge.getMethod(
                            "entityRenderTransform",
                            Entity.class,
                            float.class,
                            double.class,
                            double.class,
                            double.class);
            var failure =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> entity.invoke(null, null, .5F, 0D, 0D, 0D));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertInstanceOf(ClassNotFoundException.class, failure.getCause().getCause());
            assertEquals(1, loader.attempts);
        }
    }

    private static final class MissingSable extends ClassLoader {
        int attempts;

        MissingSable() {
            super(SableOptionalLookupTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("dev.ryanhcode.sable.")) {
                    ++attempts;
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(SableSubLevelBridge.class.getName()))
                    return super.loadClass(name, resolve);
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    try (var input =
                            getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        found = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (resolve) resolveClass(found);
                return found;
            }
        }
    }

    private static final class Mods implements AutoCloseable {
        final java.lang.reflect.Field instance;
        final Object previous;

        Mods(boolean sable) throws Exception {
            instance = ModList.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            previous = instance.get(null);
            var access = Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            ModList current = (ModList) ((Unsafe) access.get(null)).allocateInstance(ModList.class);
            var index = ModList.class.getDeclaredField("indexedMods");
            index.setAccessible(true);
            // This boundary reads only the loader's presence index, never containers or metadata.
            index.set(current, sable ? Collections.singletonMap("sable", null) : Map.of());
            instance.set(null, current);
        }

        public void close() throws IllegalAccessException {
            instance.set(null, previous);
        }
    }
}
