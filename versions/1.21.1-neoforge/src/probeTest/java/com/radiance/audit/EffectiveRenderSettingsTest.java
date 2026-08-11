package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class EffectiveRenderSettingsTest {
    @Test
    void legacyGlobalBounceValueCannotCertifyDifferentShaderAttributes() {
        var expected =
                JsonParser.parseString(
                                "{\"num_ray_bounces\":\"4\",\"coverage_mode\":\"stochastic\"}")
                        .getAsJsonObject();
        var actual =
                JsonParser.parseString("{\"num_ray_bounces\":\"128\",\"coverage_mode\":\"blend\"}")
                        .getAsJsonObject();
        assertThrows(
                IllegalStateException.class,
                () -> EffectiveRenderSettings.compare(expected, actual, "ray-tracing"));
        assertDoesNotThrow(
                () ->
                        EffectiveRenderSettings.compare(
                                expected, expected.deepCopy(), "ray-tracing"));
        assertThrows(
                IllegalStateException.class,
                () -> EffectiveRenderSettings.compare(expected, null, "ray-tracing"));
    }
}
