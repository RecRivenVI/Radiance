package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.util.Set;
import net.minecraft.client.renderer.ShaderInstance;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/** Exercises the production facade with optional Veil classes genuinely unavailable. */
class VeilOptionalRasterBoundaryTest {
    @Test
    void vanillaDrawDoesNotLinkTheOptionalCompiler() throws Exception {
        String probe = VanillaDraw.class.getName();
        Set<String> isolated = Set.of(VeilAdapter.class.getName(), probe);
        ClassLoader loader =
                new ClassLoader(getClass().getClassLoader()) {
                    @Override
                    protected Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        synchronized (getClassLoadingLock(name)) {
                            if (name.startsWith("foundry.veil.")
                                    || name.equals(
                                            "com.radiance.compatibility.veil.SpringRasterLowering"))
                                throw new ClassNotFoundException(
                                        "Optional integration absent: " + name);
                            if (!isolated.contains(name)) return super.loadClass(name, resolve);
                            Class<?> found = findLoadedClass(name);
                            if (found == null) {
                                try (var stream =
                                        getParent()
                                                .getResourceAsStream(
                                                        name.replace('.', '/') + ".class")) {
                                    if (stream == null) throw new ClassNotFoundException(name);
                                    byte[] bytes = stream.readAllBytes();
                                    found = defineClass(name, bytes, 0, bytes.length);
                                } catch (IOException failure) {
                                    throw new ClassNotFoundException(name, failure);
                                }
                            }
                            if (resolve) resolveClass(found);
                            return found;
                        }
                    }
                };
        Runnable draw = (Runnable) loader.loadClass(probe).getConstructor().newInstance();
        assertDoesNotThrow(draw::run);
    }

    public static final class VanillaDraw implements Runnable {
        public VanillaDraw() {}

        @Override
        public void run() {
            try {
                // No shader compilation, GL context or native renderer needed: this call
                // must return based on the real vanilla shader's type alone.
                var field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Unsafe unsafe = (Unsafe) field.get(null);
                ShaderInstance shader =
                        (ShaderInstance) unsafe.allocateInstance(ShaderInstance.class);
                VeilAdapter.requireSpringRasterDraw(shader, null, null, 0, 0);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
