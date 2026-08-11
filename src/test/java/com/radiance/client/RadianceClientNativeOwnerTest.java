package com.radiance.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class RadianceClientNativeOwnerTest {
    @Test
    void everyRegisteredOwnerActuallyDeclaresANativeMethod() throws Exception {
        Field ownersField = RadianceClient.class.getDeclaredField("GAME_NATIVE_OWNERS");
        ownersField.setAccessible(true);
        for (Class<?> owner : (Class<?>[]) ownersField.get(null)) {
            boolean hasNative = false;
            for (Method method : owner.getDeclaredMethods()) {
                hasNative |= Modifier.isNative(method.getModifiers());
            }
            assertTrue(hasNative, () -> "JNI owner has no native methods: " + owner.getName());
        }
    }
}
