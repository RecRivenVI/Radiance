package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class SpringRasterLoweringTest {
    @Test
    void originalDirectionalInputsAreForwardedWithoutSyntheticBrightness() {
        Map<String, float[]> assigned = new HashMap<>();
        Map<String, ShaderUniform> uniforms = new HashMap<>();
        for (String name : new String[] {"Light0_Direction", "Light1_Direction"}) {
            uniforms.put(name, (ShaderUniform) Proxy.newProxyInstance(
                ShaderUniform.class.getClassLoader(), new Class<?>[] {ShaderUniform.class},
                (proxy, method, args) -> {
                    if (method.isDefault())
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("setVector"))
                        assigned.put(name, new float[] {(float) args[0], (float) args[1],
                            (float) args[2]});
                    return null;
                }));
        }
        ShaderProgram program = (ShaderProgram) Proxy.newProxyInstance(
            ShaderProgram.class.getClassLoader(), new Class<?>[] {ShaderProgram.class},
            (proxy, method, args) -> method.getName().equals("getUniform")
                ? uniforms.get(args[0].toString()) : null);
        SpringRasterLowering.writeLightingDefaults(program, new Vector3f(0.2F, 0.7F, -0.3F),
            new Vector3f(-0.4F, 0.1F, 0.9F));
        assertArrayEquals(new float[] {0.2F, 0.7F, -0.3F}, assigned.get("Light0_Direction"));
        assertArrayEquals(new float[] {-0.4F, 0.1F, 0.9F}, assigned.get("Light1_Direction"));
        assertEquals(2, assigned.size());
    }
}
